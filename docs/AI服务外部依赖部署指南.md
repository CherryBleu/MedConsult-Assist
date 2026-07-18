# AI 服务外部依赖部署指南（Windows / PowerShell）

> 适用：ai-service 完整运行所需的 4 个外部依赖 —— MongoDB / Milvus / MinIO / 阿里云百炼 LLM。
> 已有的 MySQL / Redis / RabbitMQ / Nacos 不在本指南范围（见 `infra/README.md`）。

## 0. 依赖总览

| 依赖 | 用途 | 部署方式 | 默认端口 | 凭据 |
|---|---|---|---|---|
| MongoDB | 疾病知识库（`diseases` collection） | **本机安装**（用户已装） | 27017 | 无认证 |
| Milvus 2.4 | 向量检索（症状对齐疾病） | docker-compose | 19530 / 9091 | `root:Milvus` |
| MinIO | 医学影像文件存储 | docker-compose | 9000 / 9001 | `minioadmin/minioadmin` |
| Embedding | 向量生成（症状→向量→Milvus 检索） | docker-compose（本地 Python 容器） | 7997 | 无需 api-key |
| 阿里云百炼 | LLM 对话（症状问诊 / 病历摘要） | 远程 API（OpenAI 兼容） | — | 需自行申请 |

> Embedding 随 `docker compose up -d` 自动拉起，无需额外申请凭证。详见 §6。
>
> 变量名以 `infra/.env.example` 与 `backend/ai-service/src/main/resources/application.yml` 为准（均为 `ALIYUNBAILIAN_*`）。

---

## 1. 前置检查

```powershell
# 1.1 确认 Docker Desktop 已启动
docker version

# 1.2 确认基础中间件已运行（MySQL/Redis/RabbitMQ/Nacos）
docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"

# 1.3 确认本机 MongoDB 在 27017
powershell -Command "Test-NetConnection -ComputerName localhost -Port 27017 -InformationLevel Quiet"
# 期望输出：True
```

---

## 2. MongoDB（疾病知识库）

MongoDB 已由用户本机安装，无需重复部署。只需确认库和 collection 就绪：

```powershell
# 2.1 连接 MongoDB（用 mongosh 或 mongo）
mongosh "mongodb://localhost:27017"

# 2.2 在 mongosh shell 内执行：
use medconsult
db.createCollection("diseases")
show collections          # 应看到 diseases
exit
```

**数据导入**：疾病知识来自 `backend/data/medical.data.unified.json`（48MB / 8807 条，UTF-8）。
`ai-service` 的 RAG 自检默认要求 Mongo `medconsult.diseases` 至少有 8807 条，否则会在 `/api/v1/ai/rag/readiness` 标记 `mongo=DOWN`，症状自诊会以 degraded 语义提示“知识库自检未完全通过”，不再把空库误判为“无命中”。

验证：
```powershell
mongosh "mongodb://localhost:27017/medconsult" --eval "db.diseases.countDocuments()"
# 期望输出：8807（低于该值需重新导入疾病知识库）
```

---

## 3. Milvus（向量检索）

### 3.1 启动

Milvus standalone 三件套（etcd + 内置 minio + milvus）已写进 `infra/docker-compose.yml`，直接拉起：

```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist"

# 拉起 Milvus 三件套（etcd / milvus-minio / milvus）
docker compose -f infra/docker-compose.yml up -d milvus-etcd milvus-minio milvus
```

> 首次拉镜像约 1.5GB（milvus 主体 ~900MB + etcd ~50MB + minio ~300MB），耐心等待。

### 3.2 健康检查

```powershell
# 查看三个容器状态（milvus 启动较慢，start_period=90s）
docker compose -f infra/docker-compose.yml ps milvus-etcd milvus-minio milvus

# 直接探测 Milvus 健康端点
powershell -Command "Invoke-RestMethod http://localhost:9091/healthz"
# 期望输出：OK
```

### 3.3 建库、导入与 flush

Milvus REST 创建 collection 前必须先存在 database；如本地只有 `default`，先用 `pymilvus` 创建 `medical`：

```powershell
@'
from pymilvus import MilvusClient
client = MilvusClient(uri='http://localhost:19530', token='root:Milvus')
if 'medical' not in client.list_databases():
    client.create_database('medical')
print(client.list_databases())
'@ | python -
```

导入器必须与 ai-service 使用同一 embedding 模型、维度、库、集合和 metric：

```powershell
$env:MEDICAL_DATA_INPUT = "backend/data/medical.data.unified.json"
$env:EMBEDDING_BASE_URL = "http://localhost:7997/v1"
$env:EMBEDDING_API_KEY = "not-needed"
$env:EMBEDDING_MODEL = "BAAI/bge-small-zh-v1.5"
$env:EMBEDDING_DIMENSION = "512"
$env:MILVUS_URI = "http://localhost:19530"
$env:MILVUS_TOKEN = "root:Milvus"
$env:MILVUS_DATABASE = "medical"
$env:MILVUS_COLLECTION = "data"
$env:MILVUS_METRIC_TYPE = "COSINE"
mvn -f backend/pom.xml -pl :medical-data-milvus-importer exec:java
```

本地 Milvus 2.4.13 REST v2 不支持 `/v2/vectordb/collections/flush`。导入后如果 `get_stats` 仍为 0，执行一次 gRPC flush：

```powershell
@'
from pymilvus import MilvusClient
client = MilvusClient(uri='http://localhost:19530', token='root:Milvus', db_name='medical')
client.flush(collection_name='data')
print(client.query(collection_name='data', filter='', output_fields=['count(*)']))
'@ | python -
```

2026-07-19 本地证据：导入器读取 8807 条、写入 8807 条；`count(*)=8807`，源 JSON 8807 个唯一 `_id` 与 Milvus 可查询 `id` 完全一致。重复 upsert 后 REST `get_stats.rowCount` 可能大于 8807（当前 8817），不能单独当作业务主键数量。

### 3.4 REST v2 接口验证

```powershell
# 列出 collection（导入完成后应包含 data）
$body = '{"dbName":"medical"}'
Invoke-RestMethod -Uri http://localhost:19530/v2/vectordb/collections/list `
    -Method Post -ContentType "application/json" -Body $body
# 期望：code=0，data 中包含 collection=data

# 查看向量行数（ai-service RAG 自检也会调用该统计接口）
$statsBody = '{"dbName":"medical","collectionName":"data"}'
Invoke-RestMethod -Uri http://localhost:19530/v2/vectordb/collections/get_stats `
    -Method Post -ContentType "application/json" -Body $statsBody
# 期望：rowCount > 0；精确业务数量以 pymilvus count(*) 和主键对账为准
```

> 注意：ai-service 运行时调 `/v2/vectordb/entities/search`，并在启动/人工刷新时通过
> `/api/v1/ai/rag/readiness?refresh=true` 检查 Mongo 行数、Milvus 行数和 embedding 维度。库/collection 不存在或向量为空会明确标记为 degraded，不再静默当作“未检索到相关疾病”。

---

## 4. MinIO（影像文件存储）

### 4.1 启动

```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist"
docker compose -f infra/docker-compose.yml up -d minio
```

### 4.2 健康检查

```powershell
# 探测 live 端点
powershell -Command "(Invoke-WebRequest http://localhost:9000/minio/health/live).StatusCode"
# 期望输出：200
```

### 4.3 Web 控制台

浏览器打开 **http://localhost:9001**，用 `minioadmin` / `minioadmin` 登录。

> ai-service 配置了 `auto-create-bucket=true`，首次上传影像时会自动创建 `medconsult` bucket，**无需手动建**。
> 如果想预先建好：
> ```powershell
> docker exec medconsult-minio mc alias set local http://localhost:9000 minioadmin minioadmin
> docker exec medconsult-minio mc mb local/medconsult
> ```

---

## 5. 阿里云百炼 LLM（OpenAI 兼容）

> ai-service 通过 langchain4j 的 `OpenAiChatModel` 调用，因此任何 OpenAI 兼容协议的 LLM 均可。
> 当前代码与 `infra/.env.example` 默认走**阿里云百炼**（通义千问 qwen3.7-plus）。

### 5.1 申请 API Key

1. 访问 **https://bailian.console.aliyun.com**
2. 开通百炼 → API-KEY 管理 → 创建新 key（格式 `sk-...`）

### 5.2 写入 .env

```powershell
# 编辑 infra/.env，把占位换成你的真实 key
notepad "D:\project\spring cloud alibaba\MedConsult-Assist\infra\.env"
```

`.env` 中相关行（与 `application.yml` 读取的变量名 `ALIYUNBAILIAN_*` 一致）：
```env
ALIYUNBAILIAN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
ALIYUNBAILIAN_APIKEY=sk-你的真实key
ALIYUNBAILIAN_MODEL=qwen3.7-plus
ALIYUNBAILIAN_TIMEOUT_SECONDS=60
```

> 也可改用 DeepSeek、OpenAI 等：把上述四个变量值换成对应供应商即可（变量名保持 `ALIYUNBAILIAN_*`，因为 `application.yml` 读的是这套名字）。

### 5.3 连通性验证

```powershell
# 设置环境变量（当前 shell 生效）
$env:ALIYUNBAILIAN_APIKEY = "sk-你的真实key"

# 调阿里云百炼 chat 接口（OpenAI 兼容协议）
$body = @{
    model = "qwen3.7-plus"
    messages = @(@{ role = "user"; content = "你好，回复一句" })
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod -Uri "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" `
    -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $env:ALIYUNBAILIAN_APIKEY" } `
    -Body $body

$response.choices[0].message.content
# 期望：一句中文回复
```

---

## 6. Embedding（ai-service RAG 依赖，随 docker compose 自动拉起）

⚠️ **关键认知**：`symptom-chat` 接口（症状问诊）的流程是「用户症状 → embedding → Milvus 检索相关疾病 → 把疾病知识喂给 LLM 生成回答」。LLM（阿里云百炼）**不提供 embedding 接口**，所以 embedding 必须单独配。

本项目内置了一个本地 Embedding 容器（Flask + sentence-transformers，模型 `BAAI/bge-small-zh-v1.5`，512 维），**无需额外 API key，无需申请外部服务**。

### 6.1 一键启动（默认方案，推荐）

Embedding 容器已写进 `infra/docker-compose.yml`，随 `docker compose up -d` 自动拉起：

```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist"

# 单独拉起 embedding（或随全量 up -d 一起）
docker compose -f infra/docker-compose.yml up -d embedding

# 查看构建日志（首次构建约 5min：下载 torch CPU wheel + sentence-transformers）
docker compose -f infra/docker-compose.yml logs -f embedding
```

> 首次启动会从 `hf-mirror.com`（国内 HuggingFace 镜像）下载模型文件（~95MB），缓存在 Docker 命名卷 `embedding_model_cache` 内，后续重启秒加载。
>
> `start_period=120s` 内 healthcheck 显示 `unhealthy` 是正常的（模型还在下载/加载）。

### 6.2 健康检查

```powershell
# 探测健康端点
powershell -Command "Invoke-RestMethod http://localhost:7997/health"
# 期望: {"status":"ok","model":"BAAI/bge-small-zh-v1.5","loaded":true,"dimension":512,"device":"cpu"}

# 手动测试 embedding 接口（OpenAI 兼容格式）
$body = @{
    model = "BAAI/bge-small-zh-v1.5"
    input = @("感冒发烧咳嗽")
} | ConvertTo-Json
$response = Invoke-RestMethod -Uri "http://localhost:7997/v1/embeddings" `
    -Method Post -ContentType "application/json" -Body $body
$response.data[0].embedding.Count
# 期望: 512
```

### 6.3 配置说明

`infra/.env` 中（默认值已配好，无需修改）：
```env
EMBEDDING_BASE_URL=http://localhost:7997/v1
EMBEDDING_API_KEY=not-needed
EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
EMBEDDING_DIMENSION=512
HF_ENDPOINT=https://hf-mirror.com
```

- `EMBEDDING_BASE_URL`：ai-service 和数据导入器都通过此 URL 调用 embedding 接口
- `EMBEDDING_API_KEY`：本地容器不需要鉴权，保留 `not-needed` 占位即可
- `HF_ENDPOINT`：模型下载地址，`https://hf-mirror.com` 为国内镜像加速；海外网络可改为 `https://huggingface.co`

### 6.4 可选：切换为外部 Embedding 服务

如不想本地跑容器，可改用 OpenAI / 智谱 / 通义等提供的 embedding API：
```env
# 例：OpenAI embedding（需海外网络 + API key）
EMBEDDING_BASE_URL=https://api.openai.com/v1
EMBEDDING_API_KEY=sk-你的-openai-key
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=1536
```

> ⚠️ 切换模型后 Milvus 中已导入的向量与新模型向量空间不兼容，需重新导入数据。
> 导入器和 ai-service 必须使用同一 embedding 模型，否则检索结果无意义。

---

## 7. 一次性拉起全部 AI 中间件

```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist"

# 拉起所有 compose 容器（含原有 MySQL/Redis/RabbitMQ/Qdrant + 新增 Milvus/MinIO）
docker compose -f infra/docker-compose.yml up -d

# 全量状态确认
docker compose -f infra/docker-compose.yml ps
```

期望看到 **8 个容器**（medconsult-mysql / -redis / -rabbitmq / -qdrant / -milvus-etcd / -milvus-minio / -milvus / -minio / -embedding）全部 Up / healthy。
> Milvus 主体启动较慢（约 60-90s），`start_period=90s` 内 healthcheck 显示 unhealthy 是正常的。

---

## 8. 启动 ai-service 时注入环境变量

ai-service 从 JVM 环境变量读配置（不是从 docker-compose 读，因为它跑在宿主机上）。
启动前需把 `.env` 中的变量 export 到当前 shell：

### 方式 A：PowerShell 手动设
```powershell
$env:ALIYUNBAILIAN_APIKEY = "sk-你的key"              # 使用 DeepSeek/OpenAI 时也保持变量名不变，只替换值和 base-url
$env:ALIYUNBAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:ALIYUNBAILIAN_MODEL  = "qwen3.7-plus"
$env:EMBEDDING_BASE_URL   = "http://localhost:7997/v1"
$env:EMBEDDING_API_KEY    = "not-needed"
$env:EMBEDDING_MODEL      = "BAAI/bge-small-zh-v1.5"
$env:MILVUS_URI           = "http://localhost:19530"
$env:MILVUS_DATABASE      = "medical"
$env:MILVUS_COLLECTION    = "data"
$env:MILVUS_METRIC_TYPE   = "COSINE"                 # 必须与导入器建索引 metric 一致
$env:MILVUS_SEARCH_TIMEOUT_SECONDS = "15"
$env:MINIO_ENDPOINT       = "http://localhost:9000"
$env:MONGODB_URI          = "mongodb://localhost:27017"
```

`MILVUS_METRIC_TYPE` 默认 `COSINE`，可选 `COSINE`/`IP`/`L2`/`EUCLIDEAN`；`EUCLIDEAN` 会规范为 Milvus 原生 `L2`。如果切换 metric，需先删除旧 collection 并用 `backend/data` 导入器带同一 `MILVUS_METRIC_TYPE` 重新导入，否则运行时会按错误分数语义过滤召回结果。

### 方式 B：读 .env 自动设（推荐）
```powershell
# 一行加载 .env 到当前会话
Get-Content "<你的 worktree>\infra\.env" |
  Where-Object { $_ -match '^\s*[^#].*=' } |
  ForEach-Object {
    $kv = $_ -split '=', 2
    Set-Item -Path "Env:$($kv[0].Trim())" -Value $kv[1].Trim()
  }
```

### 然后启动

方式 C：VS Code / Spring Boot Dashboard。选择 `.vscode/launch.json` 中的 `Spring Boot-AiServiceApplication<ai-service>`。该启动项的 `cwd` 必须是 `${workspaceFolder}/backend/ai-service`；`medconsult-common` 和 `data` 不应出现在 Dashboard 的生产服务启动配置里。

方式 D：命令行启动（推荐用可执行 jar，避免 `spring-boot:run -am` 在多模块 reactor 中误运行 library 模块）。
```powershell
cd "<你的 worktree>\backend"
.\mvnw.cmd -pl :ai-service -am -DskipTests package

cd ai-service
java -jar target\ai-service-0.1.0-SNAPSHOT.jar
```

2026-07-19 本地冒烟：在 `backend/ai-service` 工作目录下启动可执行 jar，日志出现 `Tomcat started on port 8086`、`REGISTER-SERVICE ... 127.0.0.1:8086` 和 `Started AiServiceApplication`。

---

## 9. 全链路验证清单

| 检查项 | 命令 | 期望 |
|---|---|---|
| MongoDB 连通 | `Test-NetConnection localhost -Port 27017` | True |
| Milvus 健康 | `Invoke-RestMethod http://localhost:9091/healthz` | OK |
| MinIO 健康 | `(Invoke-WebRequest http://localhost:9000/minio/health/live).StatusCode` | 200 |
| LLM 连通 | §5.3 的 chat 请求 | 一句中文回复 |
| ai-service 启动日志 | grep `Started AiServiceApplication` | 无异常 |
| ai-service 注册 Nacos | Nacos 控制台 http://localhost:8848/nacos | 服务列表有 `ai-service` |
| symptom-chat 接口 | `POST /api/v1/ai/symptom-chat`（带用户 JWT） | 返回 LLM 回答（RAG 数据未导入时为纯对话） |

---

## 10. 已知遗留项（不在本次范围）

1. ~~**疾病 JSON 编码乱码**~~：2026-07-19 已复核当前 `backend/data/medical.data.unified.json` 可按 UTF-8 读取，Mongo/Milvus 样本中文字段正常。
2. ~~**data 模块孤儿问题**~~：**已修复**——`backend/data/pom.xml` 的 parent 已改为 `medconsult-parent`，并已挂进 `backend/pom.xml` 的 `<modules>`（`<module>data</module>`），可正常构建。
3. ~~**Milvus 数据导入**~~：2026-07-19 已导入 `medical.data`，可查询 8807 个源 JSON 主键；后续仍需固定症状探针和召回质量门禁。
4. ~~**MongoDB 数据导入**~~：2026-07-19 已验收 `medconsult.diseases=8807`。

这三项的修复方案见主整合文档 `docs/三分支整合说明.md` §7 后续计划。
