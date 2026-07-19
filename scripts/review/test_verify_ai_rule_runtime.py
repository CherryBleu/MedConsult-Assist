import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.review.verify_ai_rule_runtime import (
    FAILURE_PATTERNS,
    build_urls,
    endpoint_status_is_acceptable,
    main,
    scan_log_file,
)


class AiRuleRuntimeVerifierTest(unittest.TestCase):
    def test_build_urls_normalizes_base_url_and_rag_paths(self):
        urls = build_urls("http://127.0.0.1:8086/")

        self.assertEqual(
            [
                "http://127.0.0.1:8086/api/v1/ai/rag/readiness?refresh=true",
                "http://127.0.0.1:8086/api/v1/ai/rag/probes",
            ],
            urls,
        )

    def test_scan_log_file_reports_enabled_column_and_degrade_keywords(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = Path(tmp) / "ai-service.log"
            log_path.write_text(
                "java.sql.SQLSyntaxErrorException: Unknown column 'enabled'\n"
                "RiskRuleEngine 降级到旧 snapshot\n",
                encoding="utf-8",
            )

            findings = scan_log_file(log_path, FAILURE_PATTERNS)

        self.assertEqual(2, len(findings), findings)
        self.assertTrue(any("Unknown column 'enabled'" in finding for finding in findings))
        self.assertTrue(any("RiskRuleEngine 降级" in finding for finding in findings))

    def test_scan_log_file_passes_when_failure_keywords_absent(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = Path(tmp) / "ai-service.log"
            log_path.write_text("[RAG-READINESS] ready=true checks=[]\n", encoding="utf-8")

            findings = scan_log_file(log_path, FAILURE_PATTERNS)

        self.assertEqual([], findings)

    def test_endpoint_status_accepts_success_or_auth_required_only(self):
        self.assertTrue(endpoint_status_is_acceptable(200))
        self.assertTrue(endpoint_status_is_acceptable(403))
        self.assertFalse(endpoint_status_is_acceptable(404))
        self.assertFalse(endpoint_status_is_acceptable(500))

    def test_self_check_cli_passes_without_service(self):
        self.assertEqual(0, main(["--self-check"]))

    def test_cli_reports_service_not_running(self):
        with patch("sys.stderr") as stderr:
            exit_code = main(["--base-url", "http://127.0.0.1:1", "--timeout", "0.2"])

        self.assertEqual(1, exit_code)
        self.assertIn("ai-service is not reachable at 127.0.0.1:1", "".join(call.args[0] for call in stderr.write.call_args_list))

    def test_cli_rejects_non_positive_timeout(self):
        with patch("sys.stderr") as stderr:
            exit_code = main(["--timeout", "0"])

        self.assertEqual(1, exit_code)
        self.assertIn("timeout must be greater than 0", "".join(call.args[0] for call in stderr.write.call_args_list))


if __name__ == "__main__":
    unittest.main()
