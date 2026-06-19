#!/bin/bash
# AI-Native RAG 智能体编排平台 - 一键启动脚本

set -e

echo "=========================================="
echo "  AI-Native RAG 智能体编排平台"
echo "=========================================="

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误: 未安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未安装 Docker Compose"
    exit 1
fi

# 启动服务
echo "正在启动服务..."
docker-compose up -d

echo ""
echo "=========================================="
echo "  启动完成!"
echo "=========================================="
echo ""
echo "前端访问: http://localhost"
echo "后端 API: http://localhost:8099"
echo "Swagger:  http://localhost:8099/swagger-ui.html"
echo ""
echo "默认账号: admin / admin123"
echo ""
echo "查看日志: docker-compose logs -f"
echo "停止服务: docker-compose down"
echo ""
