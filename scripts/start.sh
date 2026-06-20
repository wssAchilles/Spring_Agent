#!/bin/bash
# ============================================================================
# AI-Native RAG 智能体编排平台 — 一键启动脚本
# 用法: ./scripts/start.sh
# ============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PORT=8099
FRONTEND_PORT=80
REDIS_EMBEDDED_PORT=12138

log_info()  { echo -e "${GREEN}[✓]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
log_error() { echo -e "${RED}[✗]${NC} $1"; }
log_step()  { echo -e "${BLUE}[→]${NC} $1"; }

# ============================================================================
# 1. 停止已有服务，释放端口
# ============================================================================

echo ""
echo -e "${BLUE}=========================================="
echo -e "  AI-Native RAG 智能体编排平台"
echo -e "==========================================${NC}"
echo ""

log_step "检查并释放端口..."

for PORT in $BACKEND_PORT $FRONTEND_PORT $REDIS_EMBEDDED_PORT; do
    PID=$(lsof -ti :$PORT 2>/dev/null || true)
    if [ -n "$PID" ]; then
        log_warn "端口 $PORT 被进程 $PID 占用，正在释放..."
        kill -9 $PID 2>/dev/null || true
        sleep 1
        log_info "端口 $PORT 已释放"
    else
        log_info "端口 $PORT 可用"
    fi
done

# ============================================================================
# 2. 启动后端
# ============================================================================

log_step "启动后端服务..."

cd "$PROJECT_DIR/backend"

# 首次运行需要编译
if [ ! -f "qknow-server/target/qknow-server.jar" ]; then
    log_info "首次启动，正在编译后端 (约1-2分钟)..."
    mvn install -DskipTests -q
    log_info "后端编译完成"
fi

nohup java -jar qknow-server/target/qknow-server.jar > /tmp/ai-agent-backend.log 2>&1 &
BACKEND_PID=$!

# 等待后端就绪
log_info "等待后端启动..."
for i in $(seq 1 60); do
    if curl -s http://localhost:$BACKEND_PORT/login > /dev/null 2>&1; then
        log_info "后端启动成功 (PID: $BACKEND_PID, 端口: $BACKEND_PORT)"
        break
    fi
    [ $i -eq 60 ] && { log_error "后端启动超时，查看日志: /tmp/ai-agent-backend.log"; exit 1; }
    sleep 1
done

# ============================================================================
# 3. 启动前端
# ============================================================================

log_step "启动前端服务..."

cd "$PROJECT_DIR/frontend"

# 首次运行需要安装依赖
if [ ! -d "node_modules" ]; then
    log_info "首次启动，正在安装前端依赖..."
    npm install --registry=https://registry.npmmirror.com -q
    log_info "前端依赖安装完成"
fi

nohup npm run dev > /tmp/ai-agent-frontend.log 2>&1 &
FRONTEND_PID=$!

# 等待前端就绪
log_info "等待前端启动..."
for i in $(seq 1 30); do
    if curl -s http://localhost:$FRONTEND_PORT > /dev/null 2>&1; then
        log_info "前端启动成功 (PID: $FRONTEND_PID, 端口: $FRONTEND_PORT)"
        break
    fi
    [ $i -eq 30 ] && { log_error "前端启动超时，查看日志: /tmp/ai-agent-frontend.log"; exit 1; }
    sleep 1
done

# ============================================================================
# 4. 完成
# ============================================================================

echo ""
echo -e "${GREEN}=========================================="
echo -e "  启动完成!"
echo -e "==========================================${NC}"
echo ""
echo -e "  前端: http://localhost:$FRONTEND_PORT"
echo -e "  后端: http://localhost:$BACKEND_PORT"
echo -e "  Swagger: http://localhost:$BACKEND_PORT/swagger-ui.html"
echo ""
echo -e "  账号: admin / admin123"
echo ""
echo -e "  日志: /tmp/ai-agent-backend.log"
echo -e "        /tmp/ai-agent-frontend.log"
echo ""
