import json
import tempfile
import unittest
from pathlib import Path

from scripts.review.verify_vscode_launch import verify_launch_config


def _write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _write_application(root: Path, module: str, package_path: str, class_name: str) -> None:
    _write_text(
        root / f"backend/{module}/src/main/java/{package_path}/{class_name}.java",
        f"""
package {package_path.replace('/', '.')};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class {class_name} {{
    public static void main(String[] args) {{
        SpringApplication.run({class_name}.class, args);
    }}
}}
""".strip(),
    )


class VscodeLaunchVerifierTest(unittest.TestCase):
    def test_aligned_launch_config_passes(self):
        expected = {
            "ai-service": "com.medconsult.ai.AiServiceApplication",
            "auth-service": "com.medconsult.auth.AuthServiceApplication",
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_application(root, "ai-service", "com/medconsult/ai", "AiServiceApplication")
            _write_application(root, "auth-service", "com/medconsult/auth", "AuthServiceApplication")
            _write_text(
                root / "backend/medconsult-common/common-web/src/test/java/com/medconsult/common/web/TestApplication.java",
                "@SpringBootApplication\nclass TestApplication {}\n",
            )
            launch = {
                "version": "0.2.0",
                "configurations": [
                    {
                        "type": "java",
                        "name": "Spring Boot-AiServiceApplication<ai-service>",
                        "request": "launch",
                        "cwd": "${workspaceFolder}/backend/ai-service",
                        "mainClass": "com.medconsult.ai.AiServiceApplication",
                        "projectName": "ai-service",
                        "classPaths": ["$Runtime"],
                    },
                    {
                        "type": "java",
                        "name": "Spring Boot-AuthServiceApplication<auth-service>",
                        "request": "launch",
                        "cwd": "${workspaceFolder}/backend/auth-service",
                        "mainClass": "com.medconsult.auth.AuthServiceApplication",
                        "projectName": "auth-service",
                        "classPaths": ["$Runtime"],
                    },
                ],
            }
            _write_text(root / ".vscode/launch.json", json.dumps(launch, ensure_ascii=False))

            errors = verify_launch_config(root, expected_services=expected)

        self.assertEqual([], errors)

    def test_reports_non_service_entries_bad_cwd_envfile_and_classpath(self):
        expected = {"ai-service": "com.medconsult.ai.AiServiceApplication"}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_application(root, "ai-service", "com/medconsult/ai", "AiServiceApplication")
            launch = {
                "version": "0.2.0",
                "configurations": [
                    {
                        "type": "java",
                        "name": "Spring Boot-AiServiceApplication<ai-service>",
                        "request": "launch",
                        "cwd": "${workspaceFolder}/backend",
                        "mainClass": "com.medconsult.ai.AiServiceApplication",
                        "projectName": "ai-service",
                        "classPaths": ["$Runtime", "$Test"],
                        "envFile": "${workspaceFolder}/.env.local",
                    },
                    {
                        "type": "java",
                        "name": "Spring Boot-TestApplication<common-web>",
                        "request": "launch",
                        "cwd": "${workspaceFolder}/backend/medconsult-common/common-web",
                        "mainClass": "com.medconsult.common.web.TestApplication",
                        "projectName": "common-web",
                        "classPaths": ["$Runtime"],
                    },
                ],
            }
            _write_text(root / ".vscode/launch.json", json.dumps(launch, ensure_ascii=False))

            errors = verify_launch_config(root, expected_services=expected)

        self.assertTrue(any("forbidden launch project common-web" in error for error in errors), errors)
        self.assertTrue(any("ai-service cwd must be ${workspaceFolder}/backend/ai-service" in error for error in errors), errors)
        self.assertTrue(any("ai-service classPaths must be ['$Runtime']" in error for error in errors), errors)
        self.assertTrue(any("ai-service must not declare envFile" in error for error in errors), errors)

    def test_rejects_common_test_application_with_main_method(self):
        expected = {"ai-service": "com.medconsult.ai.AiServiceApplication"}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_application(root, "ai-service", "com/medconsult/ai", "AiServiceApplication")
            _write_text(
                root / "backend/medconsult-common/common-mq/src/test/java/com/medconsult/common/mq/MqTestApplication.java",
                """
@SpringBootApplication
class MqTestApplication {
    public static void main(String[] args) {}
}
""".strip(),
            )
            launch = {
                "version": "0.2.0",
                "configurations": [
                    {
                        "type": "java",
                        "name": "Spring Boot-AiServiceApplication<ai-service>",
                        "request": "launch",
                        "cwd": "${workspaceFolder}/backend/ai-service",
                        "mainClass": "com.medconsult.ai.AiServiceApplication",
                        "projectName": "ai-service",
                        "classPaths": ["$Runtime"],
                    }
                ],
            }
            _write_text(root / ".vscode/launch.json", json.dumps(launch, ensure_ascii=False))

            errors = verify_launch_config(root, expected_services=expected)

        self.assertTrue(any("common test anchor must not declare main" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
