#!/usr/bin/env python3
"""Collect PR inputs, run the local Risk Gate, and decide the CI result."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request


WORKSPACE = Path(os.environ.get("GITHUB_WORKSPACE", Path.cwd())).resolve()
RISK_GATE = Path(os.environ.get("RISK_GATE_PATH", Path(__file__).resolve().parents[2])).resolve()
BUILD = WORKSPACE / "build/reports/build-convention/report.json"
SEMGREP = WORKSPACE / "build/reports/semgrep/report.json"
RESPONSE = WORKSPACE / "build/reports/risk-gate/response.json"


def changes(base):
    raw = subprocess.check_output([
        "git", "diff", "--name-status", "-z", "--find-renames", f"origin/{base}...HEAD"
    ], cwd=WORKSPACE).split(b"\0")
    files = []
    index = 0
    while index < len(raw) - 1:
        status = raw[index].decode("ascii")
        index += 1
        if status.startswith("R") and status[1:].isdigit():
            index += 1  # original path
            kind = "RENAMED"
        else:
            kind = {"A": "ADDED", "M": "MODIFIED", "D": "DELETED"}.get(status)
        if kind is None:
            raise ValueError(f"Unsupported Git change status: {status}")
        files.append({"path": os.fsdecode(raw[index]), "changeType": kind})
        index += 1
    return files


def summary(message, decision=None, score=None, reasons=None, build=None, semgrep=None):
    lines = [f"## Risk Gate: {decision or 'ERROR'}", message]
    if score is not None:
        lines.append(f"Risk Score: {score}")
    if reasons:
        lines += ["Reasons:"] + [f"- {reason}" for reason in reasons]
    if build:
        lines.append(f"Build Convention: {build}")
    if semgrep:
        lines.append(f"Semgrep: {semgrep}")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
        output.write("\n\n".join(lines) + "\n")


def main():
    build_exit = int(os.environ["BUILD_EXIT"])
    semgrep_exit = int(os.environ["SEMGREP_EXIT"])
    build = json.loads(BUILD.read_text(encoding="utf-8"))
    semgrep = json.loads(SEMGREP.read_text(encoding="utf-8"))
    if build["status"] not in ("PASS", "FAIL"):
        raise ValueError("Invalid Build Convention status")
    if not isinstance(semgrep.get("results"), list) or semgrep.get("errors"):
        raise ValueError("Semgrep analysis incomplete")
    if semgrep_exit != 0:
        raise ValueError(f"Semgrep execution failed (exit {semgrep_exit})")
    request = {
        "reportVersion": "1",
        "repository": os.environ["REPOSITORY"],
        "commitSha": os.environ["HEAD_SHA"],
        "pullRequestNumber": int(os.environ["PR_NUMBER"]),
        "buildConventionReport": build,
        "semgrepReport": semgrep,
        "findings": [],
        "changedFiles": changes(os.environ["BASE_REF"]),
        "diff": subprocess.check_output([
            "git", "diff", "--binary", f"origin/{os.environ['BASE_REF']}...HEAD"
        ], cwd=WORKSPACE).decode("utf-8", errors="replace"),
    }
    jars = [path for path in (RISK_GATE / "build/libs").glob("*.jar") if not path.name.endswith("-plain.jar")]
    if len(jars) != 1:
        raise ValueError("Expected one Risk Gate boot jar")
    jar = jars[0]
    process = subprocess.Popen(["java", "-jar", str(jar), "--server.port=18080"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(60):
            if process.poll() is not None:
                raise RuntimeError("Risk Gate process exited before readiness")
            try:
                with urllib.request.urlopen("http://127.0.0.1:18080/actuator/health", timeout=2) as health:
                    if health.status == 200:
                        break
            except (urllib.error.URLError, TimeoutError):
                time.sleep(1)
        else:
            raise RuntimeError("Risk Gate readiness timeout")
        with tempfile.TemporaryDirectory() as directory:
            request_file = Path(directory) / "request.json"
            request_file.write_text(json.dumps(request), encoding="utf-8")
            call = urllib.request.Request("http://127.0.0.1:18080/api/v1/risk-assessments",
                                          data=request_file.read_bytes(),
                                          headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(call, timeout=30) as result:
                response = json.load(result)
        RESPONSE.parent.mkdir(parents=True, exist_ok=True)
        RESPONSE.write_text(json.dumps(response, indent=2), encoding="utf-8")
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    decision = response.get("decision")
    if decision not in ("PASS", "REVIEW", "BLOCK"):
        raise ValueError("Invalid Risk Gate decision")
    consistent = (build_exit == 0) == (build["status"] == "PASS")
    build_state = build["status"] if consistent else "INCONSISTENT"
    summary("Human review required" if decision == "REVIEW" else "Assessment complete",
            decision, response.get("score"), response.get("reasonCodes"), build_state, "PASS")
    if not consistent or (build_exit != 0 and decision != "BLOCK"):
        raise ValueError("Build Convention result contradicts Risk Gate decision")
    return 1 if decision == "BLOCK" else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        summary(f"Assessment failed: {type(error).__name__}")
        print(f"Risk Gate failed: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
