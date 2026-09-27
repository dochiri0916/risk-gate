import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("risk_gate.py")
WORKFLOW = SCRIPT.parents[2] / ".github/workflows/risk-gate.yml"
CONTEXT = SCRIPT.with_name("pr_context.py")


class ReusableWorkflowTests(unittest.TestCase):
    def test_caller_and_tool_repositories_have_separate_roles(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("  workflow_call:", workflow)
        self.assertIn("BUILD_CONVENTION_PATH: ${{ github.workspace }}/build-convention", workflow)
        self.assertNotIn("BUILD_CONVENTION_DIR", workflow)
        self.assertLess(workflow.index("fetch-depth: 0"), workflow.index("repository: dochiri0916/build-convention"))
        self.assertIn("repository: dochiri0916/risk-gate\n          path: .risk-gate", workflow)
        self.assertIn('-PchangedCoverageBaseRef="origin/${{ steps.pr.outputs.BASE_REF }}"', workflow)
        self.assertIn("pull-requests: write", workflow)
        self.assertIn("run: python3 .risk-gate/scripts/ci/pr_context.py", workflow)
        self.assertIn("--exclude build-convention", workflow)
        self.assertIn("--exclude .risk-gate", workflow)
        self.assertIn("working-directory: .risk-gate\n        run: ./gradlew bootJar", workflow)
        self.assertIn("run: python3 .risk-gate/scripts/ci/risk_gate.py", workflow)
        self.assertNotIn("server.port", workflow)

    def test_pr_context_creates_then_reuses_pr(self):
        spec = importlib.util.spec_from_file_location("pr_context_test_target", CONTEXT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        env = {"EVENT_NAME": "push", "REPOSITORY": "owner/repo", "BRANCH": "feature",
               "BASE_BRANCH": "main", "HEAD_SHA": "new-sha"}
        pr = {"number": 12, "base": {"ref": "main"}}
        with patch.object(module, "find_pr", return_value=None), patch.object(
            module, "gh", return_value=subprocess.CompletedProcess([], 0, json.dumps(pr), "")
        ) as create:
            self.assertEqual(module.resolve(env), ("main", 12, "new-sha"))
            self.assertIn("POST", create.call_args.args)
        with patch.object(module, "find_pr", return_value=pr), patch.object(module, "gh") as create:
            self.assertEqual(module.resolve(env), ("main", 12, "new-sha"))
            create.assert_not_called()

    def test_pr_context_uses_manual_pr(self):
        spec = importlib.util.spec_from_file_location("pr_context_manual_test", CONTEXT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.NamedTemporaryFile(mode="w+", encoding="utf-8") as event:
            json.dump({"pull_request": {"number": 9, "base": {"ref": "main"},
                                         "head": {"sha": "manual-sha"}}}, event)
            event.flush()
            with patch.object(module, "gh") as api:
                self.assertEqual(module.resolve({"EVENT_NAME": "pull_request", "EVENT_PATH": event.name}),
                                 ("main", 9, "manual-sha"))
                api.assert_not_called()

    def test_script_uses_caller_reports_and_tool_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory).resolve()
            tool = workspace / ".risk-gate"
            tool.mkdir()
            with patch.dict(os.environ, {"GITHUB_WORKSPACE": str(workspace), "RISK_GATE_PATH": str(tool)}):
                spec = importlib.util.spec_from_file_location("risk_gate_test_target", SCRIPT)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
            self.assertEqual(module.BUILD_REPORT, workspace / "build/reports/build-convention/report.json")
            self.assertEqual(module.SEMGREP_REPORT, workspace / "build/reports/semgrep/report.json")
            self.assertEqual(module.RISK_GATE_RESPONSE, workspace / "build/reports/risk-gate/response.json")
            self.assertEqual(module.RISK_GATE, tool)

    def test_one_shot_decision_exit_codes(self):
        spec = importlib.util.spec_from_file_location("risk_gate_exit_test", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        jar = Path("risk-gate.jar")
        cases = ((0, "PASS"), (2, "REVIEW"), (3, "BLOCK"))
        for exit_code, decision in cases:
            with self.subTest(decision=decision), patch.dict(os.environ, {
                "BASE_REF": "main", "REPOSITORY": "owner/repo", "HEAD_SHA": "abcdef0", "PR_NUMBER": "1",
                "BUILD_EXIT": "0", "SEMGREP_EXIT": "0",
            }), patch.object(module, "find_boot_jar", return_value=jar), patch.object(
                module.subprocess, "run",
                return_value=subprocess.CompletedProcess([], exit_code,
                    json.dumps({"decision": decision, "reasonCodes": [], "score": 0}), ""),
            ) as run:
                result, actual_exit = module.run_risk_gate()
                self.assertEqual((result["decision"], actual_exit), (decision, exit_code))
                self.assertEqual(run.call_args.args[0][:5], ["java", "-jar", str(jar), "ci", "--project"])

    def test_one_shot_error_contract(self):
        spec = importlib.util.spec_from_file_location("risk_gate_error_test", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with patch.dict(os.environ, {
            "BASE_REF": "main", "REPOSITORY": "owner/repo", "HEAD_SHA": "abcdef0", "PR_NUMBER": "1",
            "BUILD_EXIT": "0", "SEMGREP_EXIT": "0",
        }), patch.object(module, "find_boot_jar", return_value=Path("risk-gate.jar")), patch.object(
            module.subprocess, "run", return_value=subprocess.CompletedProcess([], 4, "", "execution error")
        ):
            self.assertEqual(module.run_risk_gate(), ({"decision": "ERROR", "reasonCodes": ["EXECUTION_ERROR"], "score": 0}, 4))

    def test_main_preserves_results_and_maps_cli_decisions(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory).resolve()
            build_path = workspace / "build/reports/build-convention/report.json"
            semgrep_path = workspace / "build/reports/semgrep/report.json"
            build_path.parent.mkdir(parents=True)
            semgrep_path.parent.mkdir(parents=True)
            build_path.write_text(json.dumps({"status": "PASS"}), encoding="utf-8")
            semgrep_path.write_text(json.dumps({"results": []}), encoding="utf-8")
            env = {"GITHUB_WORKSPACE": str(workspace), "RISK_GATE_PATH": str(workspace / ".risk-gate"),
                   "BUILD_EXIT": "0", "SEMGREP_EXIT": "0", "BASE_REF": "main", "REPOSITORY": "o/r",
                   "HEAD_SHA": "abcdef0", "PR_NUMBER": "1"}
            with patch.dict(os.environ, env):
                spec = importlib.util.spec_from_file_location("risk_gate_main_test", SCRIPT)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                cases = (("PASS", 0, 0), ("REVIEW", 2, 0), ("BLOCK", 3, 3), ("ERROR", 4, 4))
                for decision, cli_exit, runner_exit in cases:
                    with self.subTest(decision=decision), patch.object(module, "run_risk_gate", return_value=(
                        {"decision": decision, "reasonCodes": [decision], "score": 0}, cli_exit
                    )):
                        self.assertEqual(module.main(), runner_exit)
                        result = json.loads((workspace / "build/reports/risk-gate/response.json").read_text(
                            encoding="utf-8"))
                        self.assertEqual(result["decision"], decision)


if __name__ == "__main__":
    unittest.main()
