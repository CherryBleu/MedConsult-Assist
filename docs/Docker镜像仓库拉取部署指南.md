# Docker 镜像仓库拉取使用文档

本文从拉取 Docker Hub 镜像开始，目标是在新机器上启动一套与本机 Docker 容器命名、网络、端口和环境变量一致的 MedConsult-Assist。

项目自建镜像统一放在 Docker Hub 仓库：

```text
cherrybleu/medconsult-assist
```

同一个仓库使用不同 tag 区分服务：

```text
auth-service-latest
patient-service-latest
outpatient-service-latest
medical-record-service-latest
drug-service-latest
notification-service-latest
ai-service-latest
gateway-latest
frontend-latest
embedding-latest
```

基础设施镜像仍从公共仓库直接拉取，例如 `mysql:8.0`、`mongo:7`、`milvusdb/milvus:v2.4.13`、`nacos/nacos-server:v2.3.0`。

## 1. 准备文件

部署机器需要拿到仓库里的这些文件：

```text
infra/docker-compose.registry.yml
infra/.env.example
infra/mysql/init/
infra/mysql/conf.d/
backend/data/medical.data.unified.json
backend/data/
backend/mvnw.cmd
backend/pom.xml
```

`backend/data/medical.data.unified.json` 是 AI 问诊 RAG 的疾病知识库源数据。只拉镜像不会包含 MongoDB / Milvus 数据卷，因此首次部署必须导入它。

## 2. 配置环境变量

复制模板：

```powershell
Copy-Item infra\.env.example infra\.env
```

确认或修改 `infra\.env`：

```text
MEDCONSULT_IMAGE_REPOSITORY=cherrybleu/medconsult-assist
MEDCONSULT_IMAGE_TAG=latest
ALIYUNBAILIAN_APIKEY=你的真实百炼 API KEY
```

真实 API KEY 只放在部署机器的 `infra\.env` 或云平台 Secret 中。镜像仓库里的镜像不包含你的本机 `.env`。

## 3. 拉取镜像

如果仓库是公开的，直接拉取：

```powershell
docker compose --env-file infra\.env -f infra\docker-compose.registry.yml pull
```

如果仓库设为私有，需要先登录 Docker Hub：

```powershell
docker login
docker compose --env-file infra\.env -f infra\docker-compose.registry.yml pull
```

## 4. 启动容器

```powershell
docker compose --env-file infra\.env -f infra\docker-compose.registry.yml up -d
```

查看状态：

```powershell
docker compose --env-file infra\.env -f infra\docker-compose.registry.yml ps
```

访问地址：

```text
前端：http://127.0.0.1:3000
网关：http://127.0.0.1:8080
Nacos：http://127.0.0.1:18848/nacos
MinIO：http://127.0.0.1:9001
RabbitMQ：http://127.0.0.1:15672
```

## 5. 首次启动后的数据状态

镜像仓库只保存镜像，不保存数据库卷。新机器第一次启动后：

- MySQL 会按 `infra/mysql/init` 初始化业务 schema 和表。
- MongoDB 的 `medconsult.diseases` 是空的。
- Milvus 的 `medical.data` collection 是空的或不存在。
- Embedding 模型缓存卷首次可能为空，`medconsult-embedding` 会下载模型到 `embedding_model_cache`。

AI 问诊的检索证据依赖 MongoDB 原始疾病 JSON 和 Milvus 向量索引。必须按下面两节导入数据，否则 RAG readiness 会显示 Mongo/Milvus 未就绪。

## 6. 导入 MongoDB 疾病 JSON

源文件：

```text
backend/data/medical.data.unified.json
```

导入：

```powershell
docker cp backend\data\medical.data.unified.json medconsult-mongodb:/tmp/medical.data.unified.json

docker exec medconsult-mongodb mongoimport `
  --db medconsult `
  --collection diseases `
  --file /tmp/medical.data.unified.json `
  --jsonArray `
  --drop
```

校验：

```powershell
docker exec medconsult-mongodb mongosh --quiet --eval "db.getSiblingDB('medconsult').diseases.countDocuments()"
```

期望输出：

```text
8807
```

`--drop` 会先删除 `medconsult.diseases` collection 再导入，只用于知识库重建，不要对业务数据 collection 使用。

## 7. 导入 Milvus 向量索引

### 7.1 创建 Milvus Database

```powershell
docker run --rm --network medconsult-net python:3.12-slim sh -c `
  "pip install pymilvus && python - <<'PY'
from pymilvus import MilvusClient
client = MilvusClient(uri='http://medconsult-milvus:19530', token='root:Milvus')
if 'medical' not in client.list_databases():
    client.create_database('medical')
print(client.list_databases())
PY"
```

### 7.2 运行 JSON 到 Milvus 导入器

导入器位于 `backend/data`，会读取 JSON、调用 embedding 服务生成 512 维向量，再写入 Milvus `medical.data`。

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

.\backend\mvnw.cmd -f backend\pom.xml -pl :medical-data-milvus-importer exec:java
```

正式导入期望写入 8807 条。首次导入需要等待 embedding 模型下载完成；可先确认：

```powershell
curl.exe http://127.0.0.1:7997/health
```

### 7.3 Flush 与精确数量校验

```powershell
docker run --rm --network medconsult-net python:3.12-slim sh -c `
  "pip install pymilvus && python - <<'PY'
from pymilvus import MilvusClient
client = MilvusClient(uri='http://medconsult-milvus:19530', token='root:Milvus', db_name='medical')
client.flush(collection_name='data')
print(client.query(collection_name='data', filter='', output_fields=['count(*)']))
PY"
```

期望 `count(*)` 为：

```text
8807
```

REST 也可以做非空校验：

```powershell
$body = '{"dbName":"medical"}'
Invoke-RestMethod -Uri http://localhost:19530/v2/vectordb/collections/list `
  -Method Post -ContentType "application/json" -Body $body

$statsBody = '{"dbName":"medical","collectionName":"data"}'
Invoke-RestMethod -Uri http://localhost:19530/v2/vectordb/collections/get_stats `
  -Method Post -ContentType "application/json" -Body $statsBody
```

REST `rowCount` 可能因重复 upsert 或 segment 统计略有偏差；精确业务数量以 `pymilvus count(*)` 为准。

## 8. 重启与验证

导入 MongoDB 和 Milvus 后重启 AI 服务：

```powershell
docker restart medconsult-ai-service
```

验证容器状态：

```powershell
docker compose --env-file infra\.env -f infra\docker-compose.registry.yml ps
```

验证 RAG readiness：

```powershell
curl.exe http://127.0.0.1:8080/api/v1/ai/rag/readiness
```

如果 readiness 里 Mongo 或 Milvus 数量不足，回到第 6、7 节重新导入。

## 9. 常见问题

不要单独运行业务镜像：

```powershell
docker run cherrybleu/medconsult-assist:ai-service-latest
```

单独运行时容器内的 `127.0.0.1` 不是宿主机，也没有 compose 注入的 `NACOS_ADDR=medconsult-nacos:8848`、`MYSQL_HOST=medconsult-mysql` 等变量，服务通常会连接失败。

检查 API KEY 是否注入：

```powershell
docker exec medconsult-ai-service sh -c 'test -n "$ALIYUNBAILIAN_APIKEY" && echo configured || echo missing'
```

只确认是否已配置，不直接打印密钥值。
