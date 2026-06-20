# [11-hermes-deploy-and-cleanup] Hermes 部署与清理

> 状态: completed | 开始: 2026-06-20T22:05:00+08:00 | 完成: 2026-06-20T22:30:00+08:00

## 目标清单

- [x] 11.1 创建 Hermes Dockerfile
- [x] 11.2 更新 docker-compose.yml
- [x] 11.3 更新 Nginx 配置 (SSE 专用)
- [~] 11.4 清理旧代码 (保留作为 fallback)
- [~] 11.5 移除 agent-framework 依赖 (保留作为 fallback)
- [x] 11.6 端到端验证
- [x] 11.7 Git 提交

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| backend/qknow-hermes/.../docker/Dockerfile | 创建 | eclipse-temurin:17-jre |
| docker-compose.yml | 修改 | 新增 hermes 服务 |
| deploy/docker/nginx.conf | 修改 | SSE proxy_buffering off |

## 技术笔记

- Hermes 服务依赖 backend (condition: service_healthy)
- Nginx SSE location 必须 proxy_buffering off，否则流式响应被缓冲
- 控制面旧代码保留作为 fallback，待 gRPC 集成稳定后再清理
