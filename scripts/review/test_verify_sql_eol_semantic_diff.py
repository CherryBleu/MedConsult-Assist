import unittest
from unittest.mock import patch

from scripts.review.verify_sql_eol_semantic_diff import (
    analyze_bytes,
    git_blob_sha1,
    main,
)


class SqlEolSemanticDiffVerifierTest(unittest.TestCase):
    def test_detects_line_ending_only_worktree_diff(self):
        head = b"SELECT 1;\nSELECT 2;\n"
        worktree = b"SELECT 1;\r\nSELECT 2;\n"

        report = analyze_bytes(head, worktree)

        self.assertFalse(report.raw_equal)
        self.assertTrue(report.normalized_equal)
        self.assertTrue(report.line_ending_only)
        self.assertEqual(1, report.worktree_counts.crlf)
        self.assertEqual(1, report.worktree_counts.lf_only)
        self.assertEqual(report.head_sha256, report.worktree_normalized_sha256)

    def test_detects_semantic_diff_after_line_ending_normalization(self):
        head = b"SELECT 1;\n"
        worktree = b"SELECT 2;\r\n"

        report = analyze_bytes(head, worktree)

        self.assertFalse(report.raw_equal)
        self.assertFalse(report.normalized_equal)
        self.assertFalse(report.line_ending_only)

    def test_git_blob_sha1_uses_git_blob_framing(self):
        self.assertEqual(
            "ce013625030ba8dba906f756967f9e9ca394464a",
            git_blob_sha1(b"hello\n"),
        )

    def test_self_check_cli_passes_without_git_or_sql_file(self):
        with patch("sys.stdout") as stdout:
            exit_code = main(["--self-check"])

        self.assertEqual(0, exit_code)
        self.assertIn("self-check passed", "".join(call.args[0] for call in stdout.write.call_args_list))


if __name__ == "__main__":
    unittest.main()
