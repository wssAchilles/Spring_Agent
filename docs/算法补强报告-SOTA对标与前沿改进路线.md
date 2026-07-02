# qKnow 算法补强报告 — SOTA 对标与前沿改进路线

> **生成时间**: 2026-07-01  
> **分析方法**: 代码深度解析 + Exa AI 搜索（NeurIPS/ICML/ACL/SIGIR 2024-2026）+ 工业实践对比  
> **覆盖模块**: RAG 管线 / Agent 编排 / 记忆系统 / 评估框架 / 知识图谱 / DAG 工作流

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
- **关键创新**: 通过拒绝采样自动生成中间检索链，无需人工标注
- **性能**: 多跳 QA 提升 10+ EM 分，KILT benchmark 新 SOTA
- **vs qKnow**: 你的 CRAG 只做 1 次重检索，CoRAG 支持多步迭代检索链

#### 论文 2: DynamicRAG — 强化学习驱动的动态重排序
- **来源**: NeurIPS 2025
- **核心思想**: 将重排序器建模为 RL agent，用 LLM 输出质量作为奖励信号
- **关键创新**: 动态调整重排序后保留的文档数量 k，避免过少丢失信息或过多引入噪声
- **vs qKnow**: 你的 topK 是固定参数，DynamicRAG 根据查询动态决定

#### 论文 3: RaLa — Ranking by Contrasting Layers
- **来源**: NeurIPS 2025
- **核心思想**: 利用 Transformer 不同层的信息差异进行重排序
- **关键创新**: 低层关注语法，高层关注语义，对比差异给文档加权
- **vs qKnow**: 你的 ColBERT 粗排用 n-gram 重叠，RaLa 用真正的多层语义对比

#### 论文 4: ColBERTv2 — 有效高效的 Late Interaction
- **来源**: NAACL 2022（持续影响力至 2025）
- **核心思想**: token-level 多向量嵌入 + MaxSim 操作符
- **关键创新**: 压缩残差表示降低存储，保留 token-level 精度
- **vs qKnow**: 你的 `ColbertScorer` 用 n-gram 近似而非真正的 token embedding，中文支持缺失

#### 论文 5: CRAG — Corrective Retrieval Augmented Generation
- **来源**: arXiv 2024（你已引用）
- **核心思想**: 检索质量评估 + Correct/Incorrect/Ambiguous 三种纠正策略
- **关键创新**: Incorrect 时回退到 web search，Ambiguous 时混合使用
- **vs qKnow**: 你的实现缺少 web search 回退（Incorrect 时返回空结果）

### 1.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | ColBERT 粗排用 n-gram 而非 token embedding | 中文检索精度损失，非真正 late interaction | 🔴 高 |
| 2 | CRAG Incorrect 时返回空而非 web search 回退 | 复杂查询可能完全失败 | 🔴 高 |
| 3 | topK 固定，不根据查询动态调整 | 简单查询返回过多噪声，复杂查询信息不足 | 🟡 中 |
| 4 | 只做 1 次 CRAG 重检索，不支持多步迭代 | 多跳推理场景效果差 | 🟡 中 |
| 5 | QueryRouter 正则匹配过于宽泛 | 40+ 关键词 OR 连接，误判率高 | 🟡 中 |

### 1.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | ColBERT v2 真 token-level | 替换 n-gram 为 BERT token embedding + MaxSim | 检索精度 +15-25% |
| **P0** | CRAG web search 回退 | Incorrect 时调用 web_search 工具补充 | 复杂查询成功率 +30% |
| **P1** | Dynamic topK | 基于查询复杂度动态调整 topK | 噪声减少 20% |
| **P1** | CoRAG 多步检索链 | 支持 2-3 步迭代检索 + 查询重构 | 多跳 QA +10 EM |
| **P2** | RaLa 多层对比重排 | 利用 Transformer 中间层差异 | 重排精度 +5-10% |

---

## 模块 2: Agent 编排

### 2.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| Plan-and-Solve | `AgentOrchestrator.planAndSolve()` | LLM 分解子任务 → 拓扑排序 → 并行执行 → 聚合 |
| ReactAgent | `AgentOrchestrator.executeAgent()` | ReAct 循环 + ModelCallLimitHook(10) |
| fail-plausible | `HermesKernel.checkSelfConsistency()` | 3 次采样 Jaccard 一致性检查 |
| 智能路由 | `AgentOrchestrator.needsToolCalling()` | 正则匹配 → GPT-4o / DeepSeek 切换 |

### 2.2 SOTA 对标

#### 论文 1: Reason-Plan-ReAct (RP-ReAct)
- **来源**: arXiv 2025
- **核心思想**: 推理-规划器监督 ReAct 执行器，分离规划与执行
- **关键创新**: RPA 自我纠正机制——失败时诊断原因并制定纠正步骤
- **vs qKnow**: 你的 Plan-and-Solve 和 ReactAgent 是独立路径，RP-ReAct 将两者统一

#### 论文 2: Beyond ReAct — Planner-Centric DAG Framework
- **来源**: AAAI 2026
- **核心思想**: 专门的 Planner 模型将复杂查询翻译为 DAG 执行计划
- **关键创新**: SFT + RL 两阶段训练 Planner，优化节点选择和边预测
- **vs qKnow**: 你的 Plan-and-Solve 用 LLM prompt 分解，Beyond ReAct 用专门训练的 Planner

#### 论文 3: Self-Healing Agentic Orchestrator
- **来源**: arXiv 2025
- **核心思想**: 监控-检测-诊断-恢复-验证循环，将可靠性视为有界运行时控制问题
- **关键创新**: 98.8% 任务成功率（vs 94.5% retry-only），verifier-guided 恢复将静默失败降至 0%
- **vs qKnow**: 你的 fail-plausible 检测用 Jaccard（对中文无效），Self-Healing 用验证器引导恢复

#### 论文 4: Gradientsys — Multi-Agent LLM Scheduler
- **来源**: arXiv 2025
- **核心思想**: ReAct 范式的 LLM 调度器，支持并行工具调用和容量管理
- **关键创新**: 流式执行（规划与执行重叠），容量/Token 门控，SSE 可观测性
- **vs qKnow**: 你已有 SSE 流式，但缺少容量门控和流式规划-执行重叠

### 2.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | fail-plausible Jaccard 对中文无效 | 中文场景静默失败检测完全失效 | 🔴 高 |
| 2 | Plan-and-Solve 用 ForkJoinPool 无界线程 | 高并发下线程饥饿 | 🔴 高 |
| 3 | ReAct 无容量门控 | 可能产生过多并行工具调用 | 🟡 中 |
| 4 | Plan-and-Solve 和 ReactAgent 独立 | 无法利用规划结果指导执行 | 🟡 中 |
| 5 | token 计数估算不准（字符/2） | LangFuse 报告的 token 数偏高 | 🟢 低 |

### 2.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 中文 fail-plausible 修复 | 替换 Jaccard 为 CJK 双字符分词 | 中文静默失败检测恢复 |
| **P0** | RP-ReAct 统一架构 | 规划器监督执行器，失败时自动纠正 | 任务成功率 +5% |
| **P1** | 容量门控 | 限制并行工具调用数 + Token 预算 | 高并发稳定性 |
| **P1** | 流式规划-执行重叠 | 规划完成的子任务立即执行，不等待全部规划 | 延迟 -30% |
| **P2** | 专门训练的 Planner | SFT + RL 训练 DAG 规划器 | 复杂任务准确率 +10% |

---

## 模块 3: 记忆系统

### 3.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| 复合评分 | `LongTermMemory.computeCompositeScore()` | 0.5*similarity + 0.3*decay + 0.2*importance |
| Consolidation | `LongTermMemory.store()` | 相似度 >0.85 时合并记忆 |
| Scope 组织 | `MemoryManager` | 类文件系统的路径层级 |

### 3.2 SOTA 对标

#### 论文 1: MemGPT / Letta — 操作系统式记忆管理
- **来源**: ICLR 2024 + Letta 2025
- **核心思想**: 将上下文窗口视为受限内存资源，实现类操作系统的分层记忆
- **关键创新**:
  - Core Memory（始终在上下文中）
  - Archival Memory（无限容量，外部存储）
  - Recall Memory（对话索引）
  - Sleep-time Agent（空闲时异步整理记忆）
- **vs qKnow**: 你的三层记忆（Short/Long/Working）类似，但缺少 sleep-time agent 异步整理

#### 论文 2: Memory Blocks — 上下文窗口抽象
- **来源**: Letta 2025
- **核心思想**: 将上下文窗口分割为离散的功能块，agent 可自主编辑
- **关键创新**: Agent 使用工具调用重写自己的记忆块，实现上下文自我优化
- **vs qKnow**: 你的记忆是被动存储，Letta 的记忆是 agent 主动编辑

### 3.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | Consolidation 阈值可能不触发 | 相似记忆无限增长 | 🔴 高 |
| 2 | 合并时丢失旧内容 | 旧记忆的独特信息丢失 | 🔴 高 |
| 3 | 无 sleep-time agent | 记忆整理只能在对话中进行 | 🟡 中 |
| 4 | scope 过滤过于宽泛 | "user" 匹配 "userProfile" | 🟡 中 |
| 5 | 无记忆 TTL/GC | 衰减到 0 的记忆永不清理 | 🟢 低 |

### 3.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 修复 Consolidation 阈值 | PgVector distance → similarity 转换 | 相似记忆合并生效 |
| **P0** | 合并时保留旧内容 | `existing.getText() + "\n---\n" + content` | 信息零丢失 |
| **P1** | Sleep-time Agent | 后台异步整理记忆（合并/删除/重组） | 记忆质量持续提升 |
| **P1** | Memory Blocks | Agent 可主动编辑自己的记忆块 | 上下文利用率 +50% |
| **P2** | 记忆 TTL/GC | 衰减分 <0.1 的记忆自动清理 | 存储空间回收 |

---

## 模块 4: 评估框架

### 4.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| RAGAS 7 指标 | `RagasEvaluator.java` | LLM-as-judge，7 个独立 LLM 调用 |
| RAGChecker | `RAGChecker.java` | Claim-level entailment（ENTAILED/CONTRADICTED/NOT_FOUND） |
| AI Judge | `AiJudgeService.java` | 3D 评分（factuality/relevance/instruction） |

### 4.2 SOTA 对标

#### 论文 1: RAGChecker — Fine-grained RAG Diagnosis
- **来源**: Amazon Science, NeurIPS 2024
- **核心思想**: Claim-level entailment 检查，而非 response-level
- **关键创新**: 从回答中提取 claims，逐个判断蕴含关系，计算 precision/recall/F1
- **vs qKnow**: 你已实现 RAGChecker，但 N+1 LLM 调用未并行化

#### 论文 2: RAGAS — Reference-free RAG Evaluation
- **来源**: EACL 2024
- **核心思想**: 无需 ground truth 的 RAG 评估（faithfulness/answer_relevance/context_relevance）
- **关键创新**: LLM-as-judge + NLI 模型混合评分
- **vs qKnow**: 你已实现 RAGAS 7 指标，但 7 次串行 LLM 调用成本高

### 4.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | RAGAS 7 指标串行执行 | 100 条数据 = 800 次 LLM 调用 | 🔴 高 |
| 2 | RAGChecker N+1 串行 | 10 个 claim = 11 次 LLM 调用 | 🔴 高 |
| 3 | 门控只检查 4/7 指标 | factual_correctness 被忽略 | 🟡 中 |
| 4 | 空回答得满分（RAGChecker） | 空洞真理语义错误 | 🟡 中 |
| 5 | expectedAnswer 参数未使用 | 无法对比 ground truth | 🟢 低 |

### 4.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | RAGAS 并行化 | 7 个指标 CompletableFuture 并行 | 评估速度 7× |
| **P0** | RAGChecker claim 并行 | 所有 claim 蕴含判断并行 | 评估速度 N× |
| **P1** | 门控检查 7/7 指标 | 添加 factual_correctness + noise_sensitivity | 质量漏检减少 |
| **P2** | 批量评估优化 | 多条数据的 LLM 调用批处理 | API 成本 -50% |

---

## 模块 5: 知识图谱增强

### 5.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| LightRAG 双层检索 | `GraphRagRetriever.dualLevelRetrieve()` | Low-level 实体匹配 + High-level 主题发现 |
| 时序事实 | `TemporalFactService` | validity window + 时间衰减 |
| 语义引导遍历 | `GraphRagRetriever.semanticGuidedRetrieve()` | 边权重 = 跳数衰减 × 名称相关性 |

### 5.2 SOTA 对标

#### 论文 1: LightRAG — Simple and Fast RAG
- **来源**: arXiv 2024（你已引用）
- **核心思想**: 图结构文本索引 + 双层检索框架
- **关键创新**: 增量更新算法，图结构与向量表示结合
- **vs qKnow**: 你已实现双层检索，但 `dualLevelRetrieve()` 是死代码（从未被调用）

#### 论文 2: TG-RAG — Temporal Graph RAG
- **来源**: arXiv 2025
- **核心思想**: 双层时间图（时间层次图 + 时间知识图谱）
- **关键创新**: 增量更新避免全量重算，时间覆盖 win rate 0.889 vs GraphRAG
- **vs qKnow**: 你的 `TemporalFactService` 有 validity window，但缺少时间层次图

#### 论文 3: HippoRAG 2 — 个性化 PageRank 检索
- **来源**: arXiv 2025
- **核心思想**: 开放知识图谱 + 个性化 PageRank 进行推理导向检索
- **关键创新**: 将段落级上下文深度整合到 PageRank 算法中
- **vs qKnow**: 你的图遍历用简单跳数衰减，HippoRAG 2 用 PageRank 进行全局排序

### 5.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | dualLevelRetrieve/semanticGuided/temporal 是死代码 | 3 个高级功能从未被调用 | 🔴 高 |
| 2 | 缺少时间层次图 | 无法进行多粒度时间推理 | 🟡 中 |
| 3 | 图遍历用简单跳数衰减 | 缺乏全局排序（如 PageRank） | 🟡 中 |
| 4 | Neo4j GDS 插件未安装 | 社区检测功能不可用 | 🟡 中 |
| 5 | SQL 回退性能差 | Neo4j 不可用时 LIKE 查询 O(n) | 🟢 低 |

### 5.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 接入 dead code | 将 dualLevelRetrieve/temporalRetrieve 接入检索管线 | 功能利用率 100% |
| **P1** | 时间层次图 | 多粒度时间节点（年/月/日）+ 时间报告 | 时间推理能力 |
| **P1** | PageRank 排序 | 用 Personalized PageRank 替代简单跳数衰减 | 图检索精度 +15% |
| **P2** | 增量图更新 | 新文档到达时增量更新图，避免全量重建 | 更新效率 10× |

---

## 模块 6: DAG 工作流引擎

### 6.1 当前实现分析

| 组件 | 实现 | 核心逻辑 |
|------|------|---------|
| 并行执行 | `DagExecutor.execute()` | 拓扑排序 → 并行组 → CompletableFuture |
| 断点续传 | `DagCheckpointManager` | 每组执行后持久化 checkpoint |
| 条件节点 | `ConditionNodeBO` | SpEL 表达式引擎 |

### 6.2 SOTA 对标

#### 论文 1: LangGraph — Durable Execution
- **来源**: LangChain 2024-2025
- **核心思想**: 图状态持久化 + checkpoint 恢复 + 人工介入
- **关键创新**:
  - Checkpointer（线程级短期记忆）
  - Store（跨线程长期记忆）
  - Time travel（回溯到任意 checkpoint）
  - Human-in-the-loop（暂停等待人工审批）
- **vs qKnow**: 你有 checkpoint，但缺少 time travel 和 human-in-the-loop

### 6.3 局限性诊断

| # | 局限性 | 影响 | 严重度 |
|---|--------|------|--------|
| 1 | 错误时删除 checkpoint | 断点续传功能失效 | 🔴 高 |
| 2 | 部分完成组会重复执行 | 浪费计算资源 | 🟡 中 |
| 3 | 无 time travel | 无法回溯到任意执行状态 | 🟡 中 |
| 4 | 无 human-in-the-loop | 无法暂停等待人工审批 | 🟡 中 |
| 5 | PostgreSQL 专用 SQL | 不支持其他数据库 | 🟢 低 |

### 6.4 补强路线图

| 优先级 | 改进项 | 核心逻辑 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 修复 checkpoint 删除 | 仅在成功时删除（已修复） | 断点续传恢复 |
| **P0** | 跳过已完成节点 | pending 过滤已执行节点 | 避免重复执行 |
| **P1** | Time travel | 支持回溯到任意 checkpoint | 调试能力 |
| **P1** | Human-in-the-loop | 暂停节点等待人工审批 | 企业级工作流 |
| **P2** | 数据库抽象层 | 支持 MySQL/H2 | 多数据库兼容 |

---

## 总结：优先级排序的补强路线图

### P0 — 立即执行（1-2 周）

| # | 改进项 | 模块 | 核心收益 |
|---|--------|------|---------|
| 1 | ColBERT v2 真 token-level | RAG | 检索精度 +15-25% |
| 2 | CRAG web search 回退 | RAG | 复杂查询成功率 +30% |
| 3 | 接入 GraphRAG dead code | KG | 功能利用率 100% |
| 4 | RAGAS/RAGChecker 并行化 | Eval | 评估速度 7× |
| 5 | 修复 Consolidation 阈值 | Memory | 相似记忆合并生效 |

### P1 — 短期执行（2-4 周）

| # | 改进项 | 模块 | 核心收益 |
|---|--------|------|---------|
| 6 | CoRAG 多步检索链 | RAG | 多跳 QA +10 EM |
| 7 | RP-ReAct 统一架构 | Agent | 任务成功率 +5% |
| 8 | Sleep-time Agent | Memory | 记忆质量持续提升 |
| 9 | 时间层次图 | KG | 时间推理能力 |
| 10 | Human-in-the-loop | DAG | 企业级工作流 |

### P2 — 中期执行（1-3 月）

| # | 改进项 | 模块 | 核心收益 |
|---|--------|------|---------|
| 11 | Dynamic topK | RAG | 噪声减少 20% |
| 12 | 专门训练的 Planner | Agent | 复杂任务准确率 +10% |
| 13 | Memory Blocks | Memory | 上下文利用率 +50% |
| 14 | PageRank 排序 | KG | 图检索精度 +15% |
| 15 | Time travel | DAG | 调试能力 |

---

## 参考文献

| # | 论文 | 会议 | 核心贡献 |
|---|------|------|---------|
| 1 | CoRAG: Chain-of-Retrieval Augmented Generation | NeurIPS 2025 | 多步迭代检索链 |
| 2 | DynamicRAG: Leveraging LLM Outputs for Dynamic Reranking | NeurIPS 2025 | RL 驱动的动态重排序 |
| 3 | RaLa: Ranking by Contrasting Layers | NeurIPS 2025 | 多层对比重排序 |
| 4 | ColBERTv2: Effective and Efficient Retrieval | NAACL 2022 | token-level late interaction |
| 5 | Jina-ColBERT-v2: Multilingual Late Interaction | MRL 2024 | 多语言 ColBERT |
| 6 | CRAG: Corrective Retrieval Augmented Generation | arXiv 2024 | 检索质量纠正 |
| 7 | RAGChecker: Fine-grained RAG Diagnosis | NeurIPS 2024 | Claim-level 评估 |
| 8 | RAGAS: Automated RAG Evaluation | EACL 2024 | Reference-free 评估 |
| 9 | LightRAG: Simple and Fast RAG | arXiv 2024 | 图结构双层检索 |
| 10 | TG-RAG: RAG Meets Temporal Graphs | arXiv 2025 | 时间图 RAG |
| 11 | HippoRAG 2: Personalized PageRank Retrieval | arXiv 2025 | 推理导向图检索 |
| 12 | Reason-Plan-ReAct | arXiv 2025 | 规划-执行分离 |
| 13 | Beyond ReAct: Planner-Centric DAG | AAAI 2026 | 专门训练的 Planner |
| 14 | Self-Healing Agentic Orchestrator | arXiv 2025 | 可靠性控制循环 |
| 15 | Gradientsys: Multi-Agent LLM Scheduler | arXiv 2025 | 并行工具调度 |
| 16 | MemGPT / Letta: Operating System for LLM Memory | ICLR 2024 | 分层记忆管理 |
| 17 | Memory Blocks: Agentic Context Management | Letta 2025 | 上下文窗口抽象 |
| 18 | LangGraph: Durable Execution | LangChain 2025 | checkpoint + time travel |
