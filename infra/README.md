# MedConsult-Assist 基础设施（infra/）

本目录用 `docker compose` 统一编排 **MySQL / Redis / Qdrant / RabbitMQ / Milvus / MinIO / Embedding** 等中间件。
**Nacos 不在本 compose 内**（由独立脚本启动），见下文「启动 Nacos」。

## 端口与凭据（本地冒烟用）

| 服务 | 容器内 | 宿主机 | 凭据（.env） |
|---|---|---|---|
| MySQL 8.0 | 3306 | **3307**（避开本机 mysqld.exe 3306） | root/root123456，app 账号 medconsult/medconsult123 |
| Redis 7 | 6379 | **16379**（避开本机 memurai 6379） | 无密码（本地） |
| Qdrant | 6333(REST)/6334(gRPC) | 6333/6334 | 无密码（本地） |
| RabbitMQ | 5672(AMQP)/15672(UI) | 5672/15672 | medconsult/medconsult123 |
| Milvus 2.4 | 19530/9091 | 19530/9091 | root:Milvus |
| MinIO | 9000/9001 | 9000/9001 | minioadmin/minioadmin |
| Embedding | 7997 | 7997 | 无需 api-key（本地 Python 容器） |

> 端口选择原因：宿主机 Windows 已有 `mysqld.exe` 占 3306、`memurai.exe` 占 6379，所以容器映射到非冲突端口。

## 启动 / 停止

```bash
# 1) 首次：复制 .env 模板
cp infra/.env.example infra/.env

# 2) 启动全部三方件（首次会自动跑 mysql/init 建库）
docker compose -f infra/docker-compose.yml up -d

# 3) 查看状态
docker compose -f infra/docker-compose.yml ps

# 4) 停止（保留数据卷）
docker compose -f infra/docker-compose.yml down

# 5) 完全重置（删数据卷，慎用！）
docker compose -f infra/docker-compose.yml down -v
```

## 启动 Nacos（独立）

Nacos 配置中心/注册中心，由独立脚本启动（不入 compose 是既定约定）：

```bash
# Windows
"D:\springcloudalibaba\nacos-server-2.3.0\nacos\bin\startup.cmd" -m standalone

# 验证：http://localhost:8848/nacos  （默认 nacos/nacos）
```

## MySQL 多 Schema 设计（架构文档 §1.2）

按服务边界切分 7 个独立 schema，**禁止跨 schema join / 视图 / 触发器**（架构红线）：

| Schema | 独占服务 | 表 |
|---|---|---|
| `medconsult_auth` | auth-service | sys_user / sys_role / sys_permission / sys_role_permission / sys_user_role / sys_service_account / login_log |
| `medconsult_patient` | patient-service | patient |
| `medconsult_outpatient` | outpatient-service | department / doctor / doctor_schedule / appointment |
| `medconsult_record` | medical-record-service | medical_record / attachment / prescription / prescription_item |
| `medconsult_drug` | drug-service | drug / drug_stock_batch / drug_stock_flow |
| `medconsult_ai` | ai-service | disease_json_knowledge / ai_chat_* / ai_triage_result / ai_medical_summary / ai_medication_analysis / ai_report_text_analysis / ai_image_detection / ai_feedback / ai_call_log / symptom_rule 等 |
| `medconsult_notify` | notification-service | notification / audit_log |

具体业务表的 DDL 由各服务在自己的 `schema.sql` 里维护（服务启动时 `spring.sql.init` 自动执行），
本目录 `mysql/init/` 只负责建空 schema + 授权。

## 各服务连接示例（application.yml）

```yaml
spring:
  datasource:
    # 注意端口 3307（不是默认 3306）
    url: jdbc:mysql://127.0.0.1:3307/medconsult_auth?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: medconsult
    password: medconsult123
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## Embedding 服务（ai-service RAG 依赖）

ai-service 的症状问诊 RAG 链路需要 embedding 向量化用户症状，再从 Milvus 检索匹配疾病。
Embedding 走本地 Python 容器（Flask + sentence-transformers，模型 `BAAI/bge-small-zh-v1.5`，512 维，~95MB），
提供 OpenAI 兼容的 `/v1/embeddings` 接口，**无需额外 API key**。

```bash
# 随 docker compose up -d 自动拉起（首次会构建镜像 + 下载模型）
docker compose -f infra/docker-compose.yml up -d embedding

# 健康检查
curl -s http://localhost:7997/health
# 期望: {"status":"ok","model":"BAAI/bge-small-zh-v1.5","loaded":true,"dimension":512}

# 手动测试 embedding
curl -s -X POST http://localhost:7997/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"BAAI/bge-small-zh-v1.5","input":["感冒发烧"]}' | python -m json.tool
```

> **首次构建约 5min**（下载 torch CPU wheel + sentence-transformers）。
> **首次启动约 30s**（从 hf-mirror.com 下载模型文件，缓存在命名卷 `embedding_model_cache` 内，后续重启秒加载）。
> 模型下载走 `HF_ENDPOINT=https://hf-mirror.com` 国内镜像，如海外网络可改为 `https://huggingface.co`。

## 红线合规

- **红线 1**：MySQL 8.0 / Redis / RabbitMQ / Qdrant 版本严格锁定（见根 pom.xml），不得擅自升级。
- **红线 2/3**：本目录的配置不含业务机密（密码是本地冒烟默认值），但 `.env` 不入仓。
