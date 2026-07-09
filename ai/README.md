# MedConsult AI Service

`ai` 模块是智慧医疗系统的 AI 智能域后端服务。当前实现基于 JDK 21、Spring Boot 3.x、Spring Cloud Alibaba、Nacos、OpenFeign、MyBatis-Plus、MySQL 8.0、Redis、RabbitMQ、LangChain4j、MongoDB 和 Milvus 构建。

模块接口统一挂载在 `/api/v1` 下，既可以直接访问 AI 服务 `http://localhost:8082/api/v1`，也可以通过新增的 Gateway 模块访问 `http://localhost:8080/api/v1`。本次技术栈改造不改变已有接口路径、请求 DTO、响应 DTO 和业务流程。

说明：症状自诊逻辑按当前代码实现保留，即先完成意图抽取、RAG 检索、风险规则评估，再基于命中的疾病 JSON 知识生成最终自然语言回答。

## Postman 测试集合

已提供可直接导入的 Postman 文件：

| 文件 | 说明 |
| --- | --- |
| [postman/MedConsult-AI.postman_collection.json](postman/MedConsult-AI.postman_collection.json) | AI 智能域接口集合，含请求示例、基础断言和变量自动保存脚本 |
| [postman/MedConsult-AI.postman_environment.json](postman/MedConsult-AI.postman_environment.json) | 本地环境变量，默认 `baseUrl=http://localhost:8082` |
| [postman/README.md](postman/README.md) | Postman 导入步骤、接口覆盖表、推荐测试流程和常见问题 |

如果通过 Gateway 联调，将 Postman 环境变量 `baseUrl` 改为 `http://localhost:8080`。

## 模块职责

- 症状自诊问答：`POST /ai/symptom-chat`
- 智能分诊推荐：`POST /ai/triage`
- 病历自动摘要：`POST /ai/medical-record-summary`、`POST /ai/medical-record-summary/text`
- 医生确认摘要：`POST /medical-records/{recordId}/summary/confirm`
- 用药提醒与禁忌分析：`POST /ai/medication-analysis`
- 影像报告异常检测：`POST /ai/imaging-abnormal-detection`
- 影像 AI 结果查询与复核：`GET /ai/imaging-abnormal-detection/{detectionId}`、`POST /ai/imaging-abnormal-detection/{detectionId}/review`
- AI 结果反馈：`POST /ai/feedback`、`GET /ai/feedback`
- AI 调用日志：`GET /ai/call-logs`

## 目录结构

```text
ai
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/medconsult/ai
    │   │   ├── client          # LangChain4j/OpenAI-compatible、MongoDB、Milvus 客户端
    │   │   ├── common          # 统一响应、异常、分页对象
    │   │   ├── config          # 配置属性、MyBatis-Plus 分页插件
    │   │   ├── dto             # 接口请求/响应 DTO
    │   │   ├── knowledge       # 疾病知识检索、口语扩展、风险规则
    │   │   ├── persistence     # MyBatis-Plus 实体和 Mapper
    │   │   ├── service         # AI 业务编排
    │   │   ├── util            # JSON、业务编号工具
    │   │   └── web             # REST Controller
    │   └── resources
    │       ├── application.yml
    │       └── db/schema-ai.sql
    └── test
        └── java/com/medconsult/ai/knowledge
```

## 技术栈

| 层级 | 技术 | 当前使用方式 |
| --- | --- | --- |
| 基础底座 | JDK 21、Spring Boot 3.3.7 | Maven 统一配置 Java 21；`spring.threads.virtual.enabled=true` 启用虚拟线程 |
| 微服务框架 | Spring Cloud Alibaba、Nacos、Spring Cloud Gateway、OpenFeign | AI 服务接入 Nacos 注册与配置、启用 OpenFeign；统一网关在根目录 `gateway` 模块 |
| 持久层 | MyBatis-Plus、MySQL 8.0 | 结构化业务数据、AI 调用结果、聊天记录和调用日志写入 MySQL |
| 缓存与消息 | Redis、RabbitMQ | Redis 用于疾病检索缓存；RabbitMQ 依赖和连接配置已就绪，当前业务逻辑未强行改成异步 |
| AI 与大模型 | LangChain4j | `OpenAiCompatibleClient` 内部使用 LangChain4j `OpenAiChatModel`、`OpenAiEmbeddingModel`，对外保持原方法签名 |
| 向量数据库 | Milvus | 保存疾病 JSON 切分后的文本向量，支撑 RAG 检索 |
| 疾病知识辅助索引 | MongoDB | 当前代码保留 MongoDB `name` 精确匹配，作为 Milvus 语义检索前的快速命中层 |

## 微服务与网关联调

根工程已包含 `gateway` 模块，默认路由：

```yaml
Path=/api/v1/**
uri=lb://medconsult-ai-service
```

本地直连 AI 服务：

```text
http://localhost:8082/api/v1
```

通过 Gateway 访问：

```text
http://localhost:8080/api/v1
```

Nacos 默认地址：

```text
localhost:8848
```

AI 服务和 Gateway 都配置了：

- Nacos Discovery：服务注册与发现
- Nacos Config：配置中心，`spring.config.import=optional:nacos:${spring.application.name}.yaml`
- 虚拟线程：`spring.threads.virtual.enabled=true`

`optional:nacos` 表示本地没有启动 Nacos 时，服务仍可使用本地 `application.yml` 启动。`local` profile 下 Nacos 服务注册默认关闭，生产环境默认开启；如需本地注册到 Nacos，可设置 `NACOS_DISCOVERY_ENABLED=true`。

## 配置项

配置入口在 [application.yml](src/main/resources/application.yml)，按 Spring Profile 分成默认段、`local` 和 `prod`。

| 配置段 | 说明 |
| --- | --- |
| 默认段 | 服务端口、`context-path`、应用名、虚拟线程、Nacos、OpenFeign、MyBatis-Plus 通用配置 |
| `local` | 本地开发默认配置，直接在 yml 中配置 MySQL、Redis、RabbitMQ、MongoDB、Milvus、LLM 等地址 |
| `prod` | 生产/部署配置，从环境变量读取敏感信息和服务地址 |

默认启用：

```yaml
spring:
  profiles:
    active: local
```

本地开发时通常只需要修改 `local` 段：

```yaml
medconsult:
  ai:
    llm:
      base-url: https://api.openai.com/v1
      api-key: 你的 API Key
      model: gpt-4o-mini
    embedding:
      base-url: https://api.openai.com/v1
      api-key: 你的 Embedding API Key
      model: text-embedding-3-small
```

生产部署时不要把密钥写入配置文件，改用环境变量并启用 `prod`：

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:NACOS_SERVER_ADDR="localhost:8848"
$env:MYSQL_URL="jdbc:mysql://localhost:3306/medconsult?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="root"
$env:REDIS_HOST="localhost"
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
$env:MONGODB_URI="mongodb://localhost:27017"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:OPENAI_API_KEY="你的 API Key"
$env:MILVUS_URI="http://localhost:19530"
```

注意：不要把真实 API Key 提交到仓库。未配置大模型、Embedding、Redis、MongoDB 或 Milvus 时，相关能力会按代码中的降级策略处理。

## 数据库初始化

AI 域表结构见 [schema-ai.sql](src/main/resources/db/schema-ai.sql)。

执行前确认 MySQL 已存在目标库：

```sql
CREATE DATABASE IF NOT EXISTS medconsult DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medconsult;
SOURCE ai/src/main/resources/db/schema-ai.sql;
```

该脚本覆盖 AI 业务表：

- `ai_chat_session`
- `ai_chat_message`
- `ai_triage_result`
- `ai_medical_summary`
- `ai_medication_analysis`
- `ai_imaging_detection`
- `ai_feedback`
- `ai_call_log`

`medical_record`、`doctor_schedule` 等传统业务表只在本模块中以轻量实体读取，不由本模块创建。

## 疾病知识数据要求

MongoDB 集合至少包含：

```json
{
  "name": "疾病名称",
  "symptom": ["症状1", "症状2"],
  "desc": "疾病描述",
  "cure_department": ["推荐科室"],
  "yibao_status": "医保状态",
  "check": ["检查项目"]
}
```

字段名与 `data/medical.data.unified.json` 保持一致。建议为 `name` 建索引：

```javascript
db.diseases.createIndex({ name: 1 })
```

Milvus 集合结构沿用 `data/src/main/java/com/medconsult/data/MedicalDataMilvusImporter.java` 的导入结果：

```text
id       字符串主键
name     疾病名称
text     向量化文本，包含 name、symptom、desc
vector   Embedding 向量
metadata JSON，包含 cure_department、yibao_status、category 等字段
```

## 症状自诊流程

`POST /ai/symptom-chat` 的核心流程：

1. 使用大模型从用户输入中抽取疾病候选、症状、医学描述和元数据查询意图。
2. 对可读中文但被模型误判为乱码的情况做本地纠偏。
3. 先查 Redis 缓存：
   - `medical:standardize:<sha256>`
   - `medical:disease:<标准疾病名>`
4. Redis 未命中时查 MongoDB `name` 精确匹配。
5. MongoDB 未命中时，将原始输入、LLM 候选、症状描述和本地口语扩展合成语义查询，调用 Embedding 后检索 Milvus。
6. 对结果按疾病名去重，并生成 `vectorMatches` 与 `citations`。
7. 执行高危症状规则，生成 `riskLevel` 与 `emergencyAdvice`。
8. 将命中的疾病 JSON 知识、引用、风险规则和患者上下文交给大模型生成最终回答。
9. 写入 `ai_chat_session`、`ai_chat_message`、`ai_call_log`。

最终回答要求：

- 不作确定诊断。
- 不编造疾病 JSON 之外的医学依据。
- 必须包含“非诊断，仅供参考，不能替代医生诊断”或同义提示。
- 若命中高危症状，优先提示急诊。

## 接口示例

### 症状自诊问答

```http
POST /api/v1/ai/symptom-chat
Content-Type: application/json
```

```json
{
  "sessionId": "CHAT202607060001",
  "patientId": "P202607060001",
  "message": "我想咨询肺栓塞。最近突然胸痛、胸闷、呼吸困难，还伴有心慌和咯血，应该挂什么科，需要做哪些检查？",
  "patientContext": {
    "age": 38,
    "gender": "MALE",
    "allergies": ["青霉素"],
    "pastMedicalHistory": ["高血压"]
  },
  "ragOptions": {
    "knowledgeSource": "DISEASE_JSON",
    "topK": 5,
    "returnCitations": true
  }
}
```

响应字段：

- `answerSource`：当前固定为 `RAG_WITH_FINAL_LLM`。
- `possibleCauses`：来自命中的疾病 JSON 条目名。
- `suggestedDepartments`：来自命中条目的 `cure_department`，高危时额外包含急诊科。
- `vectorMatches`：检索命中详情。
- `citations`：可追溯的疾病 JSON 引用。

### 智能分诊

```http
POST /api/v1/ai/triage
Content-Type: application/json
```

```json
{
  "patientId": "P202607060001",
  "symptoms": ["胸闷", "心悸", "活动后加重"],
  "duration": "3天",
  "severity": "MODERATE",
  "age": 38,
  "gender": "MALE",
  "pastMedicalHistory": ["高血压"],
  "needAvailableSchedules": true,
  "preferredDate": "2026-07-08"
}
```

### 病历摘要

```http
POST /api/v1/ai/medical-record-summary
Content-Type: application/json
```

```json
{
  "recordId": "MR202607060001",
  "summaryType": "STRUCTURED",
  "saveDraft": true
}
```

### 用药分析

```http
POST /api/v1/ai/medication-analysis
Content-Type: application/json
```

```json
{
  "patientId": "P202607060001",
  "recordId": "MR202607060001",
  "prescriptions": [
    {
      "drugId": "DRUG0003",
      "drugName": "布洛芬缓释胶囊",
      "dosage": "0.3g",
      "frequency": "每日两次",
      "route": "口服",
      "days": 3
    }
  ],
  "patientContext": {
    "allergies": ["青霉素"],
    "pastMedicalHistory": ["慢性胃炎"],
    "currentMedications": ["阿司匹林"]
  },
  "returnFunctionTrace": true
}
```

### 影像异常检测

```http
POST /api/v1/ai/imaging-abnormal-detection
Content-Type: application/json
```

```json
{
  "patientId": "P202607060001",
  "recordId": "MR202607060001",
  "imageType": "CHEST_CT",
  "reportText": "胸部 CT 示右肺上叶见小结节影，边界尚清，建议随访。",
  "imageUrls": ["https://oss.example.com/imaging/ct-001.dcm"],
  "externalModel": {
    "provider": "external-ai-provider",
    "model": "chest-ct-abnormal-detector"
  }
}
```

## 启动与验证

推荐在根目录执行多模块命令。

编译：

```powershell
mvn -q -pl ai -am -DskipTests compile
```

测试：

```powershell
mvn -q -pl ai -am test
```

全量测试：

```powershell
mvn -q test
```

启动 AI 服务：

```powershell
mvn -pl ai spring-boot:run
```

启动 Gateway：

```powershell
mvn -pl gateway spring-boot:run
```

服务地址：

```text
AI 服务直连: http://localhost:8082/api/v1
Gateway 入口: http://localhost:8080/api/v1
```

## 降级策略

- LLM 未配置或调用失败：症状自诊会尝试本地候选和向量检索兜底；最终回答会退化为规则模板。
- Redis 不可用：跳过缓存，不影响 MongoDB / Milvus 检索。
- Milvus 或 Embedding 不可用：仅使用 MongoDB `name` 精确匹配；若无命中则返回依据不足。
- 影像检测模型不可用：根据报告文本中的异常关键词做低置信度本地兜底。
- 用药分析模型不可用：使用本地基础规则生成风险提醒。

## 注意事项

- 本模块不负责维护传统业务表结构，只读取必要字段。
- `ai_call_log.request_summary` 和 `response_summary` 会截断保存，避免记录完整敏感内容。
- 症状自诊、分诊、摘要、用药和影像结果均为辅助信息，不能替代医生诊断。
- 若要完整联调 RAG，请先完成 MongoDB 疾病数据导入和 Milvus 向量数据导入。
- RabbitMQ 当前作为基础设施依赖与连接配置保留，后续可在不改变接口的前提下承接耗时 AI 任务异步化。
