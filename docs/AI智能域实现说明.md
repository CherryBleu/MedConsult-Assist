# AI 智能域实现说明

本文档说明当前 `ai` 模块的实现范围、服务边界、接口契约、数据库结构、异步能力、症状自诊链路和联调验收方式。启动与 Postman 使用细节见 [ai/README.md](../ai/README.md) 和 [ai/postman/README.md](../ai/postman/README.md)。

## 1. 模块定位

`ai` 模块负责智慧医疗系统中的 AI 智能域能力，包括：

- 症状自诊问答
- 智能分诊推荐
- 病历摘要
- 用药提醒与禁忌分析
- 报告文本分析
- 影像图像检测
- AI 结果反馈
- AI 调用日志

AI 域只维护 `medconsult_ai` 相关表。其他业务域数据由对应服务维护，AI 需要读取时通过内部 Feign 接口调用，不跨库查询。
其中病历 ID 摘要通过 medical-record-service 的 `GET /internal/medical-records/{id}/full` 获取病历全文，AI 域不保留 `medical_record` 表实体或 Mapper。

## 2. 内外接口边界

当前实现按架构文档拆分为两类入口。

| 类型 | 路径前缀 | 调用方 | 鉴权方式 |
| --- | --- | --- | --- |
| 外部接口 | `/api/v1/*` | 前端、Gateway | 用户 JWT，由 Gateway 或后续安全模块处理 |
| 内部接口 | `/internal/*` | 其他后端服务 Feign 调用 | Bearer 服务 Token，兼容 `X-Service-Api-Key` |

Gateway 只应路由 `/api/v1/**`。`/internal/**` 不应通过 Gateway 对外暴露。

内部调用优先携带：

```http
Authorization: Bearer ${AI_INTERNAL_SERVICE_TOKEN}
X-Caller-Service: medical-record-service
X-Trace-Id: trace-xxx
X-Trigger-User-Id: 123
```

其中 `X-Trigger-User-Id` 可选，但用户触发链路建议传入，便于调用日志追踪。

## 3. 已实现接口

### 3.1 外部接口

| 功能 | 接口 |
| --- | --- |
| 症状自诊问答 | `POST /api/v1/ai/symptom-chat` |
| 智能分诊推荐 | `POST /api/v1/ai/triage` |
| 智能分诊流式返回 | `POST /api/v1/ai/triage/stream` |
| 病历摘要 | `POST /api/v1/ai/medical-record-summary` |
| 病历摘要流式返回 | `POST /api/v1/ai/medical-record-summary/stream` |
| 文本病历摘要 | `POST /api/v1/ai/medical-record-summary/text` |
| 医生确认摘要 | `POST /api/v1/medical-records/{recordId}/summary/confirm` |
| 用药分析 | `POST /api/v1/ai/medication-analysis` |
| 用药分析流式返回 | `POST /api/v1/ai/medication-analysis/stream` |
| 报告文本分析 | `POST /api/v1/ai/report-analysis` |
| 报告文本分析复核 | `POST /api/v1/ai/report-analysis/{analysisId}/review` |
| 影像图像检测提交 | `POST /api/v1/ai/image-detection` |
| 查询影像图像检测 | `GET /api/v1/ai/image-detection/{detectionId}` |
| 影像图像检测复核 | `POST /api/v1/ai/image-detection/{detectionId}/review` |
| 提交 AI 反馈 | `POST /api/v1/ai/feedback` |
| 查询 AI 反馈 | `GET /api/v1/ai/feedback` |
| 查询 AI 调用日志 | `GET /api/v1/ai/call-logs` |

### 3.2 内部接口

| 调用方 | 功能 | 接口 |
| --- | --- | --- |
| medical-record-service | 用药分析 | `POST /internal/ai/medication-analysis` |
| outpatient-service | 分诊推荐 | `POST /internal/ai/triage` |
| outpatient-service | 分诊流式返回 | `POST /internal/ai/triage/stream` |
| medical-record-service | 病历摘要 | `POST /internal/ai/medical-record-summary` |
| medical-record-service | 病历摘要流式返回 | `POST /internal/ai/medical-record-summary/stream` |
| medical-record-service | 报告文本分析 | `POST /internal/ai/report-analysis` |
| medical-record-service | 影像图像检测提交 | `POST /internal/ai/image-detection` |
| medical-record-service | 查询影像图像检测 | `GET /internal/ai/image-detection/{detectionId}` |
| medical-record-service | 用药分析流式返回 | `POST /internal/ai/medication-analysis/stream` |

## 4. 统一响应

普通 JSON 接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "trace-xxx",
  "timestamp": "2026-07-10T10:30:00+08:00"
}
```

流式接口使用 `text/event-stream`，事件包括：

- `start`
- `delta`
- `result`
- `done`
- `error`

其中病历摘要和用药分析的 `delta` 为模型 token 增量；分诊的 `delta` 为逐条推荐结果。

## 5. 症状自诊链路

症状自诊接口保持同步接口，不改为 SSE：

```text
POST /api/v1/ai/symptom-chat
```

当前链路为：

```text
用户输入
  -> 意图抽取
  -> 编码异常保护与口语化症状扩展
  -> Redis 缓存
  -> MongoDB name 精确匹配
  -> Milvus 语义检索兜底
  -> 疾病 JSON 命中结果去重
  -> 高危症状规则评估
  -> 将命中知识、引用、患者上下文、风险规则交给 gpt-5.5 生成最终回答
  -> 写入会话、消息、调用日志
```

关键约束：

- Redis、MongoDB、Milvus 的 RAG 前置逻辑保持不变。
- 最终自然语言结果由配置中的 LLM 模型生成，当前本地配置为 `gpt-5.5`。
- 回答必须提示“非诊断，仅供参考，不能替代医生诊断”或同义表达。
- 命中高危症状时优先给出急诊建议。

关键类：

| 类 | 责任 |
| --- | --- |
| `DiseaseSearchService` | 意图抽取、缓存、MongoDB、Milvus 检索编排 |
| `DiseaseCacheService` | Redis 标准化结果缓存 |
| `MongoDiseaseRepository` | MongoDB `name` 精确匹配 |
| `MilvusRestClient` | Milvus REST 向量检索 |
| `QueryExpander` | 口语化症状扩展 |
| `EncodingComplaintGuard` | 编码异常纠偏 |
| `RiskRuleEngine` | 高危症状规则 |
| `SymptomChatService` | 自诊问答总编排与最终 LLM 回答 |

## 6. 用药分析链路

用药分析用于处方审核、药师工作台等场景。

架构约束：

- medical-record-service 调用 AI 时应内联传入处方和患者上下文。
- AI 不应拿 `prescriptionId` 反向查询处方，避免循环依赖。
- AI 可只读调用 patient-service 和 drug-service 的内部接口补充风险信息。

当前实现：

```text
处方 + 患者上下文
  -> MedicationFunctionService 受控函数分析
  -> 可选只读 Feign 查询药品风险信息
  -> LLM 基于受控函数结果生成风险说明
  -> 写入 ai_medication_analysis
  -> 异步写入 ai_call_log
```

已定义的只读 Feign 契约：

| 服务 | 接口 |
| --- | --- |
| patient-service | `GET /internal/patients/{id}/context` |
| patient-service | `GET /internal/patients/{id}/allergies` |
| drug-service | `GET /internal/drugs/{id}/risk-info` |
| medical-record-service | `GET /internal/medical-records/{id}/full` |

## 7. 影像能力拆分

影像能力已拆分为文本报告分析和图像检测。

| 能力 | 接口 | 表 | 行为 |
| --- | --- | --- | --- |
| 报告文本分析 | `POST /api/v1/ai/report-analysis` | `ai_report_text_analysis` | 同步返回 `COMPLETED` |
| 影像图像检测 | `POST /api/v1/ai/image-detection` | `ai_image_detection` | 提交后返回 `PENDING` |

图像检测异步链路：

```text
POST /api/v1/ai/image-detection
  -> 写入 ai_image_detection，status=PENDING
  -> 投递 RabbitMQ: ai.image.detect.queue
  -> 消费者处理，status=PROCESSING
  -> 拉取 imageUrls 指向的 DICOM/图片文件
  -> 调用医学视觉模型（OpenAI-compatible 多模态接口）
  -> 视觉模型不可用时本地保守兜底
  -> 更新 status=COMPLETED/FAILED
  -> 调用方 GET 轮询结果
```

## 8. RabbitMQ

当前 AI 域使用 RabbitMQ 承接：

| Exchange | Queue | 用途 |
| --- | --- | --- |
| `ai.exchange` | `ai.image.detect.queue` | 影像图像检测异步任务 |
| `ai.exchange` | `ai.calllog.queue` | AI 调用日志异步落库 |

如果 RabbitMQ 暂不可用：

- 图像检测提交仍会写入 `PENDING` 记录。
- 调用日志会同步兜底写库。
- 日志中会记录队列投递失败信息。

## 9. 数据库结构

完整建表脚本：

```text
ai/src/main/resources/db/schema-ai.sql
```

已有库升级脚本：

```text
ai/src/main/resources/db/upgrade-ai-architecture-20260710.sql
```

AI 域核心表：

- `ai_chat_session`
- `ai_chat_message`
- `ai_triage_result`
- `ai_medical_summary`
- `ai_medication_analysis`
- `ai_report_text_analysis`
- `ai_image_detection`
- `ai_feedback`
- `ai_call_log`
- `symptom_rule`
- `high_risk_symptom_rule`
- `negative_rule`

说明：

- `symptom_rule`、`high_risk_symptom_rule`、`negative_rule` 已建表。
- 当前高危规则业务仍由 Java 本地 `RiskRuleEngine` 执行。
- 规则表可作为后续运营配置化入口。

## 10. 配置项

本地默认配置位于：

```text
ai/src/main/resources/application.yml
```

关键环境变量：

| 变量 | 用途 |
| --- | --- |
| `OPENAI_BASE_URL` | OpenAI-compatible LLM 地址 |
| `OPENAI_API_KEY` | LLM API Key |
| `OPENAI_MODEL` | LLM 模型，默认可配置为 `gpt-5.5` |
| `VISION_BASE_URL` | 医学视觉模型 OpenAI-compatible 地址 |
| `VISION_API_KEY` | 医学视觉模型 API Key |
| `VISION_MODEL` | 医学视觉模型名称 |
| `VISION_MAX_BYTES_PER_IMAGE` | 单张影像下载大小上限 |
| `EMBEDDING_BASE_URL` | Embedding 服务地址 |
| `EMBEDDING_API_KEY` | Embedding API Key |
| `EMBEDDING_MODEL` | Embedding 模型 |
| `AI_INTERNAL_SERVICE_TOKEN` | 内部服务调用 AI 的 Bearer 服务 Token |
| `AI_INTERNAL_API_KEY` | 旧内部服务 API Key，仅作兼容 |
| `RABBITMQ_HOST` | RabbitMQ 地址 |
| `MONGODB_URI` | MongoDB 地址 |
| `MILVUS_URI` | Milvus 地址 |

## 11. 联调要求

AI 域接入其他业务前，需要确认：

1. Gateway 只转发 `/api/v1/**`。
2. 其他服务 Feign 调用 AI 的 `/internal/ai/*`。
3. 其他服务请求头携带 `Authorization: Bearer ${AI_INTERNAL_SERVICE_TOKEN}`、`X-Caller-Service`、`X-Trace-Id`。
4. patient-service 提供患者上下文内部接口。
5. drug-service 提供药品风险内部接口。
6. medical-record-service 提供病历全文内部接口。
7. RabbitMQ 可用，或接受异步任务降级行为。
8. MySQL 已执行 `schema-ai.sql` 或升级脚本。
9. MongoDB 已导入疾病 JSON。
10. Milvus 已导入疾病向量索引。

## 12. 验证

已执行：

```powershell
mvn -pl ai -am clean compile -DskipTests
mvn -pl ai -am test
```

测试结果：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

测试覆盖：

- 口语化“鸡叫样咳嗽”扩展后更倾向百日咳相关匹配。
- 高危症状规则命中后返回急诊建议。

## 13. 当前对接结论

按架构设计文档的逻辑拓扑，AI 智能域当前已经具备与其他业务域联调的基础条件：

- 对前端和 Gateway 暴露 `/api/v1/*`。
- 对其他后端服务暴露 `/internal/ai/*`。
- 内部接口通过 Bearer 服务 Token 做服务级保护，并兼容旧 API Key。
- 调用日志可以记录调用方服务、触发用户、traceId、requestId 和 token 成本。
- 病历摘要、用药分析、报告文本分析、影像图像检测和分诊均有可调用契约。
- 影像图像检测已按异步任务模式提交并返回 `PENDING`。
- 症状自诊保持原 Redis + MongoDB + Milvus RAG 链路，不改接口。

其他业务域接入时重点确认三件事：

1. Gateway 不暴露 `/internal/**`。
2. 调用方统一传入内部鉴权和链路追踪请求头。
3. patient-service、drug-service、medical-record-service 提供 AI 域只读 Feign 依赖接口。
