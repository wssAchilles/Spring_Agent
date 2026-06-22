# 本机开发模式设计

## 目标

将所有应用服务从 Docker 迁移到 macOS 本机运行，并保证代码修改可以自动生效：

- 前端使用 Vite HMR。
- 主后端 `qknow-server` 使用 Spring Boot DevTools 自动重启。
- Hermes 服务使用 Spring Boot DevTools 自动重启。
- PostgreSQL 与 Redis 继续作为基础设施容器运行。

## 运行架构

开发环境分为两层：

1. 应用层在本机运行：
   - 前端监听 `80` 端口。
   - `qknow-server` 监听 `8099` 端口。
   - Hermes gRPC 服务监听 `9090` 端口。
2. 基础设施层在 Docker 运行：
   - PostgreSQL 暴露 `5432` 端口。
   - Redis 暴露 `6379` 端口。

本机服务统一通过 `localhost` 访问其他服务，不再使用 Compose 服务名 `backend`、`hermes`、`postgres` 或 `redis`。

## 热重启机制

### 前端

使用 `npm run dev` 启动 Vite。Vue、JavaScript、样式和静态资源修改通过 HMR 注入浏览器；Vite 配置变化由 Vite 自动重启开发服务器。

### 主后端与 Hermes

为两个 Spring Boot 启动模块启用 `spring-boot-devtools`，通过 Maven 的 `spring-boot:run` 启动。源代码重新编译后由 DevTools 重建应用上下文。开发脚本负责启动持续编译或对应的 Maven 开发进程，不使用预构建 JAR。

## 开发脚本

提供三个职责单一的入口：

- 启动：检查 PostgreSQL/Redis，清理遗留的应用容器与本机端口进程，启动三个本机应用服务并等待健康状态。
- 停止：只停止由开发脚本启动的本机应用进程，不停止 PostgreSQL/Redis。
- 状态：显示三个应用服务、两个基础设施服务、端口和日志位置。

进程 PID 和日志放在项目忽略的运行目录中，避免误杀无关进程，也避免污染 Git 状态。

## Compose 边界

默认 Compose 文件只保留 PostgreSQL 和 Redis。前端、主后端、Hermes 不再由 Compose 创建。切换开发模式时停止并移除现有的三个应用容器，但保留数据库卷和 Redis 数据卷。

## 配置与通信

- 前端开发 API 基址使用 Vite 代理，由 `/dev-api` 转发至 `http://localhost:8099`。
- 主后端连接 `localhost:5432` 和 `localhost:6379`。
- 主后端访问 Hermes 使用 `localhost:9090`。
- Hermes 控制面地址使用 `http://localhost:8099`。
- 开发配置不得依赖 Docker DNS。

## 错误处理

- 基础设施未就绪时启动脚本立即失败并给出具体服务和端口。
- 任一应用服务启动超时时，保留日志并返回非零状态。
- 端口被非本项目进程占用时不强制杀死，报告 PID 后停止启动。
- 自动重启失败时，原始编译错误保留在对应日志中。

## 验证标准

1. Docker 中不存在 frontend、backend、Hermes 应用容器。
2. PostgreSQL 与 Redis 容器保持健康，数据卷不被删除。
3. 首页、登录、`/getInfo`、`/getRouters` 和 Hermes gRPC 健康检查可用。
4. 修改一个 Vue 文本后浏览器无需手工重启服务即可更新。
5. 修改主后端代码后进程 PID 保持由开发工具管理，接口返回新行为。
6. 修改 Hermes 代码后 gRPC 服务自动重启并恢复健康。
7. 启动、停止、状态脚本可重复执行。

## 非目标

- 不将 PostgreSQL 或 Redis 安装到 macOS 本机。
- 不修改生产部署方式。
- 不重构业务模块。
- 不删除现有数据库或 Redis 数据。
