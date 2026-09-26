#!/usr/bin/env python3
"""Collect PR inputs, run the local Risk Gate, and decide the CI result."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import traceback
import urllib.error
import urllib.request


WORKSPACE = Path(
    os.environ.get("GITHUB_WORKSPACE", Path.cwd())
).resolve()

RISK_GATE = Path(
    os.environ.get(
        "RISK_GATE_PATH",
        Path(__file__).resolve().parents[2],
    )
).resolve()

BUILD_REPORT = (
    WORKSPACE / "build/reports/build-convention/report.json"
)

SEMGREP_REPORT = (
    WORKSPACE / "build/reports/semgrep/report.json"
)

RISK_GATE_RESPONSE = (
    WORKSPACE / "build/reports/risk-gate/response.json"
)

RISK_GATE_PORT = 18080
RISK_GATE_BASE_URL = f"http://127.0.0.1:{RISK_GATE_PORT}"


def read_json(path: Path, label: str) -> dict:
    if not path.exists():
        raise FileNotFoundError(
            f"{label} does not exist: {path}"
        )

    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(
            f"{label} contains invalid JSON: {path}"
        ) from error


def git_changes(base_ref: str) -> list[dict]:
    raw = subprocess.check_output(
        [
            "git",
            "diff",
            "--name-status",
            "-z",
            "--find-renames",
            f"origin/{base_ref}...HEAD",
        ],
        cwd=WORKSPACE,
    ).split(b"\0")

    files = []
    index = 0

    while index < len(raw) - 1:
        status = raw[index].decode("ascii")
        index += 1

        if status.startswith("R") and status[1:].isdigit():
            if index + 1 >= len(raw):
                raise ValueError(
                    "Malformed Git rename output"
                )

            # Skip original path.
            index += 1
            change_type = "RENAMED"

        else:
            change_type = {
                "A": "ADDED",
                "M": "MODIFIED",
                "D": "DELETED",
            }.get(status)

        if change_type is None:
            raise ValueError(
                f"Unsupported Git change status: {status}"
            )

        if index >= len(raw):
            raise ValueError(
                f"Missing path for Git status: {status}"
            )

        files.append(
            {
                "path": os.fsdecode(raw[index]),
                "changeType": change_type,
            }
        )

        index += 1

    return files


def git_diff(base_ref: str) -> str:
    return subprocess.check_output(
        [
            "git",
            "diff",
            "--binary",
            f"origin/{base_ref}...HEAD",
        ],
        cwd=WORKSPACE,
    ).decode(
        "utf-8",
        errors="replace",
    )


def write_summary(
    message: str,
    decision: str | None = None,
    score=None,
    reasons=None,
    build_state: str | None = None,
    semgrep_state: str | None = None,
):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")

    if not summary_path:
        return

    lines = [
        f"## Risk Gate: {decision or 'ERROR'}",
        message,
    ]

    if score is not None:
        lines.append(f"Risk Score: {score}")

    if reasons:
        lines.append("Reasons:")
        lines.extend(
            f"- {reason}"
            for reason in reasons
        )

    if build_state is not None:
        lines.append(
            f"Build Convention: {build_state}"
        )

    if semgrep_state is not None:
        lines.append(
            f"Semgrep: {semgrep_state}"
        )

    with open(
        summary_path,
        "a",
        encoding="utf-8",
    ) as output:
        output.write("\n\n".join(lines))
        output.write("\n")


def validate_build_report(
    build: dict,
    build_exit: int,
) -> str:
    status = build.get("status")

    if status not in ("PASS", "FAIL"):
        raise ValueError(
            f"Invalid Build Convention status: {status!r}"
        )

    exit_passed = build_exit == 0
    report_passed = status == "PASS"

    if exit_passed != report_passed:
        return "INCONSISTENT"

    return status


def validate_semgrep_report(
    semgrep: dict,
    semgrep_exit: int,
):
    if semgrep_exit != 0:
        raise ValueError(
            "Semgrep execution failed "
            f"(exit code: {semgrep_exit})"
        )

    results = semgrep.get("results")

    if not isinstance(results, list):
        raise ValueError(
            "Semgrep report does not contain "
            "a valid results array"
        )

    errors = semgrep.get("errors")

    if errors:
        raise ValueError(
            "Semgrep report contains analysis errors "
            f"(count: {len(errors)})"
        )


def find_boot_jar() -> Path:
    libs = RISK_GATE / "build/libs"

    if not libs.exists():
        raise FileNotFoundError(
            f"Risk Gate build/libs does not exist: {libs}"
        )

    jars = sorted(
        path
        for path in libs.glob("*.jar")
        if not path.name.endswith("-plain.jar")
    )

    if len(jars) != 1:
        names = [path.name for path in jars]

        raise ValueError(
            "Expected exactly one Risk Gate boot jar, "
            f"found {len(jars)}: {names}"
        )

    return jars[0]


def wait_until_ready(process: subprocess.Popen):
    health_url = (
        f"{RISK_GATE_BASE_URL}/actuator/health"
    )

    for _ in range(60):
        return_code = process.poll()

        if return_code is not None:
            raise RuntimeError(
                "Risk Gate process exited before readiness "
                f"(exit code: {return_code})"
            )

        try:
            with urllib.request.urlopen(
                health_url,
                timeout=2,
            ) as response:
                if response.status == 200:
                    return

        except (
            urllib.error.URLError,
            TimeoutError,
        ):
            time.sleep(1)

    raise RuntimeError(
        "Risk Gate readiness timeout"
    )


def call_risk_gate(request_body: dict) -> dict:
    endpoint = (
        f"{RISK_GATE_BASE_URL}"
        "/api/v1/risk-assessments"
    )

    payload = json.dumps(
        request_body
    ).encode("utf-8")

    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(
            request,
            timeout=30,
        ) as result:
            return json.load(result)

    except urllib.error.HTTPError as error:
        # Do not print request body or raw diff.
        response_body = error.read().decode(
            "utf-8",
            errors="replace",
        )

        # Limit response output in CI.
        response_body = response_body[:2000]

        raise RuntimeError(
            "Risk Gate HTTP request failed "
            f"(status: {error.code}, "
            f"response: {response_body})"
        ) from error


def run_risk_gate(request_body: dict) -> dict:
    jar = find_boot_jar()

    process = subprocess.Popen(
        [
            "java",
            "-jar",
            str(jar),
            f"--server.port={RISK_GATE_PORT}",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    try:
        wait_until_ready(process)

        return call_risk_gate(
            request_body
        )

    finally:
        if process.poll() is None:
            process.terminate()

            try:
                process.wait(timeout=10)

            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def main() -> int:
    build_exit = int(
        os.environ["BUILD_EXIT"]
    )

    semgrep_exit = int(
        os.environ["SEMGREP_EXIT"]
    )

    base_ref = os.environ["BASE_REF"]

    build = read_json(
        BUILD_REPORT,
        "Build Convention report",
    )

    semgrep = read_json(
        SEMGREP_REPORT,
        "Semgrep report",
    )

    build_state = validate_build_report(
        build,
        build_exit,
    )

    validate_semgrep_report(
        semgrep,
        semgrep_exit,
    )

    changed_files = git_changes(
        base_ref
    )

    diff = git_diff(
        base_ref
    )

    request_body = {
        "reportVersion": "1",
        "repository": os.environ["REPOSITORY"],
        "commitSha": os.environ["HEAD_SHA"],
        "pullRequestNumber": int(
            os.environ["PR_NUMBER"]
        ),
        "buildConventionReport": build,
        "semgrepReport": semgrep,
        "findings": [],
        "changedFiles": changed_files,
        "diff": diff,
    }

    response = run_risk_gate(
        request_body
    )

    RISK_GATE_RESPONSE.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    RISK_GATE_RESPONSE.write_text(
        json.dumps(
            response,
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    decision = response.get(
        "decision"
    )

    if decision not in (
        "PASS",
        "REVIEW",
        "BLOCK",
    ):
        raise ValueError(
            f"Invalid Risk Gate decision: {decision!r}"
        )

    write_summary(
        (
            "Human review required"
            if decision == "REVIEW"
            else "Assessment complete"
        ),
        decision=decision,
        score=response.get("score"),
        reasons=response.get("reasonCodes"),
        build_state=build_state,
        semgrep_state="PASS",
    )

    if build_state == "INCONSISTENT":
        raise ValueError(
            "Build Convention exit code and "
            "report status are inconsistent"
        )

    if (
        build_exit != 0
        and decision != "BLOCK"
    ):
        raise ValueError(
            "Build Convention failed but "
            f"Risk Gate returned {decision}"
        )

    return 1 if decision == "BLOCK" else 0


if __name__ == "__main__":
    try:
        sys.exit(main())

    except Exception as error:
        error_type = type(error).__name__

        write_summary(
            f"Assessment failed: "
            f"{error_type}: {error}",
        )

        print(
            f"Risk Gate failed: "
            f"{error_type}: {error}",
            file=sys.stderr,
        )

        traceback.print_exc(
            file=sys.stderr,
        )

        sys.exit(1)