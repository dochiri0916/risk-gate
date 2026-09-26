import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("risk_gate.py")
WORKFLOW = SCRIPT.parents[2] / ".github/workflows/risk-gate.yml"


class ReusableWorkflowTests(unittest.TestCase):
    def test_caller_and_tool_repositories_have_separate_roles(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("  workflow_call:", workflow)
        self.assertIn("BUILD_CONVENTION_PATH: ${{ github.workspace }}/build-convention", workflow)
        self.assertNotIn("BUILD_CONVENTION_DIR", workflow)
        self.assertLess(workflow.index("fetch-depth: 0"), workflow.index("repository: dochiri0916/build-convention"))
        self.assertIn("repository: dochiri0916/risk-gate\n          path: .risk-gate", workflow)
        self.assertIn('./gradlew check -PchangedCoverageBaseRef="origin/${{ github.base_ref }}"', workflow)
        self.assertIn("--exclude build-convention --exclude .risk-gate", workflow)
        self.assertIn("working-directory: .risk-gate\n        run: ./gradlew bootJar", workflow)
        self.assertIn("run: python3 .risk-gate/scripts/ci/risk_gate.py", workflow)

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
