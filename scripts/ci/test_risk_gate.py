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

    def test_script_uses_caller_for_diff_and_reports_and_tool_for_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory).resolve()
            tool = workspace / ".risk-gate"
            tool.mkdir()
            with patch.dict(os.environ, {"GITHUB_WORKSPACE": str(workspace), "RISK_GATE_PATH": str(tool)}):
                spec = importlib.util.spec_from_file_location("risk_gate_test_target", SCRIPT)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
            self.assertEqual(module.BUILD, workspace / "build/reports/build-convention/report.json")
            self.assertEqual(module.SEMGREP, workspace / "build/reports/semgrep/report.json")
            self.assertEqual(module.RESPONSE, workspace / "build/reports/risk-gate/response.json")
            self.assertEqual(module.RISK_GATE, tool)
            with patch.object(module.subprocess, "check_output", return_value=b"M\0src/App.java\0") as diff:
                self.assertEqual(module.changes("main"), [{"path": "src/App.java", "changeType": "MODIFIED"}])
            self.assertEqual(diff.call_args.kwargs["cwd"], workspace)


if __name__ == "__main__":
    unittest.main()
