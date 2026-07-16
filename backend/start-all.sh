#!/usr/bin/env bash
# 启动后端全栈（除 ai-service，需外部依赖 MongoDB/Milvus/MinIO/百炼）
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
echo "全部启动命令已发出，等待注册(约60s)..."
