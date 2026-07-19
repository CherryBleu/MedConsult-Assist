import tempfile
import unittest
from pathlib import Path

from scripts.review.verify_contract_matrix import parse_matrix, verify_rows


class ContractMatrixVerifierTest(unittest.TestCase):
    def test_parse_matrix_reads_contract_rows(self):
        matrix = """| Domain | Method | Public Path | Docs Needle | Frontend | Frontend Needle | Backend | Controller Prefix | Method Suffix | Permission | Table | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| auth | POST | `/api/v1/auth/login` | `/auth/login` | `frontend/src/api/user.js` | `/auth/login` | `backend/AuthController.java` | `/api/v1/auth` | `/login` | `@PostMapping` | `login_log` | `backend/AuthFlowTest.java` | aligned |
"""

        rows = parse_matrix(matrix)

        self.assertEqual(1, len(rows))
        self.assertEqual("auth", rows[0].domain)
        self.assertEqual("POST", rows[0].method)
        self.assertEqual("/api/v1/auth/login", rows[0].public_path)
        self.assertEqual("login_log", rows[0].table)

    def test_reports_missing_docs_frontend_backend_permission_table_and_test(self):
        matrix = """| Domain | Method | Public Path | Docs Needle | Frontend | Frontend Needle | Backend | Controller Prefix | Method Suffix | Permission | Table | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| auth | POST | /api/v1/auth/login | /auth/login | frontend/src/api/user.js | /auth/login | backend/AuthController.java | /api/v1/auth | /login | roles = {"HOSPITAL_ADMIN"} | login_log | backend/AuthFlowTest.java | aligned |
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "frontend/src/api").mkdir(parents=True)
            (root / "backend").mkdir()
            (root / "docs/接口文档.md").write_text("/auth/register", encoding="utf-8")
            (root / "docs/数据库设计文档.md").write_text("sys_user", encoding="utf-8")
            (root / "frontend/src/api/user.js").write_text("/auth/register", encoding="utf-8")
            (root / "backend/AuthController.java").write_text(
                '@RequestMapping("/api/v1/auth")\n@PostMapping("/register")\n@Permission(roles = {"PATIENT"})',
                encoding="utf-8",
            )

            errors = verify_rows(root, parse_matrix(matrix))

        self.assertEqual(6, len(errors), errors)
        self.assertTrue(any("docs" in error for error in errors), errors)
        self.assertTrue(any("frontend" in error for error in errors), errors)
        self.assertTrue(any("backend" in error for error in errors), errors)
        self.assertTrue(any("权限" in error for error in errors), errors)
        self.assertTrue(any("数据表" in error for error in errors), errors)
        self.assertTrue(any("测试" in error for error in errors), errors)

    def test_aligned_row_passes(self):
        matrix = """| Domain | Method | Public Path | Docs Needle | Frontend | Frontend Needle | Backend | Controller Prefix | Method Suffix | Permission | Table | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| auth | POST | /api/v1/auth/login | /auth/login | frontend/src/api/user.js | /auth/login | backend/AuthController.java | /api/v1/auth | /login | clientType | login_log | backend/AuthFlowTest.java | aligned |
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "frontend/src/api").mkdir(parents=True)
            (root / "backend").mkdir()
            (root / "docs/接口文档.md").write_text("POST /auth/login", encoding="utf-8")
            (root / "docs/数据库设计文档.md").write_text("login_log", encoding="utf-8")
            (root / "frontend/src/api/user.js").write_text("axios.post('/auth/login')", encoding="utf-8")
            (root / "backend/AuthController.java").write_text(
                '@RequestMapping("/api/v1/auth")\n@PostMapping("/login")\nString clientType;',
                encoding="utf-8",
            )
            (root / "backend/AuthFlowTest.java").write_text("login flow", encoding="utf-8")

            errors = verify_rows(root, parse_matrix(matrix))

        self.assertEqual([], errors)

    def test_reports_aligned_status_without_test_evidence(self):
        matrix = """| Domain | Method | Public Path | Docs Needle | Frontend | Frontend Needle | Backend | Controller Prefix | Method Suffix | Permission | Table | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| auth | POST | /api/v1/auth/login | /auth/login | frontend/src/api/user.js | /auth/login | backend/AuthController.java | /api/v1/auth | /login | clientType | login_log | — | aligned |
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "frontend/src/api").mkdir(parents=True)
            (root / "backend").mkdir()
            (root / "docs/接口文档.md").write_text("POST /auth/login", encoding="utf-8")
            (root / "docs/数据库设计文档.md").write_text("login_log", encoding="utf-8")
            (root / "frontend/src/api/user.js").write_text("axios.post('/auth/login')", encoding="utf-8")
            (root / "backend/AuthController.java").write_text(
                '@RequestMapping("/api/v1/auth")\n@PostMapping("/login")\nString clientType;',
                encoding="utf-8",
            )

            errors = verify_rows(root, parse_matrix(matrix))

        self.assertTrue(any("测试" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
