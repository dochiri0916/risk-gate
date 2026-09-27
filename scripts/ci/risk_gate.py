#!/usr/bin/env python3
"""Run the packaged Risk Gate CI command and preserve its machine result."""

import json
import os
from pathlib import Path
import subprocess
import sys
import traceback


WORKSPACE = Path(os.environ.get("GITHUB_WORKSPACE", Path.cwd())).resolve()
RISK_GATE = Path(os.environ.get("RISK_GATE_PATH", Path(__file__).resolve().parents[2])).resolve()
BUILD_REPORT = WORKSPACE / "build/reports/build-convention/report.json"
SEMGREP_REPORT = WORKSPACE / "build/reports/semgrep/report.json"
RISK_GATE_RESPONSE = WORKSPACE / "build/reports/risk-gate/response.json"


def read_json(path: Path, label: str) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"{label} does not exist: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"{label} contains invalid JSON: {path}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be a JSON object: {path}")
    return value


def write_summary(message: str, result=None):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    decision = result.get("decision", "ERROR") if result else "ERROR"
    lines = [f"## Risk Gate: {decision}", message]
    if result and "score" in result:
        lines.append(f"Risk Score: {result['score']}")
    if result and result.get("reasonCodes"):
        lines.append("Reasons:")
        lines.extend(f"- {reason}" for reason in result["reasonCodes"])
    with open(summary_path, "a", encoding="utf-8") as output:
        output.write("\n\n".join(lines) + "\n")


def find_boot_jar() -> Path:
    libs = RISK_GATE / "build/libs"
    if not libs.exists():
        raise FileNotFoundError(f"Risk Gate build/libs does not exist: {libs}")
    jars = sorted(path for path in libs.glob("*.jar") if not path.name.endswith("-plain.jar"))
    if len(jars) != 1:
        raise ValueError(f"Expected exactly one Risk Gate boot jar, found {len(jars)}")
    return jars[0]


def write_result(result: dict):
    RISK_GATE_RESPONSE.parent.mkdir(parents=True, exist_ok=True)
    RISK_GATE_RESPONSE.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def error_result(code: str = "EXECUTION_ERROR") -> dict:
    return {"decision": "ERROR", "reasonCodes": [code], "score": 0}


def run_risk_gate() -> tuple[dict, int]:
    jar = find_boot_jar()
    command = [
        "java", "-jar", str(jar), "ci",
        "--project", str(WORKSPACE),
        "--base", os.environ["BASE_REF"],
        "--report", str(BUILD_REPORT),
        "--semgrep-report", str(SEMGREP_REPORT),
        "--repository", os.environ["REPOSITORY"],
        "--head-sha", os.environ["HEAD_SHA"],
        "--pr-number", os.environ["PR_NUMBER"],
        "--build-exit", os.environ["BUILD_EXIT"],
        "--semgrep-exit", os.environ["SEMGREP_EXIT"],
    ]
    process = subprocess.run(command, cwd=WORKSPACE, capture_output=True, text=True, check=False)
    if process.returncode in (0, 2, 3):
        try:
            result = json.loads(process.stdout)
        except json.JSONDecodeError as error:
            raise RuntimeError("Risk Gate returned no valid JSON result") from error
        expected = {0: "PASS", 2: "REVIEW", 3: "BLOCK"}[process.returncode]
        if not isinstance(result, dict) or result.get("decision") != expected:
            raise RuntimeError("Risk Gate decision and exit code do not match")
        if not isinstance(result.get("reasonCodes"), list) or not isinstance(result.get("score"), int):
            raise RuntimeError("Risk Gate result is missing required fields")
        return result, process.returncode
    if process.returncode == 4:
        return error_result(), 4
    raise RuntimeError(f"Risk Gate process failed with exit code {process.returncode}")


def main() -> int:
    build = read_json(BUILD_REPORT, "Build Convention report")
    semgrep = read_json(SEMGREP_REPORT, "Semgrep report")
    if build.get("status") not in ("PASS", "FAIL"):
        raise ValueError(f"Invalid Build Convention status: {build.get('status')!r}")
    build_exit = int(os.environ["BUILD_EXIT"])
    if (build_exit == 0) != (build.get("status") == "PASS"):
        raise ValueError("Build Convention exit code and report status are inconsistent")
    if int(os.environ["SEMGREP_EXIT"]) != 0:
        raise ValueError("Semgrep execution failed")
    if not isinstance(semgrep.get("results"), list):
        raise ValueError("Semgrep report does not contain a valid results array")

    result, cli_exit = run_risk_gate()
    write_result(result)
    decision = result["decision"]
    write_summary("Human review required" if decision == "REVIEW" else "Assessment complete", result)
    if decision == "BLOCK":
        return 3
    if decision == "ERROR":
        return 4
    # Existing CI treats REVIEW as human-required but non-blocking.
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        result = error_result()
        try:
            write_result(result)
        except Exception:
            pass
        write_summary(f"Assessment failed: {type(error).__name__}", result)
        print(f"Risk Gate failed: {type(error).__name__}: {error}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(4)
