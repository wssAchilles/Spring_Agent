# [00-env-setup-and-base-pull] 环境搭建与底座拉取

> 状态: ✅ completed | 开始: 2026-06-19T22:00:00+08:00 | 完成: 2026-06-19T23:30:00+08:00

## 目标清单

- [x] 1.1 拉取 qKnow 主底座到 references/qKnow
- [x] 1.2 拉取 spring-ai-alibaba 参考项目
- [x] 1.3 拉取 JeecgBoot 参考项目
- [x] 1.4 拉取 ruoyi-vue-pro 参考项目
- [x] 2.1 重组为标准前后端分离架构 (backend/ + frontend/)
- [x] 2.2 创建 docker-compose.yml (PostgreSQL + PgVector + Redis)
- [x] 2.3 创建项目 README.md 和 .gitignore
- [x] 3.1 建立 Agent 执行规范体系

## 关键决策

### 决策 1: 采用 qKnow 作为主底座
- **原因**: qKnow 已有完整的 Spring Boot 3 + Vue 3 + DAG 画布 + 知识库模块，可节省大量基础开发时间
- **否决方案**:
  - 从 Spring Initializr 搭建（工作量过大）
  - JeecgBoot 作为底座（框架太重，定制困难）
  - ruoyi-vue-pro 作为底座（缺少 DAG 画布和 AI 模块）

### 决策 2: 后端直接使用 qKnow 的模块化结构
- **原因**: 毕业设计 1 人开发，单体架构更务实；qKnow 模块边界已清晰划分
- **否决方案**: Spring Cloud 微服务架构（运维复杂度过高）

### 决策 3: 向量存储选择 PostgreSQL + PgVector
- **原因**: 减少运维复杂度，无需额外服务，性能够用
- **否决方案**: Milvus（需独立部署）、Qdrant（需额外服务）

## 遇到的问题

### 问题 1: qKnow SQL 使用 MySQL 语法
- **状态**: 待处理
- **解决方案**: 后续阶段需编写 PostgreSQL 版本的建表脚本，或使用 Flyway 迁移

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| backend/ | 创建 | qKnow 后端模块整体复制 |
| frontend/ | 创建 | qKnow Vue 3 前端整体复制 |
| docker-compose.yml | 创建 | PostgreSQL (PgVector) + Redis |
| README.md | 创建 | 项目说明文档 |
| .gitignore | 创建 | Git 忽略规则 |
| AGENT_GUIDELINES.md | 创建 | Agent 执行规范 |
| plans/_schema/log.schema.json | 创建 | 日志 JSON Schema |
| plans/_templates/ | 创建 | 日志模板 |

## 技术笔记

### qKnow 项目结构
- 后端采用 Maven 多模块结构，模块粒度清晰
- `qknow-module-kb` 有 112 个 Java 文件，是最大的模块（知识库 + Agent + DAG）
- `qknow-module-ai` 有 29 个 Java文件（模型市场 + API Key 管理）
- 前端使用 Vue 3 + Vite + Pinia + Element Plus

### Spring AI Alibaba 参考价值
- `spring-ai-alibaba-agent-framework` — 核心 Agent 框架
- `spring-ai-alibaba-graph-core` — DAG 图执行引擎（Hermes 内核参考）
- `AgentScope` 集成 — 路由、流、合并节点（多 Agent 协同参考）

## 风险提示

- MySQL → PostgreSQL 语法迁移可能存在兼容性问题，需仔细比对
- Spring AI 版本与 qKnow 现有 Spring Boot 版本可能有冲突，需在阶段一验证
- qKnow 的许可证为 Apache 2.0，衍生版本不得移除默认 LOGO 和版权信息

## 下一阶段预研

- [ ] 分析 qKnow 的 SQL 脚本，编写 PostgreSQL + PgVector 迁移版本
- [ ] 调研 Spring AI 与 Spring Boot 3.2 的版本兼容性
- [ ] 确定 Embedding 模型选型（BGE-large-zh vs Spring AI 内置）
- [ ] 验证 DeepSeek API 的 Spring AI 适配方式
- [ ] 确认 PgVector 扩展的安装与配置方式
