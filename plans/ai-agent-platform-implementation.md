# AI-Native RAG 智能体编排平台 — 分阶段实施计划

## 项目信息
- **底座**: qKnow (qiantongtech/qKnow)
- **技术栈**: Spring Boot 3 + Vue 3 + Spring AI + PgVector
- **架构**: 控制面(Java 单体) + 认知面(Hermes 内核)
- **预计周期**: 6 个月（6 个阶段）

---

## 阶段零：环境搭建与底座拉取（第 1 周）

### 任务 0.1: 拉取 qKnow 主底座
- `git clone https://github.com/qiantongtech/qKnow.git`
- 阅读 README，理解项目结构
- 本地启动前后端，验证可用

### 任务 0.2: 拉取参考项目
- `git clone https://github.com/alibaba/spring-ai-alibaba.git` (Agent 框架参考)
- `git clone https://github.com/jeecgboot/JeecgBoot.git` (AI/RAG 集成参考)
- `git clone https://github.com/YunaiV/ruoyi-vue-pro.git` (RBAC/工作流参考)

### 任务 0.3: 本地开发环境
- JDK 17+, Node.js 18+, Maven/Gradle
- PostgreSQL 15+ 安装 + PgVector 扩展
- Redis 安装
- Docker Desktop (可选)

### 任务 0.4: 项目初始化
- 基于 qKnow 创建新项目仓库
- 配置 `.gitignore`, `application.yml`
- 验证前后端联调正常

---

## 阶段一：RAG 知识引擎核心（第 2-4 周）

### 任务 1.1: 数据库表设计与迁移
- 设计 knowledge_base, document, document_chunk 表
- 使用 Flyway 管理数据库迁移
- 实现 MyBatis-Plus Mapper 和 Service

### 任务 1.2: 文档上传与解析
- 实现文件上传接口（支持 PDF、Word、Markdown）
- 集成 Apache Tika 或 pdf.js 进行文本抽取
- 文件存储（本地或 MinIO）

### 任务 1.3: 语义切块 (Semantic Chunking)
- 实现基于语义的文本分块算法
- 按段落/句子边界切块，保持上下文完整
- 每个 chunk 保留元数据（来源文档、页码、位置）

### 任务 1.4: 向量化与存储
- 集成 Spring AI 的 Embedding 模型
- 使用 Spring AI 的 PgVectorStore 进行向量存储
- 实现文档上传后自动向量化 pipeline

### 任务 1.5: RAG 检索与问答
- 实现向量相似度检索（余弦距离）
- 实现 BM25 关键词检索（可选，使用 PostgreSQL 全文检索）
- 混合检索 + Reranker 重排
- 将检索结果注入 Prompt，调用 DeepSeek 生成回答

### 任务 1.6: SSE 流式响应
- 后端实现 SseEmitter 流式推送
- 前端实现 Fetch ReadableStream 读取 + 打字机渲染
- Markdown 渲染 + 代码高亮

---

## 阶段二：智能体装配车间（第 5-7 周）

### 任务 2.1: Agent 数据模型
- 设计 agent, agent_knowledge 表
- 实现 Agent CRUD 接口
- Agent 与知识库多对多关联

### 任务 2.2: Agent 配置前端
- Agent 创建/编辑表单（名称、人设 Prompt、模型选择）
- 知识库绑定选择器
- Prompt 模板管理

### 任务 2.3: Agent 对话引擎
- 实现 Agent 会话管理（conversation, message 表）
- 系统提示词 + 绑定知识库检索结果 + 用户消息 组装
- 多轮对话上下文管理
- 对话历史查询

### 任务 2.4: 多模型支持
- 通过 Spring AI 统一接口支持 DeepSeek 和 Gemini
- 模型配置化（API Key、Endpoint、模型名）
- Agent 级别的模型选择

---

## 阶段三：工具箱集成 — Tool Calling（第 8-10 周）

### 任务 3.1: 工具注册中心
- 设计 tool 表（工具名、描述、参数 Schema、实现类）
- 实现工具注册/注销接口
- 内置工具：HTTP 请求、数据库查询、邮件发送

### 任务 3.2: Function Calling 集成
- 利用 Spring AI 的 Function Calling 能力
- 将注册的工具转换为 Spring AI 的 FunctionCallback
- Agent 配置中绑定可用工具列表

### 任务 3.3: ReAct 推理循环
- 实现 Reasoning and Acting 循环
- LLM 分析意图 → 选择工具 → 执行 → 观察结果 → 继续推理
- 工具调用链路追踪与日志

### 任务 3.4: MCP 协议适配（扩展）
- 研究 Model Context Protocol 规范
- 实现基础 MCP Client，对接外部服务
- 工具发现与动态注册

---

## 阶段四：工作流编排 — DAG 画布（第 11-14 周）

### 任务 4.1: DAG 数据模型
- 设计 workflow, workflow_node, workflow_edge 表
- 工作流 CRUD 接口
- 节点类型定义（Agent 节点、工具节点、条件网关、开始/结束）

### 任务 4.2: DAG 画布前端
- 基于 qKnow 画布组件扩展
- 节点拖拽、连线、删除
- 节点属性配置面板
- 画布保存/加载

### 任务 4.3: DAG 执行引擎
- 拓扑排序算法
- 按拓扑序执行节点
- 上一节点输出 → 下一节点输入的数据传递
- 条件网关路由逻辑

### 任务 4.4: 并行执行与状态监控
- 并行网关支持（多 Agent 同时执行）
- workflow_run 运行记录表
- 前端实时展示工作流执行状态

---

## 阶段五：Hermes 认知内核 + AI Judge（第 15-18 周）

### 任务 5.1: Hermes 内核设计
- 基于 Spring AI 的 Agent 能力构建反思循环
- 实现 Reflection 机制（生成 → 自我评估 → 修正）
- 长上下文状态机管理

### 任务 5.2: AI Judge 三维评分系统
- 设计 ai_judge_score 表
- 实现事实性、相关性、指令遵循度三维评分
- 阈值配置化
- 评分不达标 → 自动重试机制（最多 3 次）

### 任务 5.3: 控制面与认知面对接
- 控制面组装 Prompt + RAG 结果 → 发送至认知面
- 认知面执行 → AI Judge 评分 → 返回结果
- 通信协议设计（HTTP 或 gRPC）

### 任务 5.4: 质量分析面板
- AI 评分历史查询
- 评分趋势图表
- 失败案例分析

---

## 阶段六：集成测试与部署（第 19-22 周）

### 任务 6.1: 端到端集成测试
- 完整流程测试：上传文档 → 创建 Agent → 绑定知识库 → 对话问答
- 工作流端到端测试：创建 DAG → 配置节点 → 执行 → 查看结果
- AI Judge 质量验证

### 任务 6.2: 性能优化
- 向量检索性能调优（索引优化、缓存）
- SSE 连接管理优化
- 数据库查询优化

### 任务 6.3: Docker 容器化
- Dockerfile 编写（后端、前端）
- docker-compose.yml（应用 + PostgreSQL + PgVector + Redis）
- 一键启动脚本

### 任务 6.4: 文档与答辩准备
- API 接口文档（Swagger/SpringDoc）
- 项目 README
- 答辩 PPT 准备
- 演示 Demo 录制

---

## 开源项目依赖清单

| 项目 | 用途 | 克隆命令 |
|------|------|---------|
| qKnow | 主底座（DAG 画布 + 前端框架） | `git clone https://github.com/qiantongtech/qKnow.git` |
| spring-ai-alibaba | Agent 框架参考 | `git clone https://github.com/alibaba/spring-ai-alibaba.git` |
| JeecgBoot | AI/RAG 集成参考 | `git clone https://github.com/jeecgboot/JeecgBoot.git` |
| ruoyi-vue-pro | RBAC/工作流参考 | `git clone https://github.com/YunaiV/ruoyi-vue-pro.git` |

## 里程碑

| 阶段 | 里程碑 | 预计完成 |
|------|--------|---------|
| 阶段零 | 环境就绪，底座运行 | 第 1 周末 |
| 阶段一 | RAG 知识库可问答 | 第 4 周末 |
| 阶段二 | Agent 可配置可对话 | 第 7 周末 |
| 阶段三 | Agent 可调用外部工具 | 第 10 周末 |
| 阶段四 | DAG 工作流可编排可执行 | 第 14 周末 |
| 阶段五 | AI Judge 质量护栏上线 | 第 18 周末 |
| 阶段六 | 项目交付，答辩就绪 | 第 22 周末 |
