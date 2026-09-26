#!/usr/bin/env bash
set -eo pipefail

# 强制加载 Java 21 隔离环境 (SDKMAN)
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.5-tem >/dev/null 2>&1 || true
export JAVA_HOME="/Users/achilles/.sdkman/candidates/java/21.0.5-tem"
export PATH="$JAVA_HOME/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev/common.sh"

# 加载 .env 环境变量
if [[ -f "$PROJECT_DIR/.env" ]]; then
  set -a
  source "$PROJECT_DIR/.env"
  set +a
fi

# 参数解析
SKIP_BUILD=false
NO_TAIL=false
for arg in "$@"; do
  case "$arg" in
    --quick|--no-build)
      SKIP_BUILD=true
      ;;
    --no-tail)
      NO_TAIL=true
      ;;
  esac
done

start_background() {
  local service="$1"
  shift
  local log_file
  log_file="$(service_log_file "$service")"
  start_detached "$log_file" "$(service_pid_file "$service")" "$@"
  echo "[$service] 已拉起后台进程，日志输出至：$log_file"
}

cd "$PROJECT_DIR"
mkdir -p "$RUNTIME_DIR"

echo "================================================================"
echo "    Knowledge Hub 全栈智能体系统：一键全链路自动化启动脚本"
echo "================================================================"

# -----------------------------------------------------------------------------
# [1/6] 基础设施就绪检查 (Neo4j, PostgreSQL, Redis)
# -----------------------------------------------------------------------------
echo "[1/6] 检查并启动基础设施 (Neo4j, PostgreSQL, Redis)..."

# 1.1 Neo4j 容器启动与就绪检查
if ! docker ps --format '{{.Names}}' | grep -q "^agent-neo4j$"; then
  echo "正在启动 Neo4j 容器..."
  docker compose up -d neo4j
fi
echo "等待 Neo4j (7687) 就绪..."
NEO4J_READY=false
for i in $(seq 1 30); do
  if docker exec agent-neo4j cypher-shell -u neo4j -p "${NEO4J_PASSWORD:-neo4jpass123}" "RETURN 1" >/dev/null 2>&1; then
    echo "✓ Neo4j 图数据库已就绪"
    NEO4J_READY=true
    break
  fi
  sleep 1
done
if [[ "$NEO4J_READY" != "true" ]]; then
  echo "⚠ 警告: Neo4j 容器探活超时，图谱功能可能降级。"
fi

# 1.2 本机 PostgreSQL 探活
if lsof -tiTCP:5432 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "✓ PostgreSQL (5432) 端口正常监听"
else
  echo "❌ 错误: 未检测到本机 PostgreSQL (5432) 监听，请先启动 PostgreSQL 服务！" >&2
  exit 1
fi

# 1.3 本机 Redis 探活
if lsof -tiTCP:6379 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "✓ Redis (6379) 端口正常监听"
else
  echo "❌ 错误: 未检测到本机 Redis (6379) 监听，请先启动 Redis 服务！" >&2
  exit 1
fi

# -----------------------------------------------------------------------------
# [2/6] 清理旧实例并安全释放所有端口
# -----------------------------------------------------------------------------
echo "[2/6] 清理旧实例并安全释放前后端及微服务端口..."
for container in agent-frontend agent-backend qknow-hermes; do
  if docker rm -f "$container" >/dev/null 2>&1; then
    echo "已清理历史废弃容器：$container"
  fi
done

stop_service frontend
stop_service hermes
stop_service backend
stop_orphaned_dev_processes

# 全面释放前后端所有关联端口，防止端口冲突
release_port 5173 "前端 Vite 端口 5173"
release_port 80   "前端备用端口 80"
release_port 8099 "主后端业务端口 8099"
release_port 9090 "Hermes 智能体 gRPC 端口 9090"
release_port 8081 "Hermes 智能体 HTTP 端口 8081"

# -----------------------------------------------------------------------------
# [3/6] 后端代码编译检查
# -----------------------------------------------------------------------------
MAIN_JAR="$PROJECT_DIR/backend/qknow-server/target/qknow-server.jar"
HERMES_JAR="$PROJECT_DIR/backend/qknow-hermes/qknow-hermes-starter/target/qknow-hermes-starter-2.2.1.jar"

if [[ "$SKIP_BUILD" == "true" ]]; then
  echo "[3/6] 用户指定了 --quick / --no-build，跳过 Maven 编译"
  if [[ ! -f "$MAIN_JAR" || ! -f "$HERMES_JAR" ]]; then
    echo "❌ 错误: 未找到预编译产物，必须先进行编译构建！" >&2
    exit 1
  fi
else
  echo "[3/6] 清理并全量编译后端多模块与 Hermes 认知内核 (mvn install)..."
  rm -rf "$PROJECT_DIR/backend/qknow-hermes/qknow-hermes-proto/target"
  (cd "$PROJECT_DIR/backend" && mvn install -DskipTests) || { echo "❌ 后端编译失败"; exit 1; }
fi

# -----------------------------------------------------------------------------
# [4/6] 优先启动底层 Hermes 智能体微服务 (gRPC 9090 / HTTP 8081)
# -----------------------------------------------------------------------------
echo "[4/6] 启动 Hermes 智能体认知内核微服务 (gRPC 9090 / HTTP 8081)..."
start_background hermes \
  "$SCRIPT_DIR/dev/watch-java.sh" \
  hermes \
  "$PROJECT_DIR/backend/qknow-hermes" \
  qknow-hermes/qknow-hermes-starter \
  "$HERMES_JAR" \
  SPRING_PROFILES_ACTIVE=dev \
  POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:5432/ai_agent" \
  POSTGRESQL_USERNAME="${POSTGRESQL_USERNAME:-achilles}" \
  POSTGRESQL_PASSWORD="${POSTGRESQL_PASSWORD:-}" \
  SPRING_DATA_REDIS_HOST=127.0.0.1 \
  SPRING_DATA_REDIS_PORT=6379 \
  HERMES_GRPC_PORT=9090 \
  SERVER_PORT=8081 \
  HERMES_CONTROL_PLANE_URL=http://localhost:8099 \
  HERMES_OPENAI_API_KEY="${HERMES_OPENAI_API_KEY:-}" \
  LANGFUSE_ENABLED="${LANGFUSE_ENABLED:-false}" \
  LANGFUSE_PUBLIC_KEY="${LANGFUSE_PUBLIC_KEY:-}" \
  LANGFUSE_SECRET_KEY="${LANGFUSE_SECRET_KEY:-}" \
  LANGFUSE_BASE_URL="${LANGFUSE_BASE_URL:-https://cloud.langfuse.com}"

echo "等待 Hermes gRPC 端口 9090 就绪..."
wait_for_tcp "Hermes gRPC 服务" 9090 120
echo "✓ Hermes gRPC (9090) 已经成功就绪！"

# -----------------------------------------------------------------------------
# [5/6] 启动主业务后端 qknow-server (HTTP 8099)
# -----------------------------------------------------------------------------
echo "[5/6] 启动主业务后端 qknow-server (HTTP 8099)..."
start_background backend \
  "$SCRIPT_DIR/dev/watch-java.sh" \
  backend \
  "$PROJECT_DIR/backend" \
  qknow-server \
  "$MAIN_JAR" \
  SPRING_PROFILES_ACTIVE=dev \
  POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:5432/ai_agent" \
  POSTGRESQL_USERNAME="${POSTGRESQL_USERNAME:-achilles}" \
  POSTGRESQL_PASSWORD="${POSTGRESQL_PASSWORD:-}" \
  SPRING_DATA_REDIS_HOST=127.0.0.1 \
  SPRING_DATA_REDIS_PORT=6379 \
  HERMES_OPENAI_API_KEY="${HERMES_OPENAI_API_KEY:-}" \
  HERMES_GRPC_HOST=localhost \
  HERMES_GRPC_PORT=9090

echo "等待主后端端口 8099 就绪..."
wait_for_tcp "主业务后端" 8099 180
echo "✓ 主业务后端 (8099) 已经成功就绪！"

# -----------------------------------------------------------------------------
# [6/6] 启动前端 Vite 开发服务器 (HTTP 5173)
# -----------------------------------------------------------------------------
echo "[6/6] 启动前端 Vite 客户端开发服务器 (HTTP 5173)..."
if [[ ! -d "$PROJECT_DIR/frontend/node_modules" ]]; then
  echo "安装前端依赖包..."
  npm --prefix "$PROJECT_DIR/frontend" install --registry=https://registry.npmmirror.com
fi
start_background frontend npm --prefix "$PROJECT_DIR/frontend" run dev

echo "等待前端开发服务器 5173 就绪..."
wait_for_http "前端 Vite 服务" "http://localhost:5173/" 60
echo "✓ 前端 Vite (5173) 已经成功就绪！"

# -----------------------------------------------------------------------------
# 汇总就绪状态面板
# -----------------------------------------------------------------------------
echo
echo "================================================================"
echo "    Knowledge Hub 全微服务协同栈已全部成功启动完毕！"
echo "================================================================"
echo "  [前端应用界面] : http://localhost:5173/"
echo "  [主业务控制面] : http://localhost:8099/"
echo "  [Hermes 认知核] : http://localhost:8081/  (gRPC: localhost:9090)"
echo "  [图谱 Neo4j]   : bolt://localhost:7687  (Browser: http://localhost:7474)"
echo "  [向量/主数据库] : PostgreSQL @ 127.0.0.1:5432 (ai_agent)"
echo "  [分布式缓存]   : Redis @ 127.0.0.1:6379"
echo "----------------------------------------------------------------"
echo "  查看当前集群状态 : bash scripts/status.sh"
echo "  一键平滑停止集群 : bash scripts/stop.sh"
echo "================================================================"
echo

if [[ "$NO_TAIL" != "true" ]]; then
  echo "正在实时跟踪前端日志（按 Ctrl+C 退出日志跟随，服务将保持在后台长效运行）..."
  tail -n 60 -F "$(service_log_file frontend)"
fi
