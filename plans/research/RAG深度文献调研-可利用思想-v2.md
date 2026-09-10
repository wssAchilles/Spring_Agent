# RAG / Agent 深度文献调研 — 可利用思想汇总（v2）

> **日期**：2026-09-10  
> **方法**：6 路并行深读（A–F），按 `AGENTS.md` 定向 3–6 源 / 主题，禁止浅尝辄止  
> **性质**：只读研究；**未改代码**；每个思想仍须单独过 Gate 才能实施  
> **前置**：v1 H0–H12 已闭环；v2 路线图已写；holdout vector Hit@10=1.00（无区分度）

---

## 0. 六路结论一览

| 主题 | 核心判断 | 最优先可做 |
|---|---|---|
| **A 自适应/反思 RAG** | AMBIGUOUS 路被忽略是实现缺口；规则版 A/B/C 路由可落地 | T2 AMBIGUOUS 合并 → T1 规则路由 |
| **B 晚交互** | Hit@10 已饱和时**不应**上真晚交互；粗排 MaxSim≠检索引擎 | 保持 baseline；失败归因后再议 |
| **C 混合/评测** | RRF k≠无参；CE 可域不匹配降质；qrel 过粗无区分度 | Q0/Q1 扩容 + MRR + rerank 门控 |
| **D GraphRAG/记忆** | H10 Keep Off 与 HippoRAG2「伤 factual」一致；记忆与图正交 | 维持关图；记忆 consolidate |
| **E Agentic/生产** | ToolResilience 死接线；gRPC 服务端无 cancel；工具无裁剪 | R2 埋点 → R0 治理 + cancel |
| **F 中文基建/原生** | pg_trgm 中文弱；jieba search mode + Tantivy 才是路径 | N0 JNI 基准 → N2 Tantivy |

---

## 1. Research Ledger 精华（每主题 3–6，字段见各子报告）

### A 自适应 / 反思

| ID | 论文 | 关键发现 | 状态 |
|---|---|---|---|
| A1 | Self-RAG 2310.11511 | reflection token 需自训 | VERIFIED；**不采用完整训练** |
| A2 | CRAG 2401.15884 | AMBIGUOUS 双路合并不可省；T5 evaluator &gt; LLM judge | VERIFIED；**实现缺口** |
| A3 | Adaptive-RAG 2403.14403 | A/B/C 三路；无需训 retriever | VERIFIED；**高适用** |
| A4 | Search-o1 2501.05366 | multi-hop +23% EM；单跳≈0；普通模型可负收益 | VERIFIED |
| A5 | R1-Searcher 2503.05592 | 结果奖励 &gt; SFT 模仿 gold query | VERIFIED |
| A6 | DeepRetrieval 2503.00223 | dense 上界高时改写边际≈0；knowledge injection 风险 | VERIFIED |

### B 晚交互

| ID | 来源 | 关键发现 | 状态 |
|---|---|---|---|
| B1 | ColBERT 2004.12832 | MaxSim + query [MASK] expansion | PARTIALLY |
| B2 | ColBERTv2 2112.01488 | 残差压缩 6–10×；训练极贵 | VERIFIED |
| B3 | PLAID 2205.09707 | 质心代理可保 99% top-k | VERIFIED |
| B4 | Jina-ColBERT-v2 2408.16672 | 多语；中文 MIRACL zh nDCG@10=52.3 | VERIFIED |
| B5 | JaColBERTv2.5 2407.20750 | 小语种低成本配方：动态 query 长度、单 teacher、ckpt 平均 | VERIFIED |
| B6 | BGE-M3 2402.03216 | dense+sparse+mul；multi-vec 定位 rerank | VERIFIED |

**B 门禁阈值**：holdout Hit@10&lt;0.85 或失败≥30% 词汇失配或长文档稀释——**当前均不满足**。

### C 混合与评测

| ID | 来源 | 关键发现 | 状态 |
|---|---|---|---|
| C1 | RRF SIGIR 2009 | rank-based；k≈60 | PARTIALLY |
| C2 | Bruch TOIS 2210.11934 | **RRF 对 k 敏感；tuned 跨域差**；CC 可更优 | VERIFIED |
| C3 | BEIR 2104.08663 | 异构分层 + nDCG@10 | PARTIALLY |
| C4 | RAGChecker 2408.08067 | claim 级 P/R/F1，诊断检索 vs 生成 | VERIFIED |
| C5 | RGB 2309.01431 | 负例拒答 / 噪声 / 反事实；中英 | VERIFIED |
| C6 | SciRet 2608.03860 | **域外 CE 可降 precision** | PARTIALLY（abstract） |

### D GraphRAG / 记忆

| ID | 来源 | 关键发现 |
|---|---|---|
| D1 | MS GraphRAG 2404.16130 | 全局 sensemaking；索引贵 |
| D2 | LightRAG 2410.05779 | 实体/主题双层 seed + 1 跳；无社区全量生成 |
| D3 | HippoRAG2 2502.14802 | **图会伤 factual memory**；PPR 利在多跳 |
| D4 | RAPTOR 2401.18059 | 无图递归摘要树 |
| D5 | Mem0 2504.19413 | 图增量仅 ~2%；consolidate 更关键 |
| D6 | Zep 2501.13956 | 时序 validity；跨会话 |

### E Agentic / 生产

| ID | 来源 | 关键发现 |
|---|---|---|
| E1 | ReAct 2210.03629 | Thought/Action/Observation；步数=成本 |
| E2 | Toolformer 2302.04761 | 结果并回勿无界膨胀 |
| E3 | Agentic RAG Survey 2501.09136 | 效率/取消/治理是一等问题 |
| E4 | LangGraph docs | 取消/中断一等公民；幂等副作用 |
| E5 | Qwen-Agent | `max_input_tokens` 截断 messages |
| E6 | RAGFlow v0.27.2 | grounded citations 一等能力 |

### F 中文 IR / 原生

| ID | 来源 | 关键发现 |
|---|---|---|
| F1 | jieba-rs | **search mode**（重叠 n-gram）BM25 recall +5–15% |
| F2 | Tantivy | Rust BM25；插 jieba；替代 pg_trgm |
| F3 | pgvector HNSW | 已 SIMD；JNI vecsim 或冗余 |
| F4 | DiskANN/FAISS | 小库不需要；batch=1 时 SIMD 才关键 |
| F5 | 中文 IR 评测 | 词级 &gt; 字级；域词典 +10–20% NDCG |
| F6 | Vespa/Weaviate | 多阶段 + RRF 同构架构 |

完整全字段 Ledger 见各子代理报告（会话通知）；本文件为**可执行浓缩**。

---

## 2. 可利用思想 → 项目落地（优先级）

### P0 — 不改算法也能做（工程 / 数据）

| # | 思想 | 落地点 | 最小实验 | 出处 |
|---|---|---|---|---|
| 1 | **工具统一预算器** | `AgentOrchestrator` 包 ToolCallback：maxChars + 真超时 + 截断标记；`SearchKnowledgeTool` 不再裸返回 | 单测 + token 报告 | E2/E5 |
| 2 | **gRPC 服务端 cancel** | `HermesGrpcService.setOnCancelHandler` → dispose ReAct + 释放信号量 | 断连后无新 LLM 调用 | E4/H9 |
| 3 | **R2 成本埋点先行** | TokenCounter 真实 Usage；价目配置化；evidence r2-cost | before/after 数字 | E6/C |
| 4 | **CRAG AMBIGUOUS 合并** | 现仅 INCORRECT 纠正；补 AMBIGUOUS=内部扩展∪改写 | golden 上标签分布 + RAGChecker | A2 |
| 5 | **Holdout 扩容 + 分层** | short-zh / medium-zh/en / entity / negative；MRR@10 | n≥40；negative≥5 | C3/C5 |
| 6 | **jieba search mode + 自定义词典** | tantivy / KeywordRetriever 分词 | NDCG@10 vs precise vs pg_trgm | F1/F5 |

### P1 — 需 Gate 的算法/结构候选

| # | 思想 | 假设方向 | 前置 |
|---|---|---|---|
| 7 | **规则版 A/B/C 路由** | A 类不检索省延迟，整体 Acc 不降 | Q1 holdout；fail-open 到单步 |
| 8 | **重排门控** | 域外 CE 无益时跳过；记账 ΔMRR/p95 | Q1；selection 调门控 |
| 9 | **RRF k 最小消融** | k∈{10,60,100} + 可选加权 RRF | Q1 labeled selection |
| 10 | **Tantivy 替代 pg_trgm** | 中文 BM25 质量与 p95 | N0 基准 + Q1 |
| 11 | **查询改写消融** | dense 已强时 rewrite 边际≈0 | Q0；防 knowledge injection |
| 12 | **记忆 consolidate** | ADD/UPDATE/DELETE 一致性 | 与 GraphRAG 正交 |
| 13 | **引用一致率** | ragContext 与 sourceRefs 一致 ≥0.95 | H4a 已接线；R0 后 |

### P2 — 明确不采用（禁止换名重入）

| 不采用 | 理由 |
|---|---|
| Self-RAG / R1-Searcher / Search-o1 **训练** | 无 GPU/可训 API 模型 |
| PLAID / 真晚交互一阶段 | Hit@10 饱和；无触发条件 |
| 默认开启 GraphRAG | H10 + HippoRAG2 同向 |
| `GRAPH_SCORE` 常量压分 / 融合后 RRF 分过滤 | 跨 scale 污染；H7 教训 |
| CRAG INCORRECT→外网搜索 | 组织合规 |
| Swift 服务端 / Vespa 全栈 / DiskANN | 无收益或过度工程 |
| 改 qrel 过线 | 铁律 |
| Hermes 内二次并行检索 | 与控制面重复 |

---

## 3. 与现有默认的冲突/兼容

| 现有 | 调研结论 | 动作 |
|---|---|---|
| CRAG 抽样、忽略 AMBIGUOUS | 论文消融证明不可省 | **优先回补**（T2） |
| RRF k=60 | 非无参；有标签后再消融 | Q1 后 |
| post-fusion-filter=false | 正确 | 保持 |
| GraphRAG false | 正确 | 保持 |
| ColBERT fail-closed | 正确 | 保持 |
| query-transform 默认关 | dense 强时改写边际小 | 先 T4 消融 |
| SIMPLE 轻检索 | 与 Adaptive 策略 B 兼容 | 保持 |
| holdout Hit@10=1.00 | **无区分度** | **Q1 扩容是一切算法 Gate 前置** |

---

## 4. 建议执行序（衔接 v2）

```text
[立即]
  Q0 真实 query 脱敏集
  Q1 holdout 分层扩容（+MRR@10 脚本）
  R2 成本埋点（TokenCounter + evidence）
  P2 安全 hardening（SSRF/Key/SSE…）

[Q1 完成后，算法候选]
  T2 AMBIGUOUS 合并
  T1 规则 A/B/C 路由
  重排门控 + RRF k 消融
  N0 JNI 基准 → N2 Tantivy
  T4 改写消融

[仍拒绝]
  真晚交互 / GraphRAG 生产开 / 训练型 Self-RAG
```

---

## 5. 授权边界（本文件）

- 本 MD **只读研究**，不申请实施授权。  
- 任一 P0/P1 项均须：独立假设 + Ledger（若改算法）+ 契约 + **用户批准** 后实施。  
- 改 holdout/qrel、改生产默认、A/B、push 线上：**每次单独授权**。

---

## 6. 附：子主题报告索引（会话内）

| 主题 | 代理 | 内容 |
|---|---|---|
| A | general-3 | Self-RAG/CRAG/Adaptive-RAG/Search-o1/R1/DeepRetrieval |
| B | general-4 | ColBERT→v2→PLAID→Jina→JaColBERT→BGE-M3 |
| C | general-5 | RRF/Bruch/BEIR/RAGChecker/RGB/SciRet + holdout 设计 |
| D | general-6 | GraphRAG/LightRAG/HippoRAG2/RAPTOR/Mem0/Zep |
| E | general-7 | ReAct/Toolformer/Agentic Survey/LangGraph/Qwen-Agent/RAGFlow |
| F | general-8 | jieba-rs/Tantivy/pgvector/SIMD/中文 IR |

---

**Files touched**: 本 MD（调研输出，非算法实现）
