#!/usr/bin/env bash
# 启动后端全栈（含 ai-service）。
# 前置：需先启动外部依赖——Nacos(8848)、MySQL(3306)、Redis(16379)、RabbitMQ(5672)、
#        MongoDB(27017)、Milvus(19530)、embedding-server(7997)。
#        用 docker compose -f infra/docker-compose.yml up -d 拉起容器化依赖；MongoDB 用本机实例。
# 用小堆内存(256m)以容纳多 JVM，绝对 jar 路径，auth 禁用 sql.init(DB 已迁移)
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="/tmp/svc"
mkdir -p "$LOG"

start() {
  local name="$1"; shift
  local jar="$DIR/$name/target/$name-0.1.0-SNAPSHOT.jar"
  if [ ! -f "$jar" ]; then echo "✗ $name jar 不存在"; return; fi
  nohup java -Xms128m -Xmx256m -jar "$jar" "$@" > "$LOG/$name.log" 2>&1 &
  echo "→ $name PID=$! ($jar)"
}

# 顺序：基础设施服务先(无依赖)，auth 单独禁用sql.init
start gateway
start patient-service
start auth-service --spring.sql.init.mode=never
start medical-record-service
start drug-service
start notification-service
start outpatient-service
# ai-service 依赖 Mongo/Milvus/embedding，最后启动；症状自诊路径不调 LLM，
# 因此 ALIYUNBAILIAN_APIKEY 即使是占位也不影响 symptom-chat（病历摘要等其它 AI 功能才需真实 key）
start ai-service --spring.sql.init.mode=never
echo "全部启动命令已发出（含 ai-service），等待注册(约60s)..."
