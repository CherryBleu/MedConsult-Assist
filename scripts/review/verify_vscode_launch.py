from __future__ import annotations

import json
from pathlib import Path
import re
import sys


DEPLOYABLE_SERVICES = {
    "ai-service": "com.medconsult.ai.AiServiceApplication",
    "auth-service": "com.medconsult.auth.AuthServiceApplication",
    "drug-service": "com.medconsult.drug.DrugServiceApplication",
    "gateway": "com.medconsult.gateway.GatewayApplication",
    "medical-record-service": "com.medconsult.medicalrecord.MedicalRecordServiceApplication",
    "notification-service": "com.medconsult.notification.NotificationServiceApplication",
    "outpatient-service": "com.medconsult.outpatient.OutpatientServiceApplication",
    "patient-service": "com.medconsult.patient.PatientServiceApplication",
}
FORBIDDEN_PROJECT_RE = re.compile(r"(^|[-_])(common|data|parent)([-_]|$)|medconsult-common")


def _read_json(path: Path) -> tuple[dict, list[str]]:
    if not path.exists():
        return {}, [f"launch.json not found: {path}"]
    try:
        return json.loads(path.read_text(encoding="utf-8")), []
    except json.JSONDecodeError as exc:
        return {}, [f"launch.json invalid JSON: {exc}"]


def _workspace_path(root: Path, value: str) -> Path:
    return Path(value.replace("${workspaceFolder}", str(root))).resolve()


def _main_class_path(root: Path, project_name: str, main_class: str) -> Path:
    return root / "backend" / project_name / "src/main/java" / Path(*main_class.split(".")).with_suffix(".java")


def _contains_main_for_class(source: str, simple_class: str) -> bool:
    return (
        "public static void main" in source
        and f"SpringApplication.run({simple_class}.class" in source
    )


def _validate_service_config(root: Path, config: dict, expected_main: str) -> list[str]:
    errors: list[str] = []
    project_name = str(config.get("projectName", ""))
    label = project_name or str(config.get("name", "<unnamed>"))
    expected_cwd = f"${{workspaceFolder}}/backend/{project_name}"

    if config.get("type") != "java":
        errors.append(f"{label} type must be java")
    if config.get("request") != "launch":
        errors.append(f"{label} request must be launch")
    if config.get("cwd") != expected_cwd:
        errors.append(f"{label} cwd must be {expected_cwd}")
    elif not _workspace_path(root, expected_cwd).exists():
        errors.append(f"{label} cwd does not exist: {expected_cwd}")
    if config.get("mainClass") != expected_main:
        errors.append(f"{label} mainClass must be {expected_main}")
    if config.get("classPaths") != ["$Runtime"]:
        errors.append(f"{label} classPaths must be ['$Runtime']")
    if "envFile" in config:
        errors.append(f"{label} must not declare envFile")

    main_path = _main_class_path(root, project_name, expected_main)
    if not main_path.exists():
        errors.append(f"{label} mainClass file not found: {main_path.relative_to(root)}")
    else:
        source = main_path.read_text(encoding="utf-8", errors="ignore")
        if "@SpringBootApplication" not in source:
            errors.append(f"{label} mainClass lacks @SpringBootApplication")
        if not _contains_main_for_class(source, expected_main.rsplit(".", 1)[-1]):
            errors.append(f"{label} mainClass lacks SpringApplication.run main method")

    return errors


def _validate_common_test_anchors(root: Path) -> list[str]:
    errors: list[str] = []
    common_root = root / "backend/medconsult-common"
    if not common_root.exists():
        return errors

    for path in common_root.glob("*/src/test/java/**/*Application.java"):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if "@SpringBootApplication" in source and (
            "public static void main" in source or "SpringApplication.run" in source
        ):
            errors.append(f"common test anchor must not declare main: {path.relative_to(root)}")
    return errors


def verify_launch_config(
    root: Path,
    expected_services: dict[str, str] | None = None,
) -> list[str]:
    root = root.resolve()
    expected = expected_services or DEPLOYABLE_SERVICES
    payload, errors = _read_json(root / ".vscode/launch.json")
    if errors:
        return errors

    configurations = payload.get("configurations", [])
    if not isinstance(configurations, list):
        return ["launch.json configurations must be a list"]

    project_names = [str(config.get("projectName", "")) for config in configurations]
    actual_service_names = {name for name in project_names if name in expected}
    missing = sorted(set(expected) - actual_service_names)
    extra = sorted(name for name in project_names if name and name not in expected)
    if missing:
        errors.append(f"missing launch projects: {', '.join(missing)}")
    for name in extra:
        if FORBIDDEN_PROJECT_RE.search(name):
            errors.append(f"forbidden launch project {name}: only deployable Spring Boot services are allowed")
        else:
            errors.append(f"unexpected launch project {name}")

    seen: set[str] = set()
    for config in configurations:
        project_name = str(config.get("projectName", ""))
        if project_name in seen:
            errors.append(f"duplicate launch project {project_name}")
        seen.add(project_name)

        if project_name in expected:
            errors.extend(_validate_service_config(root, config, expected[project_name]))

    errors.extend(_validate_common_test_anchors(root))
    return errors


def main(argv: list[str] | None = None) -> int:
    args = argv if argv is not None else sys.argv[1:]
    root = Path(args[0]).resolve() if args else Path.cwd()
    errors = verify_launch_config(root)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"VS Code launch 检查通过: {len(DEPLOYABLE_SERVICES)} services")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
