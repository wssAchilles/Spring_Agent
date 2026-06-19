# AI-Native RAG 智能体编排平台

基于 Spring Boot 3 + Vue 3 + Spring AI 的企业级 AI 智能体编排平台，采用"控制面 + 认知面"双核架构。

## 核心功能

- **RAG 知识引擎**: 文档上传 → 语义切块 → 向量化 → PgVector 存储 → 混合检索
- **智能体装配车间**: Agent 配置、多模型支持、对话历史管理
- **Tool Calling 工具箱**: 内置工具 (HTTP/搜索/天气)、MCP 协议适配、权限控制
- **DAG 工作流编排**: 拓扑排序、条件网关、并行执行、状态监控
- **Hermes 认知内核**: 反思循环 (生成→评估→修正)、AI Judge 三维评分

## 项目结构

```
Agent/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 父 POM
│   ├── Dockerfile                    # 后端 Docker 镜像
│   ├── qknow-server/                 # 启动模块 (入口)
│   ├── qknow-framework/              # 框架基础
│   │   ├── qknow-ai/                 # AI 框架 (Embedding, VectorStore, ChatClient)
│   │   ├── qknow-common/             # 公共工具类
│   │   ├── qknow-mybatis/            # MyBatis-Plus 配置
│   │   ├── qknow-redis/              # Redis 配置
│   │   └── qknow-security/           # 安全框架
│   ├── qknow-module-kb/              # 知识库模块 (Agent, Flow, Tool, Judge)
│   ├── qknow-module-ai/              # AI 模块 (模型市场, API Key)
│   ├── qknow-module-kmc/             # 知识管理中心 (文档, 分段, 向量)
│   └── qknow-module-system/          # 系统模块 (RBAC)
│
├── frontend/                         # Vue 3 前端
│   ├── Dockerfile                    # 前端 Docker 镜像
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                      # 接口定义
│       ├── views/                    # 页面组件
│       ├── components/               # 公共组件
│       ├── router/                   # 路由配置
│       └── store/                    # Pinia 状态管理
│
├── deploy/                           # 部署配置
│   ├── sql/postgresql/               # PostgreSQL 初始化脚本
│   └── docker/nginx.conf             # Nginx 配置
│
├── docs/                             # 项目文档
├── plans/                            # 实施计划 (6 阶段)
├── docker-compose.yml                # 一键启动
└── README.md
```

## 快速开始

### 方式一: Docker 一键启动 (推荐)

```bash
# 克隆项目
git clone https://github.com/wssAchilles/Spring_Agent.git
cd Spring_Agent

# 启动所有服务
docker-compose up -d

# 访问
# 前端: http://localhost
# 后端 API: http://localhost:8099
# Swagger: http://localhost:8099/swagger-ui.html
```

### 方式二: 本地开发

**环境要求:**
- JDK 17+
- Node.js 18+
- PostgreSQL 15+ (需安装 PgVector 扩展)
- Redis 7+

**后端:**
```bash
# 启动数据库
docker-compose up -d postgres redis

# 编译后端
cd backend
mvn clean install -DskipTests

# 启动
cd qknow-server
mvn spring-boot:run
```

**前端:**
```bash
cd frontend
npm install
npm run dev
```

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3.5 / Java 17 |
| AI 框架 | Spring AI 1.1 / Spring AI Alibaba 1.1 |
| ORM | MyBatis-Plus |
| 前端框架 | Vue 3 / Vite / Pinia / Element Plus |
| 向量存储 | PostgreSQL + PgVector |
| 缓存 | Redis 7 |
| AI 模型 | DeepSeek (主力) / Gemini Flash (低延迟) |
| 部署 | Docker / Docker Compose |

## 架构设计

### 双核架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                          │
│            知识库管理 / Agent 配置 / DAG 画布              │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP / SSE
┌────────────────────────┴────────────────────────────────┐
│                 控制面 (Spring Boot)                      │
│  RBAC 鉴权 │ RAG 引擎 │ 工具注册 │ DAG 编排 │ API 网关    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│                 认知面 (Hermes 内核)                      │
│  反思循环 │ ReAct 推理 │ Tool Calling │ AI Judge 评分     │
└─────────────────────────────────────────────────────────┘
```

### 核心模块

| 模块 | 功能 |
|------|------|
| RAG 知识引擎 | 文档上传、语义切块、向量化、混合检索 |
| 智能体装配车间 | Agent 配置、对话管理、历史集成 |
| 工具箱 | 内置工具、MCP 适配、权限控制 |
| DAG 工作流 | 拓扑排序、条件网关、并行执行 |
| Hermes 内核 | 反思循环、AI Judge 三维评分 |

## API 文档

启动后端后访问: http://localhost:8099/swagger-ui.html

主要 API 分组:
- `system` - 系统管理 (用户、角色、菜单)
- `kb` - 知识库 (Agent、Flow、Tool、Conversation、Judge)
- `kmc` - 知识管理 (知识库、文档、分段)
- `ai` - AI 模型 (模型市场、API Key)

## 数据库

使用 PostgreSQL + PgVector 扩展，初始化脚本位于 `deploy/sql/postgresql/`:

1. `00-init-extensions.sql` - 启用扩展 (vector, pg_trgm)
2. `01-schema.sql` - 建表脚本 (50+ 表)
3. `02-init-data.sql` - 初始数据 (admin 用户、角色、字典)

## 开发日志

| 阶段 | Commit | 内容 |
|------|--------|------|
| 01 | `fecb061` | MySQL→PostgreSQL + Weaviate→PgVector |
| 02 | `f0605a1` | 对话管理 + 历史集成 + 前端 UI |
| 03 | `870aebd` | 内置工具 + MCP + 权限系统 |
| 04 | `25b5268` | DAG 拓扑排序 + 条件网关 + 并行执行 |
| 05 | `90b38f1` | Hermes 内核 + AI Judge + 质量面板 |
| 06 | - | 集成测试与部署 |

## 许可证

基于 qKnow 开源项目衍生，遵循 Apache License 2.0。
