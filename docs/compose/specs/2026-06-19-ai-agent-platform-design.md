# AI-Native RAG 架构与智能体编排平台 — 设计文档

## [S1] 项目概述

构建一个企业级 AI-Native RAG 架构与智能体编排平台，基于 Spring Boot 3 + Vue 3 + Spring AI 技术栈。平台支持知识库管理、智能体配置、工具调用、工作流编排四大核心模块，采用"控制面 + 认知面"双核架构，集成 AI Judge 质量护栏系统。

**项目性质**: 毕业设计（1人，6个月+）
**底座项目**: qKnow (qiantongtech/qKnow)
**AI 模型**: DeepSeek（主力）+ Gemini Flash（低延迟场景）
**架构风格**: 模块化单体 + 独立认知面微服务

## [S2] 双核架构

### 控制面 (Spring Boot 3 单体)
- RBAC 权限鉴权
- RAG 知识引擎（文档解析、语义切块、向量检索、BM25 混合检索）
- 工具注册中心（Function Calling / MCP 协议适配）
- API 网关层（统一鉴权、请求路由）

### 认知面 (Hermes 调度内核)
- DAG 状态机执行引擎
- 反思循环 (Reflection)
- ReAct 推理与行动范式
- 多步 Tool Calling 动态调度
- AI Judge 三维评分系统（事实性、相关性、指令遵循度）

### 通信方式
- 控制面 → 认知面: 内部 HTTP/gRPC
- 控制面 → 前端: SSE 流式推送

## [S3] 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3.2+ / Java 17 |
| AI 框架 | Spring AI / Spring AI Alibaba |
| ORM | MyBatis-Plus |
| 前端框架 | Vue 3 + Vite + Pinia + Element Plus |
| DAG 画布 | qKnow 内置画布组件 |
| 向量存储 | PostgreSQL + PgVector 扩展 |
| 缓存 | Redis |
| AI 模型 | DeepSeek (主力) + Gemini Flash (低延迟) |
| 认知内核 | Hermes (Spring AI Agent 或 Rust 独立服务) |
| 部署 | Docker + Docker Compose |

## [S4] 模块一：RAG 知识引擎

### 功能
- 多格式文档上传（PDF、Word、Markdown）
- 语义智能切块 (Semantic Chunking)
- 向量化嵌入（BGE-large-zh 或 Spring AI 内置 Embedding）
- PgVector 向量存储与余弦相似度检索
- BM25 关键词检索 + 稠密向量检索混合架构
- Reranker 重排模型二次打分

### 数据流
文档上传 → 文本抽取清洗 → 语义切块 → Embedding 向量化 → PgVector 存储
用户提问 → 问题向量化 → 混合检索召回 → Reranker 重排 → 注入 Prompt

## [S5] 模块二：智能体装配车间

### 功能
- Agent 创建与配置（名称、人设 Prompt、绑定知识库）
- 动态模型切换（DeepSeek / Gemini Flash）
- Agent 版本管理
- 系统提示词模板管理

## [S6] 模块三：工具箱集成 (Tool Calling)

### 功能
- Java 方法注册为标准工具（函数注册中心）
- LLM 意图解析 → 自动选择工具 → 提取参数 → 执行
- MCP 协议适配（对接外部观测云、数据库、代码仓库等）
- 工具执行结果回传 Agent

## [S7] 模块四：工作流编排 (DAG)

### 功能
- 基于 qKnow DAG 画布的可视化编排
- Agent 节点、工具节点、条件网关节点拖拽连线
- DAG 引擎按拓扑序执行，上一节点输出 → 下一节点输入
- 并行网关支持多 Agent 协同
- 工作流运行状态实时监控

## [S8] AI Judge 质量护栏

### 评分维度
- **事实性 (Factuality)**: 生成内容是否有检索到的知识支撑
- **相关性 (Relevance)**: 回答是否切题
- **指令遵循度 (Instruction Following)**: 是否遵循 Agent 人设和任务要求

### 工作机制
- 阈值配置化（默认 0.7/1.0）
- 评分不达标 → 打回 Hermes 内核重试（最多 3 次）
- 评分日志记录，支持质量分析面板

## [S9] 前端设计

- 基于 qKnow 的 Vue 3 + Element Plus 框架
- 核心页面：知识库管理、Agent 配置、对话测试、DAG 画布编排
- SSE 流式渲染（打字机效果 + Markdown 渲染 + 代码高亮）
- 响应式布局

## [S10] 数据库设计（核心表）

- `knowledge_base` — 知识库表
- `document` — 文档表
- `document_chunk` — 文档切块表（含向量字段）
- `agent` — 智能体配置表
- `agent_knowledge` — Agent-知识库关联表
- `tool` — 工具注册表
- `conversation` — 对话表
- `message` — 消息表
- `workflow` — 工作流定义表
- `workflow_node` — 工作流节点表
- `workflow_edge` — 工作流边表
- `workflow_run` — 工作流运行记录表
- `ai_judge_score` — AI 评分记录表
