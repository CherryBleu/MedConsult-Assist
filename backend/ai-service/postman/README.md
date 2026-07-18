# MedConsult AI Postman 集合

本目录用于测试 AI 智能域接口，包括对外 `/api/v1/*` 接口、服务间 `/internal/ai/*` 接口、AI 反馈和调用日志接口。

## 1. 文件说明

| 文件 | 说明 |
| --- | --- |
| `MedConsult-AI.postman_collection.json` | AI 智能域接口集合 |
| `MedConsult-AI.postman_environment.json` | 本地环境变量，例如 `baseUrl`、`patientId`、`recordId`、`prescriptionId` 和自动保存的结果 ID |

## 2. 测试前准备

启动本地依赖：

- MySQL
- Redis
- RabbitMQ
- MongoDB
- Milvus
- MinIO
- OpenAI-compatible LLM 服务

新库初始化：

```powershell
mysql -uroot -p123456 medconsult < ..\src\main\resources\db\schema-ai.sql
```

已有库升级：

```powershell
mysql -uroot -p123456 medconsult < ..\src\main\resources\db\upgrade-ai-architecture-20260710.sql
```

从 `ai` 目录启动服务：

```powershell
mvn spring-boot:run
```

默认服务地址：

```text
http://localhost:8082
```

对外接口路径为：

```text
{{baseUrl}}/api/v1/...
```

内部服务接口路径为：

```text
{{baseUrl}}/internal/...
```

内部接口必须携带：

```http
Authorization: Bearer {{internalServiceToken}}
X-Caller-Service: postman
X-Trace-Id: {{traceId}}
```

## 3. 导入方式

1. 打开 Postman。
2. 导入 `MedConsult-AI.postman_collection.json`。
3. 导入 `MedConsult-AI.postman_environment.json`。
4. 选择 `MedConsult AI Local` 环境。
5. 保持 `baseUrl=http://localhost:8082` 即可直连 AI 服务测试。
6. `accessToken` 可暂时为空，等网关和登录鉴权接入后再配置。

## 4. 推荐冒烟流程

不依赖 `medical_record` 表数据的最快流程：

1. `POST /api/v1/ai/medical-record-summary/text`
2. `POST /api/v1/ai/medication-analysis`
3. `POST /api/v1/ai/report-analysis`
4. `POST /api/v1/ai/imaging-detection`
5. `GET /api/v1/ai/imaging-detection/{detectionId}`
6. `POST /api/v1/ai/feedback`
7. `GET /api/v1/ai/call-logs`

依赖 RAG 数据的流程：

1. `POST /api/v1/ai/symptom-chat`
2. `POST /api/v1/ai/triage`

依赖 medical-record-service 内部接口的流程：

1. 确认 medical-record-service 提供 `GET /internal/medical-records/{id}/full`。
2. `POST /api/v1/ai/medical-record-summary`
3. `POST /api/v1/medical-records/{recordId}/summary/confirm`

## 5. 影像拆分接口

架构拆分后的接口已经体现在集合中：

| 接口 | 表 | 行为 |
| --- | --- | --- |
| `POST /api/v1/ai/report-analysis` | `ai_report_text_analysis` | 报告文本分析，同步返回 `COMPLETED` |
| `POST /api/v1/ai/imaging-detection` | `ai_image_detection` | 图像检测任务提交，返回 `PENDING` |
| `GET /api/v1/ai/imaging-detection/{detectionId}` | `ai_image_detection` | 查询图像检测结果 |

图像检测不要直接使用示例 URL。推荐流程：

1. `POST /api/v1/files/upload`，Body 选择 `form-data`，字段 `file` 类型为 File，可选 `patientId`、`recordId`。
2. 从返回结果复制 `data.fileId`。
3. 调用 `POST /api/v1/ai/imaging-detection`，把 `fileId` 放入 `fileIds`。
4. 调用 `GET /api/v1/ai/imaging-detection/{detectionId}` 轮询结果。

分片上传用于较大的 DICOM：

```text
POST /api/v1/files/upload/chunk
```

`form-data` 字段包括：

| 字段 | 说明 |
| --- | --- |
| `file` | 当前分片文件 |
| `uploadId` | 首片可不传；后续使用首片返回的 `uploadId` |
| `chunkIndex` | 从 0 开始 |
| `totalChunks` | 总分片数 |
| `filename` | 原始文件名 |
| `patientId` | 可选 |
| `recordId` | 可选 |

最后一个分片上传成功并完成合并后，返回 `completed=true` 和 `file.fileUrl`。

## 6. SSE 流式接口

集合中包含以下流式接口：

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

症状自诊接口保持普通 JSON 返回：

```text
POST /api/v1/ai/symptom-chat
```

## 7. 自动保存变量

集合会在部分请求成功后自动保存变量：

| 变量 | 来源 |
| --- | --- |
| `summaryId` | `POST /api/v1/ai/medical-record-summary` |
| `analysisId` | `POST /api/v1/ai/medication-analysis` |
| `reportAnalysisId` | `POST /api/v1/ai/report-analysis` |
| `imageDetectionId` | `POST /api/v1/ai/imaging-detection` |
| `feedbackId` | `POST /api/v1/ai/feedback` |
| `aiResultType`、`aiResultId` | 会产生 AI 结果的接口 |

## 8. 内部接口

集合中包含以下服务间调用契约：

```text
POST /internal/ai/medication-analysis
POST /internal/ai/triage
POST /internal/ai/triage/stream
POST /internal/ai/medical-record-summary
POST /internal/ai/medical-record-summary/stream
POST /internal/ai/report-analysis
POST /internal/ai/imaging-detection
GET /internal/ai/imaging-detection/{detectionId}
POST /internal/ai/medication-analysis/stream
```

本地默认 `internalServiceToken` 为：

```text
dev-ai-service-token
```

该值需要和 `application.yml` 中的 `medconsult.ai.internal.service-token` 或环境变量 `AI_INTERNAL_SERVICE_TOKEN` 保持一致。
`internalApiKey` 仍保留在环境中，仅用于旧调用方兼容。

如果内部接口返回：

```json
{
  "code": 401001,
  "message": "invalid internal service credential"
}
```

请检查：

1. Postman 是否选中了 `MedConsult AI Local` 环境。
2. 请求头是否存在 `Authorization: Bearer {{internalServiceToken}}`。
3. `internalServiceToken` 是否等于服务启动时的 `AI_INTERNAL_SERVICE_TOKEN`。
4. 本地默认值是否仍为 `dev-ai-service-token`。
