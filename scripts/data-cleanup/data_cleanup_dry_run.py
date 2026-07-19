#!/usr/bin/env python3
"""
Dry-run planner for MedConsult test-data cleanup.

The CLI intentionally has no execution mode and no service connection options.
It only renders a reviewable plan, read-only inventory commands, and offline
candidate matches from an exported JSON snapshot when one is provided.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
from typing import Any


DEFAULT_CONFIG_PATH = pathlib.Path(__file__).with_name("default-whitelist.json")

DANGEROUS_COMMAND = re.compile(
    r"(?ix)"
    r"(^|\s|[\"'])("
    r"delete|truncate|drop|update|insert|replace|alter|create|merge|call|exec|execute|"
    r"del|remove|deleteone|deletemany|dropdatabase|dropcollection|drop_collection|rm"
    r")\b"
)

TEST_MARKER = re.compile(r"(?i)(test|demo|mock|legacy|dirty|tmp|sample|12345678|测试|演示|脏|临时)")

MYSQL_SCHEMAS = [
    "medconsult_auth",
    "medconsult_patient",
    "medconsult_outpatient",
    "medconsult_drug",
    "medconsult_medical_record",
    "medconsult_notification",
    "medconsult_ai",
]

DOMAINS = [
    {
        "id": "business_mysql",
        "name": "业务 MySQL",
        "classification": "用户业务数据",
        "boundary": "只识别账号、患者、预约、退款、病历、审计 outbox 等业务表候选；不跨入知识库向量或对象存储内部元数据。",
    },
    {
        "id": "redis",
        "name": "Redis",
        "classification": "业务缓存/锁/会话辅助数据",
        "boundary": "只扫描 key 模式和候选 userId 关联 key；默认不生成 DEL、UNLINK 或批量删键命令。",
    },
    {
        "id": "mongo",
        "name": "Mongo",
        "classification": "医疗知识库文档域",
        "boundary": "作为知识库域单独盘点；不得因清理测试用户而清理知识文档。",
    },
    {
        "id": "milvus",
        "name": "Milvus",
        "classification": "医疗知识库向量域",
        "boundary": "只盘点 collection/partition/row count；不得与用户业务数据混删，不生成 drop collection/partition 命令。",
    },
    {
        "id": "milvus_internal_minio_etcd",
        "name": "Milvus 内部 MinIO/etcd",
        "classification": "Milvus 基础设施内部数据",
        "boundary": "禁止作为应用数据清理目标；仅允许记录备份和健康检查责任边界。",
    },
    {
        "id": "app_image_minio",
        "name": "应用影像 MinIO",
        "classification": "用户上传影像/附件对象",
        "boundary": "与 Milvus 内部 MinIO 分桶、分凭据、分 runbook 处理；只按候选 patientId/userId 做对象清单。",
    },
]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a dry-run-only data cleanup plan. No destructive mode exists."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Render the dry-run plan. This is also the default when omitted.",
    )
    parser.add_argument(
        "--config",
        type=pathlib.Path,
        default=DEFAULT_CONFIG_PATH,
        help="JSON whitelist config. Defaults to scripts/data-cleanup/default-whitelist.json.",
    )
    parser.add_argument(
        "--snapshot",
        type=pathlib.Path,
        help="Optional offline JSON export for candidate identification. The script never connects to services.",
    )
    parser.add_argument(
        "--format",
        choices=["markdown", "json"],
        default="markdown",
        help="Output format for the generated dry-run plan.",
    )
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="Validate that generated operations are read-only and that the guard rejects a destructive sample.",
    )
    parser.add_argument("--allow-account", action="append", default=[], help="Add a protected account.")
    parser.add_argument("--allow-phone", action="append", default=[], help="Add a protected phone.")
    parser.add_argument("--allow-user-id", action="append", type=int, default=[], help="Add a protected sys_user.id.")
    parser.add_argument("--allow-patient-id", action="append", type=int, default=[], help="Add a protected patient.id.")
    return parser.parse_args(argv)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def load_config(path: pathlib.Path, overrides: dict[str, list[Any]] | None = None) -> dict[str, Any]:
    config = load_json(path)
    identities = list(config.get("protected_identities", []))
    overrides = overrides or {}

    for account in overrides.get("accounts", []):
        identities.append(
            {
                "source": "cli --allow-account",
                "role": "CLI_WHITELIST",
                "account": account,
                "phone": None,
                "user_id": None,
                "patient_id": None,
                "name": None,
            }
        )
    for phone in overrides.get("phones", []):
        identities.append(
            {
                "source": "cli --allow-phone",
                "role": "CLI_WHITELIST",
                "account": None,
                "phone": phone,
                "user_id": None,
                "patient_id": None,
                "name": None,
            }
        )
    for user_id in overrides.get("user_ids", []):
        identities.append(
            {
                "source": "cli --allow-user-id",
                "role": "CLI_WHITELIST",
                "account": None,
                "phone": None,
                "user_id": user_id,
                "patient_id": None,
                "name": None,
            }
        )
    for patient_id in overrides.get("patient_ids", []):
        identities.append(
            {
                "source": "cli --allow-patient-id",
                "role": "CLI_WHITELIST",
                "account": None,
                "phone": None,
                "user_id": None,
                "patient_id": patient_id,
                "name": None,
            }
        )

    config["protected_identities"] = identities
    return config


def protected_identity_sets(config: dict[str, Any]) -> dict[str, set[Any]]:
    protected = {
        "accounts": set(),
        "phones": set(),
        "user_ids": set(),
        "patient_ids": set(),
        "user_nos": set(),
    }
    for identity in config.get("protected_identities", []):
        if identity.get("account"):
            protected["accounts"].add(str(identity["account"]))
        if identity.get("phone"):
            protected["phones"].add(str(identity["phone"]))
        if identity.get("user_id") is not None:
            protected["user_ids"].add(int(identity["user_id"]))
        if identity.get("patient_id") is not None:
            protected["patient_ids"].add(int(identity["patient_id"]))
        if identity.get("user_no"):
            protected["user_nos"].add(str(identity["user_no"]))
    return protected


def value(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in row:
            return row[key]
    return None


def identity_fields_are_protected(row: dict[str, Any], protected: dict[str, set[Any]]) -> bool:
    return (
        str(value(row, "account") or "") in protected["accounts"]
        or str(value(row, "phone") or "") in protected["phones"]
        or str(value(row, "user_no", "userNo") or "") in protected["user_nos"]
    )


def is_protected_user(row: dict[str, Any], protected: dict[str, set[Any]]) -> bool:
    row_id = value(row, "id", "user_id")
    patient_id = value(row, "patient_id", "patientId")
    return (
        identity_fields_are_protected(row, protected)
        or (row_id is not None and int(row_id) in protected["user_ids"])
        or (patient_id is not None and int(patient_id) in protected["patient_ids"])
    )


def is_protected_patient(row: dict[str, Any], protected: dict[str, set[Any]]) -> bool:
    patient_id = value(row, "id", "patient_id", "patientId")
    return identity_fields_are_protected(row, protected) or (
        patient_id is not None and int(patient_id) in protected["patient_ids"]
    )


def is_protected_business_row(row: dict[str, Any], protected: dict[str, set[Any]]) -> bool:
    patient_id = value(row, "patient_id", "patientId")
    return identity_fields_are_protected(row, protected) or (
        patient_id is not None and int(patient_id) in protected["patient_ids"]
    )


def infer_protected_patient_ids(
    protected: dict[str, set[Any]],
    sys_users: list[dict[str, Any]],
    patient_rows: list[dict[str, Any]],
) -> set[int]:
    patient_ids = set(protected["patient_ids"])

    for row in sys_users:
        patient_id = value(row, "patient_id", "patientId")
        if patient_id is not None and is_protected_user(row, protected):
            patient_ids.add(int(patient_id))

    protected_with_user_links = {key: set(values) for key, values in protected.items()}
    protected_with_user_links["patient_ids"] = patient_ids
    for row in patient_rows:
        patient_id = value(row, "id", "patient_id", "patientId")
        if patient_id is not None and is_protected_patient(row, protected_with_user_links):
            patient_ids.add(int(patient_id))

    return patient_ids


def text_has_test_marker(row: dict[str, Any]) -> bool:
    fields = [
        value(row, "account"),
        value(row, "phone"),
        value(row, "name"),
        value(row, "user_no", "userNo"),
        value(row, "patient_no", "patientNo"),
    ]
    return any(TEST_MARKER.search(str(field)) for field in fields if field is not None)


def candidate_reason(row: dict[str, Any]) -> str | None:
    role = str(value(row, "role") or "").upper()
    patient_id = value(row, "patient_id", "patientId")
    doctor_id = value(row, "doctor_id", "doctorId")
    pharmacist_id = value(row, "pharmacist_id", "pharmacistId")
    deleted = int(value(row, "deleted") or 0)

    if deleted != 0:
        return None
    if role == "PATIENT" and patient_id is None:
        return "PATIENT account without patient_id"
    if text_has_test_marker(row) and patient_id is None and doctor_id is None and pharmacist_id is None:
        return "test-marker account without linked business identity"
    if text_has_test_marker(row):
        return "test-marker business record"
    return None


def compact_candidate(row: dict[str, Any], reason: str) -> dict[str, Any]:
    keep = [
        "id",
        "user_id",
        "user_no",
        "account",
        "phone",
        "name",
        "role",
        "patient_id",
        "doctor_id",
        "pharmacist_id",
        "patient_no",
        "appointment_no",
        "refund_no",
    ]
    item = {key: row[key] for key in keep if key in row}
    item["reason"] = reason
    return item


def identify_candidates(config: dict[str, Any], snapshot: dict[str, Any] | None) -> dict[str, Any]:
    candidates = {
        "business_mysql": {
            "sys_user": [],
            "login_log": [],
            "patient": [],
            "appointment": [],
            "refund_order": [],
        },
        "redis": {"candidate_key_patterns": []},
        "mongo": {"candidate_documents": []},
        "milvus": {"candidate_collections": []},
        "app_image_minio": {"candidate_objects": []},
    }
    if not snapshot:
        return candidates

    protected = protected_identity_sets(config)
    mysql = snapshot.get("mysql", {})
    sys_users = mysql.get("medconsult_auth.sys_user", [])
    patient_rows = mysql.get("medconsult_patient.patient", [])
    protected["patient_ids"].update(infer_protected_patient_ids(protected, sys_users, patient_rows))

    candidate_user_ids: set[int] = set()
    candidate_accounts: set[str] = set()
    candidate_patient_ids: set[int] = set()

    for row in sys_users:
        if is_protected_user(row, protected):
            continue
        reason = candidate_reason(row)
        if not reason:
            continue
        candidates["business_mysql"]["sys_user"].append(compact_candidate(row, reason))
        if value(row, "id") is not None:
            candidate_user_ids.add(int(value(row, "id")))
        if value(row, "account") is not None:
            candidate_accounts.add(str(value(row, "account")))
        if value(row, "patient_id", "patientId") is not None:
            candidate_patient_ids.add(int(value(row, "patient_id", "patientId")))

    for row in mysql.get("medconsult_auth.login_log", []):
        row_user_id = value(row, "user_id", "userId")
        row_account = value(row, "account")
        if (row_user_id is not None and int(row_user_id) in candidate_user_ids) or (
            row_account is not None and str(row_account) in candidate_accounts
        ):
            candidates["business_mysql"]["login_log"].append(compact_candidate(row, "linked to candidate sys_user"))

    for row in patient_rows:
        patient_id = value(row, "id", "patient_id", "patientId")
        if is_protected_patient(row, protected):
            continue
        if patient_id is not None and int(patient_id) in candidate_patient_ids:
            candidates["business_mysql"]["patient"].append(compact_candidate(row, "linked to candidate patient_id"))
        elif text_has_test_marker(row):
            candidates["business_mysql"]["patient"].append(compact_candidate(row, "test-marker patient record"))

    for row in mysql.get("medconsult_outpatient.appointment", []):
        patient_id = value(row, "patient_id", "patientId")
        if is_protected_business_row(row, protected):
            continue
        if patient_id is not None and int(patient_id) in candidate_patient_ids:
            candidates["business_mysql"]["appointment"].append(compact_candidate(row, "linked to candidate patient_id"))
        elif text_has_test_marker(row):
            candidates["business_mysql"]["appointment"].append(compact_candidate(row, "test-marker appointment record"))

    for row in mysql.get("medconsult_outpatient.refund_order", []):
        patient_id = value(row, "patient_id", "patientId")
        if is_protected_business_row(row, protected):
            continue
        if patient_id is not None and int(patient_id) in candidate_patient_ids:
            candidates["business_mysql"]["refund_order"].append(compact_candidate(row, "linked to candidate patient_id"))

    for user_id in sorted(candidate_user_ids):
        candidates["redis"]["candidate_key_patterns"].append(f"medconsult:auth:role:{user_id}")
        candidates["redis"]["candidate_key_patterns"].append(f"medconsult:auth:refresh:*:{user_id}")

    return candidates


def build_operations(has_snapshot: bool) -> list[dict[str, str]]:
    operations: list[dict[str, str]] = [
        {
            "stage": "preflight",
            "domain": "workspace",
            "kind": "command",
            "command": "git status -sb --untracked-files=all",
            "purpose": "确认仍在 review/long-term-governance，且仅 scripts/data-cleanup/** 发生本任务新增改动。",
        },
        {
            "stage": "stop_writes",
            "domain": "all",
            "kind": "manual",
            "command": "manual: pause public write traffic, admin mutations, background importers, MQ dispatchers, and upload workers",
            "purpose": "真正清理前冻结写入；dry-run 阶段只记录该前置条件。",
        },
    ]

    for schema in MYSQL_SCHEMAS:
        operations.append(
            {
                "stage": "backup",
                "domain": "business_mysql",
                "kind": "command",
                "command": f"mysqldump --single-transaction --routines --events --databases {schema} --result-file backup/{schema}.sql",
                "purpose": f"备份 {schema}，真实清理前必须保留可回滚副本。",
            }
        )

    operations.extend(
        [
            {
                "stage": "backup",
                "domain": "redis",
                "kind": "command",
                "command": "redis-cli --scan --pattern 'medconsult:*'",
                "purpose": "导出 key 清单用于审计；不输出删键命令。",
            },
            {
                "stage": "backup",
                "domain": "mongo",
                "kind": "command",
                "command": "mongodump --db medconsult_knowledge --out backup/mongo-medconsult-knowledge",
                "purpose": "知识库文档域备份；用户数据清理不得混入该域。",
            },
            {
                "stage": "inventory",
                "domain": "milvus",
                "kind": "command",
                "command": "milvus-cli list collections",
                "purpose": "仅列出向量 collection 清单和 row count，禁止清理知识库向量。",
            },
            {
                "stage": "inventory",
                "domain": "milvus_internal_minio_etcd",
                "kind": "manual",
                "command": "manual: record Milvus internal MinIO buckets and etcd snapshot owner; do not include them in user cleanup",
                "purpose": "确认基础设施内部数据不进入应用数据清理范围。",
            },
            {
                "stage": "inventory",
                "domain": "app_image_minio",
                "kind": "command",
                "command": "mc ls --recursive medconsult-app-images",
                "purpose": "只输出应用影像对象清单，未来需按候选 patientId/userId 二次过滤。",
            },
            {
                "stage": "count",
                "domain": "business_mysql",
                "kind": "command",
                "command": "mysql --database medconsult_auth --execute \"SELECT COUNT(*) AS active_users FROM sys_user WHERE deleted = 0;\"",
                "purpose": "记录清理前账号基线计数。",
            },
            {
                "stage": "candidate",
                "domain": "business_mysql",
                "kind": "command",
                "command": (
                    "mysql --database medconsult_auth --execute "
                    "\"SELECT id,user_no,account,phone,name,patient_id,doctor_id,pharmacist_id,status "
                    "FROM sys_user WHERE deleted = 0 AND "
                    "(account REGEXP 'test|demo|mock|legacy|12345678' OR phone = '12345678' "
                    "OR name LIKE '%测试%' OR name LIKE '%演示%' OR patient_id IS NULL) "
                    "ORDER BY created_at;\""
                ),
                "purpose": "只读候选账号查询；结果必须再按白名单人工复核。",
            },
            {
                "stage": "candidate",
                "domain": "business_mysql",
                "kind": "command",
                "command": (
                    "mysql --database medconsult_patient --execute "
                    "\"SELECT id,patient_no,name,phone,status FROM patient WHERE deleted = 0 AND "
                    "(patient_no REGEXP 'test|demo|mock|legacy|12345678' OR phone = '12345678' "
                    "OR name LIKE '%测试%' OR name LIKE '%演示%');\""
                ),
                "purpose": "只读患者档案候选查询；不得跨域清理知识库。",
            },
            {
                "stage": "candidate",
                "domain": "redis",
                "kind": "command",
                "command": "redis-cli --scan --pattern 'medconsult:auth:role:*'",
                "purpose": "只读列出角色缓存 key；未来只允许基于已确认候选 userId 制作白名单化清理清单。",
            },
            {
                "stage": "candidate",
                "domain": "mongo",
                "kind": "command",
                "command": "mongosh medconsult_knowledge --eval \"db.getCollectionNames().forEach(c => print(c + ':' + db[c].estimatedDocumentCount()))\"",
                "purpose": "只读盘点知识库集合计数；不参与测试用户清理。",
            },
            {
                "stage": "verify",
                "domain": "business_mysql",
                "kind": "command",
                "command": "mysql --database medconsult_auth --execute \"SELECT account,phone,id FROM sys_user WHERE account IN ('admin','doctor','patient','yaofang') AND deleted = 0;\"",
                "purpose": "未来真实清理后验证默认白名单账号仍存在。",
            },
        ]
    )

    if has_snapshot:
        operations.append(
            {
                "stage": "candidate",
                "domain": "offline_snapshot",
                "kind": "command",
                "command": "python scripts/data-cleanup/data_cleanup_dry_run.py --dry-run --snapshot <snapshot.json>",
                "purpose": "用离线导出文件重新生成候选清单；该命令不连接服务。",
            }
        )

    return operations


def build_plan(config: dict[str, Any], snapshot: dict[str, Any] | None) -> dict[str, Any]:
    candidates = identify_candidates(config, snapshot)
    return {
        "mode": "dry-run-only",
        "generated_at": dt.datetime.now(dt.UTC).isoformat(timespec="seconds"),
        "safety": [
            "脚本没有真实清理模式，也不接收数据库、Redis、Mongo、Milvus 或 MinIO 连接参数。",
            "默认只输出计划、只读盘点命令、候选识别规则和离线快照候选。",
            "真实删除需要未来单独确认，并应另建经过审批的 runbook。",
        ],
        "whitelist": config.get("protected_identities", []),
        "domains": DOMAINS,
        "operations": build_operations(snapshot is not None),
        "candidates": candidates,
    }


def validate_plan_is_read_only(plan: dict[str, Any]) -> None:
    for index, operation in enumerate(plan.get("operations", []), start=1):
        command = str(operation.get("command", ""))
        if DANGEROUS_COMMAND.search(command):
            raise ValueError(f"operation {index} is not read-only: {command}")


def count_candidates(candidates: dict[str, Any]) -> dict[str, int]:
    business = candidates.get("business_mysql", {})
    return {
        "sys_user": len(business.get("sys_user", [])),
        "login_log": len(business.get("login_log", [])),
        "patient": len(business.get("patient", [])),
        "appointment": len(business.get("appointment", [])),
        "refund_order": len(business.get("refund_order", [])),
        "redis_key_patterns": len(candidates.get("redis", {}).get("candidate_key_patterns", [])),
    }


def render_markdown(plan: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# MedConsult 数据清理 dry-run 方案")
    lines.append("")
    lines.append(f"- 模式：{plan['mode']}")
    lines.append(f"- 生成时间：{plan['generated_at']}")
    lines.append("- 结论：真实删除需要未来单独确认；本脚本默认能力内不执行真实删除。")
    lines.append("")
    lines.append("## 安全边界")
    for item in plan["safety"]:
        lines.append(f"- {item}")
    lines.append("")
    lines.append("## 默认保护白名单")
    lines.append("| role | user_id | account | phone | user_no | patient_id | source |")
    lines.append("| --- | ---: | --- | --- | --- | ---: | --- |")
    for identity in plan["whitelist"]:
        lines.append(
            "| {role} | {user_id} | {account} | {phone} | {user_no} | {patient_id} | {source} |".format(
                role=identity.get("role") or "",
                user_id=identity.get("user_id") if identity.get("user_id") is not None else "",
                account=identity.get("account") or "",
                phone=identity.get("phone") or "",
                user_no=identity.get("user_no") or "",
                patient_id=identity.get("patient_id") if identity.get("patient_id") is not None else "",
                source=identity.get("source") or "",
            )
        )
    lines.append("")
    lines.append("## 数据域边界")
    lines.append("| id | name | classification | boundary |")
    lines.append("| --- | --- | --- | --- |")
    for domain in plan["domains"]:
        lines.append(f"| {domain['id']} | {domain['name']} | {domain['classification']} | {domain['boundary']} |")
    lines.append("")
    lines.append("## 备份、停写、计数、候选、验证步骤")
    for operation in plan["operations"]:
        lines.append(f"- [{operation['stage']}] {operation['domain']}: {operation['purpose']}")
        lines.append(f"  `{operation['command']}`")
    lines.append("")
    lines.append("## 离线候选摘要")
    counts = count_candidates(plan["candidates"])
    if sum(counts.values()) == 0:
        lines.append("- 未提供离线快照或快照未命中候选；请先按上方只读查询导出，再用 `--snapshot` 复跑。")
    else:
        for key, count in counts.items():
            lines.append(f"- {key}: {count}")
    lines.append("")
    for table_name in ["sys_user", "login_log", "patient", "appointment", "refund_order"]:
        items = plan["candidates"].get("business_mysql", {}).get(table_name, [])
        if not items:
            continue
        lines.append(f"### {table_name}")
        for item in items:
            identity = item.get("account") or item.get("patient_no") or item.get("appointment_no") or item.get("id")
            lines.append(f"- {identity}: {item.get('reason')}")
        lines.append("")
    redis_patterns = plan["candidates"].get("redis", {}).get("candidate_key_patterns", [])
    if redis_patterns:
        lines.append("### redis candidate key patterns")
        for pattern in redis_patterns:
            lines.append(f"- {pattern}")
        lines.append("")
    lines.append("## 后续真实清理门槛")
    lines.append("- 需要单独确认目标环境、白名单、候选 ID、备份路径、停写窗口和回滚步骤。")
    lines.append("- 医疗知识库 Mongo/Milvus、Milvus 内部 MinIO/etcd、应用影像 MinIO 必须按域拆分，不能和用户业务数据混删。")
    return "\n".join(lines)


def overrides_from_args(args: argparse.Namespace) -> dict[str, list[Any]]:
    return {
        "accounts": args.allow_account,
        "phones": args.allow_phone,
        "user_ids": args.allow_user_id,
        "patient_ids": args.allow_patient_id,
    }


def run_self_check(config: dict[str, Any], snapshot: dict[str, Any] | None) -> str:
    plan = build_plan(config, snapshot)
    validate_plan_is_read_only(plan)

    rejected_destructive_sample = False
    try:
        validate_plan_is_read_only(
            {
                "operations": [
                    {
                        "stage": "candidate",
                        "domain": "business_mysql",
                        "kind": "sql",
                        "command": "DELETE FROM medconsult_auth.sys_user WHERE id = 99",
                    }
                ]
            }
        )
    except ValueError:
        rejected_destructive_sample = True

    if not rejected_destructive_sample:
        raise AssertionError("destructive sample was not rejected")

    return (
        "PASS: dry-run plan contains "
        f"{len(plan['operations'])} read-only operations; destructive-operation guard rejected sample DELETE."
    )


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    config = load_config(args.config, overrides_from_args(args))
    snapshot = load_json(args.snapshot) if args.snapshot else None

    if args.self_check:
        print(run_self_check(config, snapshot))
        return 0

    plan = build_plan(config, snapshot)
    validate_plan_is_read_only(plan)
    if args.format == "json":
        print(json.dumps(plan, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        print(render_markdown(plan))
    return 0


if __name__ == "__main__":
    sys.exit(main())
