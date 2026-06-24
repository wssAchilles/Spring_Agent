#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev/common.sh"

# 加载 .env 文件中的密钥配置
set -a
source "$SCRIPT_DIR/../.env"
set +a

start_background() {
  local service="$1"
  shift
  local log_file
  log_file="$(service_log_file "$service")"
  start_detached "$log_file" "$(service_pid_file "$service")" "$@"
  echo "$service 启动中，日志：$log_file"
}

cd "$PROJECT_DIR"
mkdir -p "$RUNTIME_DIR"

echo "[1/5] 启动 Redis"
docker compose up -d redis

echo "[2/5] 清理旧实例并释放端口"
for container in agent-frontend agent-backend qknow-hermes; do
  if docker rm -f "$container" >/dev/null 2>&1; then
    echo "已移除容器：$container"
  fi
done

stop_service frontend
stop_service backend
stop_service hermes
stop_orphaned_dev_processes

release_port 80 "前端端口 80"
release_port 8099 "后端端口 8099"
release_port 9090 "Hermes 端口 9090"

echo "[3/5] 启动本机主后端"
start_background backend \
  "$SCRIPT_DIR/dev/watch-java.sh" \
  backend \
  "$PROJECT_DIR/backend" \
  qknow-server \
  "$PROJECT_DIR/backend/qknow-server/target/qknow-server.jar" \
  SPRING_PROFILES_ACTIVE=dev \
  POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:5432/ai_agent" \
  POSTGRESQL_USERNAME="${POSTGRESQL_USERNAME:-achilles}" \
  POSTGRESQL_PASSWORD="${POSTGRESQL_PASSWORD:-}" \
  SPRING_DATA_REDIS_HOST=127.0.0.1 \
  SPRING_DATA_REDIS_PORT=6379 \
  HERMES_GRPC_HOST=localhost \
  HERMES_GRPC_PORT=9090
wait_for_tcp "主后端" 8099 240

echo "[4/5] 启动本机 Hermes"
start_background hermes \
  "$SCRIPT_DIR/dev/watch-java.sh" \
  hermes \
  "$PROJECT_DIR/backend/qknow-hermes" \
  qknow-hermes/qknow-hermes-starter \
  "$PROJECT_DIR/backend/qknow-hermes/qknow-hermes-starter/target/qknow-hermes-starter-2.2.1.jar" \
  SPRING_PROFILES_ACTIVE=dev \
  HERMES_GRPC_PORT=9090 \
  HERMES_CONTROL_PLANE_URL=http://localhost:8099
wait_for_tcp "Hermes" 9090 180

echo "[5/5] 启动本机 Vite"
if [[ ! -d "$PROJECT_DIR/frontend/node_modules" ]]; then
  npm --prefix "$PROJECT_DIR/frontend" install --registry=https://registry.npmmirror.com
fi
start_background frontend npm --prefix "$PROJECT_DIR/frontend" run dev
wait_for_http "前端" "http://localhost/" 60

echo
echo "本机开发环境已启动"
echo "前端: http://localhost/"
echo "后端: http://localhost:8099/"
echo "Hermes gRPC: localhost:9090"
echo "状态: bash scripts/status.sh"
echo "停止: bash scripts/stop.sh"
echo
echo "正在实时输出前端日志。按 Ctrl+C 只退出日志跟随，服务仍会继续运行。"
echo "如需停止服务，请另开终端执行：bash scripts/stop.sh"
echo

tail -n 120 -F "$(service_log_file frontend)"
