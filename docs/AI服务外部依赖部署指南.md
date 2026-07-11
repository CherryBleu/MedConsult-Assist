# AI 服务外部依赖部署指南（Windows / PowerShell）

> 适用：ai-service 完整运行所需的 4 个外部依赖 —— MongoDB / Milvus / MinIO / 阿里云百炼 LLM。
> 已有的 MySQL / Redis / RabbitMQ / Nacos 不在本指南范围（见 `infra/README.md`）。

## 0. 依赖总览

| 依赖 | 用途 | 部署方式 | 默认端口 | 凭据 |
|---|---|---|---|---|
| MongoDB | 疾病知识库（`diseases` collection） | **本机安装**（用户已装） | 27017 | 无认证 |
| Milvus 2.4 | 向量检索（症状对齐疾病） | docker-compose | 19530 / 9091 | `root:Milvus` |
| MinIO | 医学影像文件存储 | docker-compose | 9000 / 9001 | `minioadmin/minioadmin` |
| 阿里云百炼 | LLM 对话（症状问诊 / 病历摘要） | 远程 API（OpenAI 兼容） | — | 需自行申请 |

> ⚠️ Embedding（向量生成）是**第 5 个隐性依赖**：阿里云百炼的 LLM 不提供 embedding 接口，需另配本地 TEI 或兼容服务。详见 §6。
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

**数据导入**：疾病知识来自 `backend/data/medical.data.unified.json`（48MB / 8807 条）。
当前该 JSON 存在编码问题（GBK 被当 UTF-8 读取导致乱码），且 `data` 模块尚未挂进 backend 聚合 pom。
**数据导入延后处理**——本次只确保基础设施就绪，collection 建空即可，ai-service 能正常启动。

验证：
```powershell
mongosh "mongodb://localhost:27017/medconsult" --eval "db.diseases.countDocuments()"
# 期望输出：0（空 collection，待后续导入）
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

### 3.3 REST v2 接口验证

```powershell
# 列出所有 collection（应该返回空 list，因为还没导入数据）
$body = '{"dbName":"medical"}'
Invoke-RestMethod -Uri http://localhost:19530/v2/vectordb/collections/list `
    -Method Post -ContentType "application/json" -Body $body
# 期望：code=0，data 中无 collection（或报 db 不存在——首次需先建库，见下方）
```

> 注意：ai-service 运行时调 `/v2/vectordb/entities/search`，如果库/collection 不存在会返回错误并被
> `MilvusRestClient` 捕获为空结果（不影响服务启动，只影响症状对齐检索）。

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
> 当前代码与 `infra/.env.example` 默认走**阿里云百炼**（通义千问 qwen-plus）。

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
ALIYUNBAILIAN_MODEL=qwen-plus
ALIYUNBAILIAN_TIMEOUT_SECONDS=60
```

> 也可改用 DeepSeek、OpenAI 等：把上述四个变量值换成对应供应商即可（变量名保持 `ALIYUNBAILIAN_*`，因为 `application.yml` 读的是这套名字）。

### 5.3 连通性验证

```powershell
# 设置环境变量（当前 shell 生效）
$env:ALIYUNBAILIAN_APIKEY = "sk-你的真实key"

# 调阿里云百炼 chat 接口（OpenAI 兼容协议）
$body = @{
    model = "qwen-plus"
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

## 6. Embedding（可选，但 RAG 检索依赖它）

⚠️ **关键认知**：`symptom-chat` 接口（症状问诊）的流程是「用户症状 → embedding → Milvus 检索相关疾病 → 把疾病知识喂给 LLM 生成回答」。LLM（阿里云百炼）**不提供 embedding 接口**，所以 embedding 必须单独配（本项目默认走本地 TEI 容器，见 §6.1）。

### 6.1 三种选择

| 方案 | 配置 | 费用 | 适用 |
|---|---|---|---|
| OpenAI embedding | `EMBEDDING_BASE_URL=https://api.openai.com/v1`，`model=text-embedding-3-small` | ~$0.02/百万 token | 海外网络通畅 |
| 国内兼容服务 | 智谱 / 通义 / 百川，把 base-url 和 key 换成对应服务 | 各家定价 | 国内首选 |
| 留空降级 | `EMBEDDING_API_KEY` 保留 `sk-placeholder` | 0 | 只用 LLM 对话，不要 RAG |

### 6.2 配置

`infra/.env` 中（替换为你的 embedding 服务凭证）：
```env
EMBEDDING_BASE_URL=https://api.openai.com/v1
EMBEDDING_API_KEY=sk-你的embedding-key
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=1536
```

> 留空时 `OpenAiCompatibleClient.embedOne()` 会 log `success=false reason=config_missing` 并返回空，
> `DiseaseSearchService` 拿到空检索结果，symptom-chat 退化为纯 LLM 对话（无疾病知识增强）。

---

## 7. 一次性拉起全部 AI 中间件

```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist"

# 拉起所有 compose 容器（含原有 MySQL/Redis/RabbitMQ/Qdrant + 新增 Milvus/MinIO）
docker compose -f infra/docker-compose.yml up -d

# 全量状态确认
docker compose -f infra/docker-compose.yml ps
```

期望看到 **7 个容器**（medconsult-mysql / -redis / -rabbitmq / -qdrant / -milvus-etcd / -milvus-minio / -milvus / -minio）全部 Up / healthy。
> Milvus 主体启动较慢（约 60-90s），`start_period=90s` 内 healthcheck 显示 unhealthy 是正常的。

---

## 8. 启动 ai-service 时注入环境变量

ai-service 从 JVM 环境变量读配置（不是从 docker-compose 读，因为它跑在宿主机上）。
启动前需把 `.env` 中的变量 export 到当前 shell：

### 方式 A：PowerShell 手动设
```powershell
$env:DEEPSEEK_API_KEY     = "sk-你的key"
$env:EMBEDDING_API_KEY    = "sk-你的embedding-key"   # 不要 RAG 可跳过
$env:MILVUS_URI           = "http://localhost:19530"
$env:MINIO_ENDPOINT       = "http://localhost:9000"
$env:MONGODB_URI          = "mongodb://localhost:27017"
```

### 方式 B：读 .env 自动设（推荐）
```powershell
# 一行加载 .env 到当前会话
Get-Content "D:\project\spring cloud alibaba\MedConsult-Assist\infra\.env" |
  Where-Object { $_ -match '^\s*[^#].*=' } |
  ForEach-Object {
    $kv = $_ -split '=', 2
    Set-Item -Path "Env:$($kv[0].Trim())" -Value $kv[1].Trim()
  }
```

### 然后启动
```powershell
cd "D:\project\spring cloud alibaba\MedConsult-Assist\backend"
.\mvnw.cmd -pl ai-service spring-boot:run
# 或用之前会话用的后台脚本方式
```

---

## 9. 全链路验证清单

| 检查项 | 命令 | 期望 |
|---|---|---|
| MongoDB 连通 | `Test-NetConnection localhost -Port 27017` | True |
| Milvus 健康 | `Invoke-RestMethod http://localhost:9091/healthz` | OK |
| MinIO 健康 | `(Invoke-WebRequest http://localhost:9000/minio/health/live).StatusCode` | 200 |
| DeepSeek 连通 | §5.3 的 chat 请求 | 一句中文回复 |
| ai-service 启动日志 | grep `Started AiServiceApplication` | 无异常 |
| ai-service 注册 Nacos | Nacos 控制台 http://localhost:8848/nacos | 服务列表有 `ai-service` |
| symptom-chat 接口 | `POST /api/v1/ai/symptom-chat`（带用户 JWT） | 返回 LLM 回答（RAG 数据未导入时为纯对话） |

---

## 10. 已知遗留项（不在本次范围）

1. **疾病 JSON 编码乱码**：`backend/data/medical.data.unified.json` 中文为 GBK 被当 UTF-8 读取的乱码，需重新获取正确编码的源数据。
2. ~~**data 模块孤儿问题**~~：**已修复**——`backend/data/pom.xml` 的 parent 已改为 `medconsult-parent`，并已挂进 `backend/pom.xml` 的 `<modules>`（`<module>data</module>`），可正常构建。
3. **Milvus 数据导入**：依赖第 1 项修复 + embedding 接口 + 8807 条数据约 8807 次 embedding 调用，建议单独安排。
4. **MongoDB 数据导入**：同源 JSON，同样需要正确编码版本，用 `mongoimport` 导入。

这三项的修复方案见主整合文档 `docs/三分支整合说明.md` §7 后续计划。
