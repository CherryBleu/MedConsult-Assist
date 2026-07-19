# 数据清理 dry-run 工具

本目录只提供 dry-run 方案生成和离线候选识别，不连接外部服务，不执行真实删除。真正清理必须在未来单独确认目标环境、停写窗口、备份路径、白名单、候选 ID 和回滚步骤。

## 文件

- `data_cleanup_dry_run.py`：生成 dry-run 计划、只读盘点命令、离线快照候选摘要、默认白名单校验和安全自检。
- `default-whitelist.json`：显式保护白名单，覆盖 `DataSeeder` 中的 `admin`、`doctor`、`patient`、`yaofang` 四类账号。脚本会拒绝缺失这些内置 seed 身份的配置。
- `test_data_cleanup_dry_run.py`：`unittest` 自检，验证默认无执行模式、危险操作被拦截、快照候选不包含白名单账号。

## 基本用法

```powershell
python scripts/data-cleanup/data_cleanup_dry_run.py --dry-run
python scripts/data-cleanup/data_cleanup_dry_run.py --self-check
python -m unittest scripts/data-cleanup/test_data_cleanup_dry_run.py
```

扩展白名单：

```powershell
python scripts/data-cleanup/data_cleanup_dry_run.py --dry-run --allow-account demo_admin --allow-user-id 10001
```

`--allow-*` 只能追加保护身份，不能替代内置 seed 白名单。若自定义 `--config` 漏掉 `admin`、`doctor`、`patient` 或 `yaofang`，脚本会在生成 dry-run 前失败。

使用离线快照识别候选：

```powershell
python scripts/data-cleanup/data_cleanup_dry_run.py --dry-run --snapshot scripts/data-cleanup/example-snapshot.json
```

快照格式示例：

```json
{
  "mysql": {
    "medconsult_auth.sys_user": [
      {
        "id": 99,
        "account": "test_patient_legacy",
        "phone": "18800009999",
        "name": "测试患者",
        "role": "PATIENT",
        "patient_id": null,
        "doctor_id": null,
        "pharmacist_id": null,
        "deleted": 0
      }
    ],
    "medconsult_auth.login_log": [
      { "id": 500, "user_id": 99, "account": "test_patient_legacy" }
    ]
  }
}
```

## 数据域边界

- 业务 MySQL：账号、患者、预约、退款、病历、通知、AI 调用日志等业务数据，只做备份、计数和候选查询。
- Redis：只扫描 `medconsult:*` 和候选 userId 关联 key，默认不生成删键命令。
- Mongo：医疗知识库文档域，只盘点，不因测试用户清理而处理。
- Milvus：医疗知识库向量域，只盘点 collection/row count，不清理向量。
- Milvus 内部 MinIO/etcd：基础设施内部数据，不纳入应用数据清理。
- 应用影像 MinIO：用户上传影像/附件对象，必须与 Milvus 内部 MinIO 分桶、分凭据、分 runbook 处理。

## dry-run 输出摘要

默认输出包含：

- 备份步骤：MySQL `mysqldump`、Redis key 清单、Mongo 知识库备份、Milvus/MinIO inventory。
- 停写步骤：真实清理前暂停公网写入、后台导入、MQ dispatch、上传 worker。
- 白名单校验：确认 `admin`、`doctor`、`patient`、`yaofang` 四类内置账号仍在保护清单中。
- 计数步骤：清理前业务表和缓存 key 计数。
- 候选识别：账号/患者只读查询，以及离线快照中命中的候选。
- 验证步骤：未来真实清理后必须复查默认白名单账号仍存在，并按域复核业务数据、知识库和对象存储边界。

安全自检会输出类似：

```text
PASS: dry-run plan contains 20 read-only operations; destructive-operation guard rejected sample DELETE.
```
