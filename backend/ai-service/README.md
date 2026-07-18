# MedConsult AI 智能域服务

本模块是 MedConsult Assist 后端中的 AI 智能域，负责症状自诊、智能分诊、病历摘要、用药分析、报告文本分析、影像图像检测、AI 反馈和调用日志。当前实现目标是让 AI 域可以独立运行，并通过内部接口与患者、药品、病历、门诊等业务域对接。

更完整的实现边界和联调说明见 [AI智能域实现说明.md](../docs/AI智能域实现说明.md)。Postman 使用说明见 [postman/README.md](postman/README.md)。

## 1. 服务边界

AI 域只维护 `medconsult_ai` 相关表，不直接修改其他业务域数据。

对外接口使用：

```text
/api/v1/**
```

服务间内部接口使用：

```text
/internal/ai/**
```

内部接口优先使用服务 Bearer Token，并通过 auth-service 的 `/internal/auth/service-verify` 校验 `sys_service_account.scope`；`X-Service-Api-Key` 仅作为旧调用方兼容：

```http
Authorization: Bearer ${AI_INTERNAL_SERVICE_TOKEN}
X-Caller-Service: medical-record-service
X-Trace-Id: trace-xxx
X-Trigger-User-Id: 123
```

其中 `X-Trigger-User-Id` 可选，建议由调用方在用户触发的链路中传入。

## 2. 已实现能力

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 症状自诊 | 已实现 | 保持 Redis + MongoDB + Milvus 的 RAG 链路，最终回答由配置模型生成 |
| 智能分诊 | 已实现 | 支持普通 JSON 和 SSE 流式接口 |
| 病历摘要 | 已实现 | 支持病历 ID 摘要、纯文本摘要、医生确认；病历 ID 摘要通过 medical-record-service 内部接口读取全文 |
| 用药分析 | 已实现 | 先走受控函数分析，再由大模型生成说明 |
| 报告文本分析 | 已实现 | 独立表 `ai_report_text_analysis` |
| 影像图像检测 | 已实现 | 独立表 `ai_image_detection`，提交后返回 `PENDING` |
| 影像文件上传 | 已实现 | 上传到 MinIO，记录 `ai_file_upload`，返回可用于图像检测的 `fileUrl` |
| AI 反馈 | 已实现 | 支持结果反馈与查询 |
| AI 调用日志 | 已实现 | 支持 trace、调用方、用户、requestId、缓存命中、Token 拆分和预估成本字段 |
| 内部接口 | 已实现 | `/internal/ai/*`，优先通过 Bearer 服务 Token 保护，兼容 `X-Service-Api-Key` |
| RabbitMQ | 已接入 | 用于图像检测任务和 AI 调用日志异步处理 |
| Redis 限流 | 已接入 | 对 `/api/v1/ai/*` 使用滑动窗口限流，可按接口覆盖阈值 |

## 3. 症状自诊说明

症状自诊逻辑保持不变，仍是：

```text
用户输入
  -> 意图抽取
  -> 编码异常保护和口语化症状扩展
  -> Redis 缓存
  -> MongoDB 精确匹配
  -> Milvus 语义检索兜底
  -> 高危规则评估
  -> gpt-5.5 或配置模型生成最终回答
  -> 保存会话、消息和调用日志
```

当前没有把症状自诊接口改成 SSE。保留接口：

```text
POST /api/v1/ai/symptom-chat
```

## 4. 影像能力拆分

架构文档中的影像能力已拆分为两个新接口：

| 能力 | 接口 | 表 | 返回方式 |
| --- | --- | --- | --- |
| 报告文本分析 | `POST /api/v1/ai/report-analysis` | `ai_report_text_analysis` | 同步返回 `COMPLETED` |
| 影像图像检测 | `POST /api/v1/ai/image-detection` | `ai_image_detection` | 提交返回 `PENDING`，后续查询 |

影像图像检测前应先上传文件：

```text
POST /api/v1/files/upload
POST /api/v1/files/upload/chunk
```

上传接口会把文件写入 MinIO，并在 `ai_file_upload` 记录元数据。返回的 `fileUrl` 可直接放入 `imageUrls` 调用 `POST /api/v1/ai/image-detection`。

## 5. 流式接口

以下能力提供 SSE 流式接口：

```text
POST /api/v1/ai/triage/stream
POST /api/v1/ai/medical-record-summary/stream
POST /api/v1/ai/medication-analysis/stream
POST /internal/ai/triage/stream
POST /internal/ai/medical-record-summary/stream
POST /internal/ai/medication-analysis/stream
```

事件类型：

```text
start
delta
result
done
error
```

SSE 事件通过 Redis Pub/Sub 广播到 `medical:pubsub:sse:{userId}`（前缀随 `REDIS_KEY_PREFIX` 改变）。多实例部署时，请求所在实例负责执行任务，持有连接的实例收到对应用户频道后本地推送。

## 6. 数据库初始化

新库直接执行：

```powershell
mysql -uroot -p123456 medconsult < src\main\resources\db\schema-ai.sql
```

已有库升级执行：

```powershell
mysql -uroot -p123456 medconsult < src\main\resources\db\upgrade-ai-architecture-20260710.sql
mysql -uroot -p123456 medconsult < src\main\resources\db\upgrade-ai-call-log-observability-20260718.sql
```

核心新增和调整包括：

- `symptom_rule`
- `high_risk_symptom_rule`
- `negative_rule`
- `ai_chat_session.last_risk_level`
- `ai_chat_session.context_symptoms`
- `ai_chat_message.query_embedding_model`
- `ai_chat_message.rule_version`
- `ai_report_text_analysis`
- `ai_image_detection`
- `ai_file_upload`
- `ai_call_log.caller_service`
- `ai_call_log.trigger_user_id`
- `ai_call_log.trace_id`
- `ai_call_log.cost_tokens`
- `ai_call_log.request_id`
- `ai_call_log.cache_hit`
- `ai_call_log.prompt_tokens`
- `ai_call_log.completion_tokens`
- `ai_call_log.total_tokens`
- `ai_call_log.estimated_cost_yuan`

## 7. 本地启动

进入 `ai` 目录：

```powershell
cd ai
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8082
```

本地依赖：

- MySQL
- Redis
- RabbitMQ
- MongoDB
- Milvus
- OpenAI-compatible LLM 服务

## 8. 关键配置

配置文件：

```text
src/main/resources/application.yml
```

常用环境变量：

| 变量 | 说明 |
| --- | --- |
| `OPENAI_BASE_URL` | 大模型服务地址 |
| `OPENAI_API_KEY` | 大模型 API Key |
| `OPENAI_MODEL` | 大模型名称，当前可配置为 `gpt-5.5` |
| `VISION_BASE_URL` | 医学视觉模型 OpenAI-compatible 地址 |
| `VISION_API_KEY` | 医学视觉模型 API Key |
| `VISION_MODEL` | 医学视觉模型名称 |
| `VISION_MAX_BYTES_PER_IMAGE` | 单张影像下载大小上限 |
| `MINIO_ENDPOINT` | AI 服务访问 MinIO 的地址，默认 `http://localhost:9000` |
| `MINIO_PUBLIC_ENDPOINT` | 返回给调用方、并供 AI 下载的文件 URL 前缀 |
| `MINIO_ACCESS_KEY` | MinIO access key，本地默认 `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key，本地默认 `minioadmin` |
| `MINIO_BUCKET` | MinIO bucket，本地默认 `medconsult` |
| `EMBEDDING_BASE_URL` | Embedding 服务地址 |
| `EMBEDDING_API_KEY` | Embedding API Key |
| `EMBEDDING_MODEL` | Embedding 模型 |
| `AI_INTERNAL_SERVICE_TOKEN` | 内部服务调用 AI 的 Bearer 服务 Token |
| `AI_INTERNAL_API_KEY` | 旧内部服务 API Key，仅作兼容 |
| `AI_AUTH_SERVICE_VERIFY_ENABLED` | 是否调用 auth-service 校验服务账号，prod 默认 `true` |
| `AI_LEGACY_API_KEY_ENABLED` | 是否允许 `X-Service-Api-Key` 兼容鉴权，prod 默认 `false` |
| `AI_RATE_LIMIT_ENABLED` | 是否启用 AI 接口 Redis 限流 |
| `AI_RATE_LIMIT_MAX_REQUESTS` | 默认滑动窗口请求数 |
| `AI_RATE_LIMIT_WINDOW_SECONDS` | 默认滑动窗口秒数 |
| `AI_RATE_LIMIT_INCLUDE_INTERNAL` | 是否同时限制 `/internal/ai/*` |
| `RABBITMQ_HOST` | RabbitMQ 地址 |
| `MONGODB_URI` | MongoDB 地址 |
| `MILVUS_URI` | Milvus 地址 |
| `MILVUS_METRIC_TYPE` | Milvus 向量索引 metric，默认 `COSINE`；可选 `COSINE`/`IP`/`L2`/`EUCLIDEAN`，必须与数据导入器建索引时一致 |
| `MILVUS_SEARCH_TIMEOUT_SECONDS` | Milvus 检索超时秒数，默认 `15` |

> `EUCLIDEAN` 会被规范为 Milvus 原生 `L2`。切换 `MILVUS_METRIC_TYPE` 后，已有 collection 的索引 metric 不会自动改变，必须删除并用 `backend/data` 导入器按同一 metric 重新导入。

## 9. 与其他业务域对接

其他业务域需要完成：

- Gateway 只暴露 `/api/v1/**`，不要暴露 `/internal/**`。
- medical-record-service 调用病历摘要、报告文本分析、影像图像检测、用药分析内部接口。
- outpatient-service 调用智能分诊内部接口。
- patient-service 提供患者上下文、过敏史等只读内部接口。
- drug-service 提供药品风险信息只读内部接口。
- medical-record-service 提供病历全文只读内部接口，AI 域不直接读取病历表。
- 调用方统一传入 `Authorization: Bearer ${AI_INTERNAL_SERVICE_TOKEN}`、`X-Caller-Service`、`X-Trace-Id`。
- 异步图像检测外部调用方通过 `GET /api/v1/ai/image-detection/{detectionId}` 轮询结果。
- 异步图像检测内部调用方通过 `GET /internal/ai/image-detection/{detectionId}` 轮询结果。

## 10. 验证命令

```powershell
mvn -pl ai -am clean compile -DskipTests
mvn -pl ai -am test
```

当前测试重点：

- 口语化症状扩展后可提升 RAG 命中质量。
- 高危症状规则命中后返回急诊建议。
