import importlib.util
import contextlib
import io
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "data-cleanup" / "data_cleanup_dry_run.py"
DEFAULT_CONFIG = ROOT / "scripts" / "data-cleanup" / "default-whitelist.json"


def load_module():
    if not MODULE_PATH.exists():
        raise AssertionError(f"missing implementation module: {MODULE_PATH}")
    spec = importlib.util.spec_from_file_location("data_cleanup_dry_run", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class DataCleanupDryRunTest(unittest.TestCase):
    def test_default_whitelist_covers_seed_roles(self):
        module = load_module()

        config = module.load_config(DEFAULT_CONFIG, overrides={})
        protected = module.protected_identity_sets(config)

        self.assertEqual({"admin", "doctor", "patient", "yaofang"}, protected["accounts"])
        self.assertEqual({1, 2, 3, 4}, protected["user_ids"])
        self.assertEqual(
            {"HOSPITAL_ADMIN", "DOCTOR", "PATIENT", "PHARMACY_ADMIN"},
            {identity["role"] for identity in config["protected_identities"]},
        )

    def test_config_must_include_required_seed_whitelist_identities(self):
        module = load_module()
        incomplete_config = {
            "version": 1,
            "protected_identities": [
                {
                    "source": "test",
                    "role": "HOSPITAL_ADMIN",
                    "user_id": 1,
                    "user_no": "U0000001",
                    "account": "admin",
                    "phone": "13800000000",
                    "patient_id": None,
                },
                {
                    "source": "test",
                    "role": "DOCTOR",
                    "user_id": 2,
                    "user_no": "U0000002",
                    "account": "doctor",
                    "phone": "13800000002",
                    "patient_id": None,
                },
                {
                    "source": "test",
                    "role": "PATIENT",
                    "user_id": 3,
                    "user_no": "U0000003",
                    "account": "patient",
                    "phone": "13800000001",
                    "patient_id": 3001,
                },
            ],
        }
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as tmp:
            json.dump(incomplete_config, tmp, ensure_ascii=False)
            tmp_path = pathlib.Path(tmp.name)

        try:
            with self.assertRaisesRegex(ValueError, "PHARMACY_ADMIN.*yaofang"):
                module.load_config(tmp_path, overrides={})
        finally:
            tmp_path.unlink(missing_ok=True)

    def test_default_plan_lists_all_data_domains(self):
        module = load_module()
        config = module.load_config(DEFAULT_CONFIG, overrides={})

        plan = module.build_plan(config, snapshot=None)

        self.assertEqual(
            {
                "business_mysql",
                "redis",
                "mongo",
                "milvus",
                "milvus_internal_minio_etcd",
                "app_image_minio",
            },
            {domain["id"] for domain in plan["domains"]},
        )

    def test_snapshot_candidates_exclude_whitelisted_seed_users(self):
        module = load_module()
        config = module.load_config(DEFAULT_CONFIG, overrides={})
        snapshot = {
            "mysql": {
                "medconsult_auth.sys_user": [
                    {
                        "id": 1,
                        "account": "admin",
                        "phone": "13800000000",
                        "name": "系统管理员",
                        "role": "HOSPITAL_ADMIN",
                        "patient_id": None,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                    {
                        "id": 99,
                        "account": "test_patient_legacy",
                        "phone": "18800009999",
                        "name": "测试患者",
                        "role": "PATIENT",
                        "patient_id": None,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                    {
                        "id": 100,
                        "account": "patient_normal",
                        "phone": "18800001000",
                        "name": "正常患者",
                        "role": "PATIENT",
                        "patient_id": 3002,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                ],
                "medconsult_auth.login_log": [
                    {"id": 500, "user_id": 99, "account": "test_patient_legacy"},
                    {"id": 501, "user_id": 1, "account": "admin"},
                ],
            }
        }

        candidates = module.identify_candidates(config, snapshot)

        self.assertEqual(
            ["test_patient_legacy"],
            [item["account"] for item in candidates["business_mysql"]["sys_user"]],
        )
        self.assertEqual([500], [item["id"] for item in candidates["business_mysql"]["login_log"]])

    def test_allow_phone_and_account_protect_patient_and_linked_outpatient_rows(self):
        module = load_module()
        config = module.load_config(
            DEFAULT_CONFIG,
            overrides={
                "accounts": ["protected_patient_account"],
                "phones": ["18800009999"],
            },
        )
        snapshot = {
            "mysql": {
                "medconsult_auth.sys_user": [
                    {
                        "id": 90,
                        "account": "protected_patient_account",
                        "phone": "18800009000",
                        "name": "测试保护账号",
                        "role": "PATIENT",
                        "patient_id": 9901,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                    {
                        "id": 91,
                        "account": "test_linked_user",
                        "phone": "18800009100",
                        "name": "测试同患者账号",
                        "role": "PATIENT",
                        "patient_id": 9901,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                    {
                        "id": 92,
                        "account": "test_other_patient",
                        "phone": "18800009200",
                        "name": "测试未保护账号",
                        "role": "PATIENT",
                        "patient_id": 9902,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    },
                ],
                "medconsult_patient.patient": [
                    {
                        "id": 9901,
                        "patient_no": "P_TEST_9901",
                        "name": "测试保护患者",
                        "phone": "18800009999",
                        "deleted": 0,
                    },
                    {
                        "id": 9902,
                        "patient_no": "P_TEST_9902",
                        "name": "测试未保护患者",
                        "phone": "18800009200",
                        "deleted": 0,
                    },
                ],
                "medconsult_outpatient.appointment": [
                    {
                        "id": 7001,
                        "appointment_no": "A_TEST_7001",
                        "patient_id": 9901,
                        "name": "测试保护预约",
                        "deleted": 0,
                    },
                    {
                        "id": 7002,
                        "appointment_no": "A_TEST_7002",
                        "patient_id": 9902,
                        "name": "测试未保护预约",
                        "deleted": 0,
                    },
                ],
                "medconsult_outpatient.refund_order": [
                    {
                        "id": 8001,
                        "refund_no": "R_TEST_8001",
                        "patient_id": 9901,
                        "deleted": 0,
                    },
                    {
                        "id": 8002,
                        "refund_no": "R_TEST_8002",
                        "patient_id": 9902,
                        "deleted": 0,
                    },
                ],
            }
        }

        candidates = module.identify_candidates(config, snapshot)
        business_candidates = candidates["business_mysql"]

        self.assertEqual(
            ["test_other_patient"],
            [item["account"] for item in business_candidates["sys_user"]],
        )
        self.assertEqual([9902], [item["id"] for item in business_candidates["patient"]])
        self.assertEqual([7002], [item["id"] for item in business_candidates["appointment"]])
        self.assertEqual([8002], [item["id"] for item in business_candidates["refund_order"]])

    def test_read_only_guard_rejects_destructive_operations(self):
        module = load_module()
        config = module.load_config(DEFAULT_CONFIG, overrides={})
        plan = module.build_plan(config, snapshot=None)

        module.validate_plan_is_read_only(plan)

        bad_plan = {
            "operations": [
                {
                    "stage": "candidate",
                    "domain": "business_mysql",
                    "kind": "sql",
                    "command": "DELETE FROM medconsult_auth.sys_user WHERE id = 99",
                }
            ]
        }
        with self.assertRaises(ValueError):
            module.validate_plan_is_read_only(bad_plan)

    def test_cli_has_no_execute_mode_and_self_check_passes(self):
        module = load_module()

        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                module.parse_args(["--execute"])

        result = subprocess.run(
            [sys.executable, str(MODULE_PATH), "--self-check"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual("", result.stderr)
        self.assertEqual(0, result.returncode)
        self.assertIn("PASS", result.stdout)

    def test_cli_can_identify_candidates_from_offline_snapshot(self):
        snapshot = {
            "mysql": {
                "medconsult_auth.sys_user": [
                    {
                        "id": 101,
                        "account": "12345678",
                        "phone": "12345678",
                        "name": "旧测试患者",
                        "role": "PATIENT",
                        "patient_id": None,
                        "doctor_id": None,
                        "pharmacist_id": None,
                        "deleted": 0,
                    }
                ]
            }
        }
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as tmp:
            json.dump(snapshot, tmp, ensure_ascii=False)
            tmp_path = tmp.name

        try:
            result = subprocess.run(
                [sys.executable, str(MODULE_PATH), "--dry-run", "--snapshot", tmp_path],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
        finally:
            pathlib.Path(tmp_path).unlink(missing_ok=True)

        self.assertEqual("", result.stderr)
        self.assertEqual(0, result.returncode)
        self.assertIn("12345678", result.stdout)
        self.assertIn("真实删除需要未来单独确认", result.stdout)


if __name__ == "__main__":
    unittest.main()
