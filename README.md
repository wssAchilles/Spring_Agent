# AI-Native RAG 智能体编排平台

基于 Spring Boot 3 + Vue 3 + Spring AI 的企业级 AI 智能体编排平台。

## 项目结构

```
Agent/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 父 POM
│   ├── qknow-server/                 # 启动模块 (入口)
│   ├── qknow-framework/              # 框架基础 (工具类、公共组件)
│   ├── qknow-module-system/          # 系统模块 (RBAC 权限、用户管理)
│   ├── qknow-module-kb/              # 知识库模块 (RAG、Agent 配置、DAG 画布)
│   ├── qknow-module-ai/              # AI 模块 (模型市场、API Key 管理)
│   ├── qknow-module-kg/              # 知识图谱模块
│   ├── qknow-module-app/             # 应用模块
│   ├── qknow-module-dm/              # 数据管理模块
│   ├── qknow-module-ext/             # 扩展模块
│   └── qknow-module-kmc/             # 知识管理中心
│
├── frontend/                         # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── src/
│   │   ├── api/                      # 接口定义
│   │   ├── views/                    # 页面组件
│   │   ├── components/               # 公共组件
│   │   ├── router/                   # 路由配置
│   │   ├── store/                    # Pinia 状态管理
│   │   ├── utils/                    # 工具函数
│   │   └── assets/                   # 静态资源
│   └── public/
│
├── deploy/                           # 部署配置
│   ├── sql/                          # 数据库初始化脚本
│   └── docker/                       # Docker 配置
│
├── docs/                             # 项目文档
│   ├── compose/specs/                # 设计文档
│   └── *.md                          # 参考资料
│
├── plans/                            # 实施计划
├── scripts/                          # 构建/部署脚本
├── references/                       # 参考开源项目 (不参与构建)
│   ├── qKnow/                        # 原始 qKnow 项目
│   ├── spring-ai-alibaba/            # Spring AI Alibaba Agent 框架
│   ├── JeecgBoot/                    # JeecgBoot AI/RAG 参考
│   └── ruoyi-vue-pro/                # ruoyi-vue-pro RBAC 参考
│
├── docker-compose.yml                # 一键启动 (应用 + PG + Redis)
├── .gitignore
└── README.md
```

## 快速开始

### 环境要求
- JDK 17+
- Node.js 18+
- PostgreSQL 15+ (需安装 PgVector 扩展)
- Redis 7+

### 后端启动
```bash
cd backend
mvn clean install -DskipTests
cd qknow-server
mvn spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run dev
```

### Docker 一键启动
```bash
docker-compose up -d
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 / Java 17 / MyBatis-Plus / Spring AI |
| 前端 | Vue 3 / Vite / Pinia / Element Plus |
| 数据库 | PostgreSQL + PgVector / Redis |
| AI 模型 | DeepSeek (主力) / Gemini Flash (低延迟) |
| 部署 | Docker / Docker Compose |

## 架构

**双核架构**: 控制面 (Spring Boot 单体) + 认知面 (Hermes 调度内核)

- 控制面: RBAC 鉴权、RAG 知识引擎、工具注册中心
- 认知面: DAG 状态机、反思循环、ReAct 推理、AI Judge 质量护栏

## 参考项目

`references/` 目录包含用于架构参考的开源项目，不参与实际构建。
