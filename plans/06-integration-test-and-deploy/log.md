# 06-integration-test-and-deploy 集成测试与部署

> 状态: completed | 开始: 2026-06-20T15:00:00+08:00 | 完成: 2026-06-20T17:00:00+08:00

## 目标清单

- [x] 6.1 Docker 容器化 + 一键启动
- [x] 6.2 项目文档 + README 更新

## 关键决策

### 决策 1: 使用 Nginx 作为前端服务和 API 代理
- **原因**: 性能优秀，支持 SSE 代理
- **否决方案**: Vite dev server、Spring Boot 静态文件

### 决策 2: Docker Compose 四服务部署
- **原因**: 一键启动，降低部署门槛
- **否决方案**: 只提供基础设施、Kubernetes

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| backend/Dockerfile | 新建 | 后端 Docker 镜像 |
| frontend/Dockerfile | 新建 | 前端 Docker 镜像 |
| deploy/docker/nginx.conf | 新建 | Nginx 配置 |
| docker-compose.yml | 修改 | 新增 Backend + Frontend |
| scripts/start.sh | 新建 | 一键启动脚本 |
| README.md | 修改 | 更新项目文档 |

## 部署说明

### Docker 一键启动
```bash
docker-compose up -d
```

### 访问地址
- 前端: http://localhost
- 后端 API: http://localhost:8099
- Swagger: http://localhost:8099/swagger-ui.html

### 默认账号
- 用户名: admin
- 密码: admin123

## 项目完成总结

本项目已完成全部 6 个阶段的开发:

| 阶段 | 核心内容 | 文件数 |
|------|---------|--------|
| 01 | MySQL→PostgreSQL + Weaviate→PgVector | 17 |
| 02 | 对话管理 + 历史集成 + 前端 UI | 19 |
| 03 | 内置工具 + MCP + 权限系统 | 9 |
| 04 | DAG 拓扑排序 + 条件网关 + 并行执行 | 6 |
| 05 | Hermes 内核 + AI Judge + 质量面板 | 9 |
| 06 | Docker 容器化 + 文档 | 6 |

**累计: 66 个文件变更，7 次 Git 提交**
