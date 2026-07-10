# MedConsult Assist Backend AI

MedConsult Assist Backend AI 是智慧医疗问诊辅助系统的后端 AI 智能域工程。当前仓库以 Maven 多模块方式组织，重点实现 AI 服务、公共支撑模块、网关、疾病知识导入工具和本地实验 Demo。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `common` | 公共 Starter，提供统一响应、异常、traceId、Redis 限流、SSE Pub/Sub 等能力 |
| `ai` | AI 智能域服务，包含症状自诊、智能分诊、病历摘要、用药分析、报告文本分析、影像图像检测、文件上传、反馈和调用日志 |
| `gateway` | Spring Cloud Gateway，只路由 `/api/v1/**`，不暴露 `/internal/**` |
| `data` | 疾病知识数据导入 Milvus 的工具模块 |
| `demo` | MongoDB、Redis、疾病匹配相关本地实验代码 |
| `docs` | 架构、AI 智能域实现说明等文档 |

## 技术栈

- JDK 21
- Spring Boot 3.3.7
- Spring Cloud 2023.0.4
- Spring Cloud Alibaba 2022.0.0.0
- MyBatis-Plus
- MySQL 8.0
- Redis
- RabbitMQ
- MongoDB
- Milvus
- MinIO
- LangChain4j OpenAI-compatible client

## 本地依赖

AI 服务完整运行建议准备：

- MySQL：默认库名 `medconsult`
- Redis：默认 `localhost:6379`，database `2`
- RabbitMQ：本地配置默认端口 `5773`
- MongoDB：默认 `mongodb://localhost:27017`
- Milvus：默认 `http://localhost:19530`
- MinIO：默认 `http://localhost:9000`，账号密码 `minioadmin/minioadmin`
- OpenAI-compatible LLM / Embedding / Vision 服务

常用配置位于：

```text
ai/src/main/resources/application.yml
```

## 数据库初始化

新库执行：

```powershell
mysql -uroot -p123456 medconsult < ai\src\main\resources\db\schema-ai.sql
```

已有库升级执行：

```powershell
mysql -uroot -p123456 medconsult < ai\src\main\resources\db\upgrade-ai-architecture-20260710.sql
```

核心 AI 表包括：

- `ai_chat_session`
- `ai_chat_message`
- `ai_triage_result`
- `ai_medical_summary`
- `ai_medication_analysis`
- `ai_report_text_analysis`
- `ai_image_detection`
- `ai_file_upload`
- `ai_feedback`
- `ai_call_log`
- `symptom_rule`
- `high_risk_symptom_rule`
- `negative_rule`

## 构建与测试

在仓库根目录执行：

```powershell
mvn -pl ai -am test
```

说明：

- `-pl ai` 表示构建 AI 模块。
- `-am` 表示同时构建 AI 依赖的本仓库模块，例如 `common`。
- 不建议直接在 `ai` 目录执行 `mvn test`，除非你已经先安装 `common`。

如需先安装公共模块：

```powershell
mvn -pl common install
```

## 启动服务

启动 AI 服务：

```powershell
mvn -pl ai -am spring-boot:run
```

启动 Gateway：

```powershell
mvn -pl gateway -am spring-boot:run
```

默认地址：

```text
AI Service: http://localhost:8082
Gateway:    http://localhost:8080
```

父工程和 `common` 默认跳过 `spring-boot:run`，只有 `ai`、`gateway` 这类应用模块会真正启动。

## AI 服务接口边界

外部接口：

```text
/api/v1/**
```

内部服务接口：

```text
/internal/**
```

内部接口需要携带：

```http
Authorization: Bearer ${AI_INTERNAL_SERVICE_TOKEN}
X-Caller-Service: medical-record-service
X-Trace-Id: trace-xxx
X-Trigger-User-Id: 123
```

本地默认：

```text
AI_INTERNAL_SERVICE_TOKEN=dev-ai-service-token
```

## 影像检测流程

图像检测需要先上传文件到 MinIO，再把返回的 `fileUrl` 传给影像检测接口。

### 1. 上传文件

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data
```

form-data：

| 字段 | 说明 |
| --- | --- |
| `file` | DICOM 或图片文件 |
| `patientId` | 可选 |
| `recordId` | 可选 |

返回示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "FILE20260710XXXXXXXXXXXX",
    "fileUrl": "http://localhost:9000/medconsult/imaging/2026/07/10/xxx.dcm",
    "fileSize": 1048576,
    "fileType": "application/dicom",
    "storageType": "MINIO",
    "bucket": "medconsult",
    "objectKey": "imaging/2026/07/10/xxx.dcm",
    "originalFilename": "ct-001.dcm"
  }
}
```

### 2. 提交影像检测

```http
POST /api/v1/ai/image-detection
Content-Type: application/json
```

```json
{
  "patientId": "P202607060001",
  "recordId": "MR202607060001",
  "imageType": "CHEST_CT",
  "storageType": "MINIO",
  "imageUrls": [
    "http://localhost:9000/medconsult/imaging/2026/07/10/xxx.dcm"
  ]
}
```

### 3. 查询检测结果

```http
GET /api/v1/ai/image-detection/{detectionId}
```

图像检测是异步任务，提交后通常先返回 `PENDING`，消费者处理后变为 `COMPLETED` 或 `FAILED`。

## 分片上传

大 DICOM 文件可使用：

```http
POST /api/v1/files/upload/chunk
Content-Type: multipart/form-data
```

form-data：

| 字段 | 说明 |
| --- | --- |
| `file` | 当前分片 |
| `uploadId` | 首片可不传，后续使用首片返回值 |
| `chunkIndex` | 从 `0` 开始 |
| `totalChunks` | 总分片数 |
| `filename` | 原始文件名 |
| `patientId` | 可选 |
| `recordId` | 可选 |

最后一片上传后会自动合并，并返回 `completed=true` 以及 `file.fileUrl`。

## Postman

Postman 集合位于：

```text
ai/postman/MedConsult-AI.postman_collection.json
ai/postman/MedConsult-AI.postman_environment.json
```

使用说明见：

```text
ai/postman/README.md
```

推荐测试顺序：

1. `POST /api/v1/ai/medical-record-summary/text`
2. `POST /api/v1/ai/medication-analysis`
3. `POST /api/v1/ai/report-analysis`
4. `POST /api/v1/files/upload`
5. `POST /api/v1/ai/image-detection`
6. `GET /api/v1/ai/image-detection/{detectionId}`
7. `POST /api/v1/ai/feedback`
8. `GET /api/v1/ai/call-logs`

## 常见问题

### 找不到 `medconsult-common-starter`

如果在 `ai` 目录直接运行 Maven，可能出现：

```text
Could not find artifact com.medconsult:medconsult-common-starter
```

请在根目录运行：

```powershell
mvn -pl ai -am test
```

或先安装 common：

```powershell
mvn -pl common install
```

### 父工程找不到 main class

启动时请使用：

```powershell
mvn -pl ai -am spring-boot:run
```

不要直接在根目录运行裸的：

```powershell
mvn spring-boot:run
```

### 影像 URL 下载失败

不要使用示例地址：

```text
https://oss.example.com/imaging/ct-001.dcm
```

应先通过 `/api/v1/files/upload` 上传到 MinIO，并使用返回的 `fileUrl`。如果 AI 服务在 Docker 容器内，`MINIO_PUBLIC_ENDPOINT` 需要配置为 AI 服务可访问的 MinIO 地址。

### RabbitMQ 图像任务重复报错

图像 URL 无法访问时任务会记录为 `FAILED`。这属于业务失败，不应无限重试。当前消费者会吞掉已落库的失败任务，避免重复 requeue。

## 参考文档

- [AI 智能域实现说明](docs/AI智能域实现说明.md)
- [AI 模块 README](ai/README.md)
- [Postman 使用说明](ai/postman/README.md)
