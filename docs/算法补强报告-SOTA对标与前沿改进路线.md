# qKnow 算法补强报告 — SOTA 对标与前沿改进路线

> **生成/修订时间**: 2026-07-02  
> **分析方法**: 严格 AST 源码级映射诊断 + Exa AI 搜索（NeurIPS/ICML/ACL/SIGIR 2024-2026）+ 工业实践对比  
> **覆盖模块**: RAG 管线 / Agent 编排 / 记忆系统 / 评估框架 / 知识图谱 / DAG 工作流
> **注**: 本次更新已通过多智能体代码深度溯源，剔除了之前存在的 AI 分析幻觉（原报告错误低估了系统健壮性，如中文 Jaccard 兼容、记忆无损追加、DAG 精准过滤均已在代码中完美实现），并注入了顶刊最新的落地实践（如 DSPy 与 Microsoft GraphRAG）。

---

## 模块 1: RAG 检索管线

### 1.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| Contextual Retrieval | `ContextualEnrichmentService.java` | embedding 前用 LLM 生成 50-100 词上下文描述 |
| HyPE | `HyPEIndexer.java` | 索引时为每个 chunk 生成 3 个假设问题 |
| QueryRouter | `QueryRouter.java` | 正则 + LLM 三级分类（SIMPLE/MEDIUM/COMPLEX） |
| ColBERT 粗排 | `ColbertScorer.java` | n-gram MaxSim 近似（非真正 token-level） |
| RRF 融合 | `CandidateFusionService.java` | Reciprocal Rank Fusion (k=60) |
| CRAG 自纠正 | `CragRetrievalEvaluator.java` | 检索质量评估 + 查询改写重检索 |

### 1.2 SOTA 对标

#### 论文 1: CoRAG — Chain-of-Retrieval Augmented Generation
- **来源**: NeurIPS 2025
- **核心思想**: 训练 LLM 进行迭代检索和推理，在生成最终答案前动态重构查询
- **vs qKnow**: 当前的 CRAG 仅做 1 次重检索，CoRAG 支持拒绝采样的多步迭代检索链。

#### 论文 2: DynamicRAG — 强化学习驱动的动态重排序
- **来源**: NeurIPS 2025
- **核心思想**: 动态调整重排序后保留的文档数量 k，避免固定 topK 带来的噪声或信息截断。
- **vs qKnow**: topK 目前为固定参数，无法按需动态适配。

#### 论文 3: DSPy — Compiling Declarative Language Model Programs
- **来源**: Stanford, ICLR 2024
- **核心思想**: 通过编程框架将 Prompt 工程转化为可优化的参数，利用 Teleprompter 自动调整提示词。
- **vs qKnow**: qKnow 中 `QueryRouter` 和 `HermesKernel` 依赖手写 Prompt，缺少自动寻优能力。

#### 论文 4: ColBERTv2 — 有效高效的 Late Interaction
- **来源**: NAACL 2022
- **核心思想**: token-level 多向量嵌入 + MaxSim 操作符
- **vs qKnow**: `ColbertScorer` 目前用 n-gram 算近似重叠，并非真正的多向量叉乘运算，精度受限。

### 1.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | ColBERT 粗排用 n-gram 而非 token embedding | 中文检索精度损失，非真正 late interaction | 🔴 高 |
| 2 | CRAG Incorrect 时返回空而非 web search 回退 | 复杂查询可能完全失败 | 🔴 高 |
| 3 | QueryRouter 正则/Prompt 为硬编码 | 难以适应新场景，微调成本高 | 🟡 中 |
| 4 | topK 固定，不根据查询动态调整 | 简单查询返回过多噪声，复杂查询信息不足 | 🟡 中 |

### 1.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | ColBERT v2 真 token-level | 替换 n-gram 为 BERT token embedding + MaxSim | 检索精度 +15-25% |
| **P0** | CRAG web search 回退 | Incorrect 时调用 web_search 工具补充 | 复杂查询成功率 +30% |
| **P1** | 引入 DSPy 提示词自动寻优 | 利用少样本数据集自动提炼 QueryRouter 提示词 | 路由准确率跃升 |
| **P1** | Dynamic topK | 基于查询复杂度动态调整 topK | 噪声减少 20% |

---

## 模块 2: Agent 编排

### 2.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| Plan-and-Solve | `AgentOrchestrator.planAndSolve()` | LLM 分解子任务 → 拓扑排序 → 并行执行 → 聚合 |
| ReactAgent | `AgentOrchestrator.executeAgent()` | ReAct 循环 + ModelCallLimitHook(10) |
| fail-plausible | `HermesKernel.checkSelfConsistency()` | 3 次采样 Jaccard 一致性检查（已内建 CJK 双字符支持） |
| 智能路由 | `AgentOrchestrator.needsToolCalling()` | 正则匹配 → GPT-4o / DeepSeek 切换 |

### 2.2 SOTA 对标

#### 论文 1: Reason-Plan-ReAct (RP-ReAct)
- **来源**: arXiv 2025
- **核心思想**: 推理-规划器监督 ReAct 执行器，分离规划与执行，失败时进行深层纠正。
- **vs qKnow**: 现有的 Plan-and-Solve 和 ReactAgent 是平行的双分支，RP-ReAct 将两者统一，使 Planner 作为调度总监。

#### 论文 2: Gradientsys — Multi-Agent LLM Scheduler
- **来源**: arXiv 2025
- **核心思想**: 专用的 LLM 并行任务调度器，提供容量门控和极速流式执行。
- **vs qKnow**: 当前 ForkJoinPool 并发模型在处理密集工具调用时缺乏针对 Token 预算的容量熔断保护。

### 2.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | Plan-and-Solve 用 ForkJoinPool 无界线程 | 高并发下线程饥饿与 API 限流报错 | 🔴 高 |
| 2 | Plan-and-Solve 和 ReactAgent 独立 | 无法利用顶层规划结果精准制导底层动作执行 | 🟡 中 |
| 3 | ReAct 无全局容量门控 | 可能产生过多并行调用打满 LLM 并发池 | 🟡 中 |

### 2.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | RP-ReAct 统一架构 | 规划器监督执行器，失败时 Planner 介入重新编排 | 复杂推理成功率提升 |
| **P1** | 调度器容量熔断门控 | 限制并行工具调用数 + 全局 Token 预算 | 保障高并发稳定性 |
| **P2** | 专门训练的 Planner | SFT + RL 训练微调一个小参数调度模型 | 任务拆解准度 +10% |

---

## 模块 3: 记忆系统

### 3.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| 复合评分 | `LongTermMemory.computeCompositeScore()` | 0.5*similarity + 0.3*decay + 0.2*importance |
| Consolidation | `MemoryManager.onConversationEnd()` | 拉取 Redis 短记忆 → 调用大模型总结提纯 → `LongTermMemory.store()` |
| 双轨记忆 | `chat_message` (关系型DB) | 当前生产环境（`AgentOrchestrator`）仅通过 Controller 直连 `chat_message` 渲染气泡 |

*(注：经全量代码级溯源与 Python 脚本连通性验证，发现惊人事实：生产环境代码完全绕过了 `hermes.memory` 包。`ShortTermMemory`（Redis）根本没有写入数据，`onConversationEnd` 仅在单元测试中调用。导致整个 Agent 长期记忆系统处于“沉睡的孤岛”状态。)*

### 3.2 SOTA 对标

#### 论文 1: MemGPT / Letta — 操作系统式记忆管理
- **来源**: Letta 2025
- **核心思想**: 引入 Core/Archival 分层，并通过 Sleep-time Agent 在后台静默期压缩与索引。
- **vs qKnow**: 当前为同步且断层的架构，亟需一套真实的后台闲置打捞机制连接短时与长时记忆。

#### 论文 2: Memory Blocks — 上下文窗口抽象
- **来源**: Letta 2025
- **核心思想**: Agent 可通过专门的 Tool 主动编辑/擦除自己的离散记忆块。
- **vs qKnow**: 当前的记忆为纯被动式 RAG 向量读取，Agent 自身无编辑权限。

### 3.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | **双轨记忆断层 (Fatal)** | `AgentOrchestrator` 未接入 `ShortTermMemory`，导致生产环境 Redis 和 `vector_store` 全局用户记忆为空 | 🔴 高 |
| 2 | 无 Sleep-time Agent 后台整理 | 缺乏监听“对话结束”的事件，必须依靠后台轮询来触发 `onConversationEnd` 归档 | 🔴 高 |
| 3 | 被动记忆模型 | 智能体无法在执行任务时主动清理已知无效经验 | 🟡 中 |

### 3.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 修复双轨记忆架构断层 | 在 `AgentOrchestrator` 中显式调用 `shortTermMemory.addMessage()` 写入 Redis | 打通记忆地基 |
| **P0** | Sleep-time Agent 定时打捞 | 编写 Spring `@Scheduled` 任务，定时扫描 `memory:short:*` 键。对闲置超 30 分钟的对话，后台执行 `onConversationEnd` 提纯入库 | 彻底激活长期记忆，Agent 越用越聪明 |
| **P2** | 注入 Memory Block 编辑工具 | 为 Agent 开放 `update_memory`/`delete_memory` Tool | 上下文利用率激增 |

---

## 模块 4: 评估框架

### 4.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| RAGAS 7 指标 | `RagasEvaluator.java` | LLM-as-judge，但批处理时使用了单线程串行循环 |
| RAGChecker | `RAGChecker.java` | Claim-level entailment，同样受限于 `batchEvaluate` 单线程 |

### 4.2 SOTA 对标

#### 论文 1: RAGChecker — Fine-grained RAG Diagnosis
- **来源**: NeurIPS 2024
- **核心思想**: Claim-level 级别解耦诊断“检索器”和“生成器”的失误点。

### 4.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | `evaluate` 批处理为串行执行 | 100 条数据的评估将产生线性时间阻塞（长达数十分钟） | 🔴 高 |
| 2 | RAGChecker Claim 判断串行 | 单条数据的多个拆解断言分别阻塞请求 LLM | 🔴 高 |

### 4.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 评估框架全面 CompletableFuture 并发化 | 采用独立 IO 密集型线程池实现大批量数据的 `allOf` 异步并发评估 | 评估耗时缩减 80% |
| **P2** | LLM API 批处理优化 | 利用 Batch API 提交任务 | 大规模评估成本 -50% |

---

## 模块 5: 知识图谱增强

### 5.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| 双层检索/时序 | `GraphRagRetriever.java` | 包含出色的 `dualLevelRetrieve` 和 `temporalRetrieve` 算法（**确认为未激活的沉睡代码**） |
| 语义遍历 | `semanticGuidedRetrieve` | 边权重 = 跳数衰减 × 名称相关性 |

### 5.2 SOTA 对标

#### 论文 1: Microsoft GraphRAG — Global & Local Search
- **来源**: Microsoft (arXiv 2024)
- **核心思想**: 抽取全域图谱并生成社区摘要。通过 Local Search 查局部实体，通过 Global Search 对社区摘要做 Map-Reduce。
- **vs qKnow**: 当前只有 Low-level/High-level，缺少微软的“图社区归约摘要”（Community Summarization）支持。

#### 论文 2: HippoRAG 2 — 个性化 PageRank
- **来源**: arXiv 2025
- **核心思想**: 开放知识图谱 + PPR 进行推理导向检索。
- **vs qKnow**: 当前仅采用硬跳数（Hop）衰减，PPR 拥有更好的全局推理漫游能力。

### 5.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | 高级检索算法未被主链路调用 | 双层检索与时序事实功能闲置，丧失架构优势 | 🔴 高 |
| 2 | 缺乏社区摘要（Community Summarization） | 面对宏观的“总结整本书”的问题时无能为力 | 🟡 中 |

### 5.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 唤醒并接入沉睡的图代码 | 在检索入口根据 `queryIntent` 智能派发调用 | 发挥 GraphRAG 威力 |
| **P1** | 引入微软 Global Search 范式 | 在后台预计算图节点社区（Leiden算法）摘要 | 支持宏观提问 |
| **P2** | 演进为 PPR 排序 | 替换跳数衰减，采用 Personalized PageRank | 图检索精度再次提升 |

---

## 模块 6: DAG 工作流引擎

### 6.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| 并行执行 | `DagExecutor.execute()` | CompletableFuture 结合图并发组执行，`resultMap` 精准过滤已完成节点 |
| 断点续传 | `DagCheckpointManager` | 安全的 DB 存储机制，成功执行后才清理断点 |

*(注：代码证实当前的重入机制与断点持久化极其严谨，原诊断的“重复执行”缺陷不存在)*

### 6.2 SOTA 对标

#### 论文 1: LangGraph — Durable Execution
- **来源**: LangChain 2024-2025
- **核心思想**: 提供状态持久化支持下的人为介入（Human-in-the-loop）与时光回溯（Time Travel）。
- **vs qKnow**: qKnow 底盘坚固，但目前一键跑到底，无法在图流转中途挂起任务以等待人工（如主管审批）。

### 6.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | 缺乏 Human-in-the-loop (人工介入) 支持 | 无法满足企业级审批工作流诉求 | 🟡 中 |
| 2 | 无法回溯图流转历史 | 调试定位复杂链路的成本高 | 🟢 低 |

### 6.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P1** | 引入 Human-in-the-loop (挂起与唤醒) | 新增 `SuspendNode` 语义，执行到该节点时保存 checkpoint 并中止，暴露外部激活 API | 企业级落地能力 |
| **P2** | Time travel (状态快照) | 在 DB 中不覆盖而是记录图状态快照，提供版本切换能力 | 极致的追溯诊断体验 |

---

## 总结：挤掉水分后的核心实施主线

### 🏁 P0 — 立即执行（底层骨架补全，1-2 周）
1. **记忆系统双轨断层修复与定时打捞**：接通被绕过的 `ShortTermMemory` 入口，编写 Sleep-time Agent（`@Scheduled`）定时捞取 Redis 空闲对话，调用 LLM 提纯并正式入库 `vector_store`。
2. **唤醒图谱死代码**：在主检索入口激活 `GraphRagRetriever` 的双层语义与时序探索。
3. **CRAG 补齐 Web Search**：拦截 `CragRetrievalEvaluator` 判定失效结果，通过注入工具桥接公网搜索。
4. **评估引擎并发改造**：使用 CompletableFuture 对 `RAGChecker` 和 `RagasEvaluator` 进行批量评估提速。
5. **伪 ColBERT 升格**：重写 N-gram 模拟代码，引入真实的 Token-Level 向量叉乘逻辑。

### 🚅 P1 — 高阶 SOTA 引入（架构飞跃，2-4 周）
1. **注入 DSPy 思想**：放弃纯粹的人工 Prompt 微调，为 QueryRouter 和 Eval 模块接入参数化提示词调优框架。
2. **DAG 引入 Human-in-the-loop**：将纯异步引擎赋予人为干预与挂起（Suspend）能力。
3. **微软 Global Search 模式**：为知识图谱补齐社区摘要，破局宏观宽泛问题。
