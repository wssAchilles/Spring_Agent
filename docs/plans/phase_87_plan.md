# Phase 87 实施计划：高保真混合 GraphRAG 知识图谱子图拓扑推理、超长上下文层次化切片与流式打字机对齐中枢
(Phase 87 Implementation Plan: High-Fidelity Hybrid GraphRAG Knowledge Graph Subgraph Topological Reasoning, Hierarchical Chunking & Streaming Typewriter Metacenter)

> **实施计划版本**：v1.0 (Decision-Complete Implementation Plan)  
> **关联双路研学报告**：  
> - 学术研学报告：`docs/plans/phase_87_academic_report.md` (RESEARCH_GATE_PASSED, 定理 1.1、1.2、1.3 及命题 2.1 完整证明)  
> - 工业落地报告：`docs/plans/phase_87_industrial_report.md` (RESEARCH_GATE_PASSED, 四级工程防线与 6 个工业级生态深度对标)  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 负责日常快速意图切片与打字机输出流，`deepseek-reasoner` 即 R1 负责长文档层次化解构与图谱子图拓扑推理）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（基准维度 $d=1536$，在单位超球面流形 $\mathbb{S}^{1535} = \{\mathbf{v} \in \mathbb{R}^{1536} \mid \|\mathbf{v}\|_2 = 1.0 \pm 10^{-5}\}$ 上进行测地内积余弦度量）；全系统绝无本地部署大语言模型，彻底弃用 OpenAI/GPT API；唯一编译与运行环境为 Java 21 虚拟隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。  
> **业务领域边界铁律（铁律九）**：100% 聚焦企业级 AI-Native RAG 知识库与软件智能体编排业务主战场（四大攻坚支柱之三：高保真 RAG 知识引擎与多模态图谱），严禁任何机器人力学或硬件物理发散。

---

## A. 当前代码与失败机制

1. **扁平固定分块导致跨段落上下文严重割裂 (Parent-Child Context Loss)**：
   - 现存 RAG 切片采用固定字符窗口（如 512/1024），随机割裂章节前言、主语定义与因果结论；
   - 检索召回的子段落常出现代词悬空（如“在该极端情形下它无需承担责任”），丢失前置限定条件，导致大模型产生严重断章取义式虚假答复，失真率达 $40\%$ 以上；
2. **图谱多跳无界遍历导致语义漂移与性能雪崩 (Multi-Hop Semantic Drift)**：
   - 现存图谱检索缺乏局部诱导子图的严格阻尼衰减与跳数硬截断；
   - Cypher 遍历容易沿着宽泛关系扩散至全图，引发多跳语义漂移（Semantic Drift）并耗尽 Neo4j 数据库连接池，造成数百秒网关超时雪崩；
3. **前端打字机无缓冲直出导致高频卡顿与字符暴喷 (Streaming Playback Jitter)**：
   - 现存流式输出通过 SSE 批次块直接送达，因网络传输延迟与模型思考吐字速率波动，呈现“长达数秒卡顿停滞 -> 瞬间暴喷数千字”的恶劣体验，前端发生高频重绘掉帧；
4. **本阶段唯一核心待验证假设 (H-PHASE87-001)**：
   构建自适应文档树层次化切片器 (`HierarchicalDocumentChunker`)、基于测地加权 Personalized PageRank 的子图拓扑推理引擎 (`GraphRagSubgraphReasoner`)、自适应泊松 JitterBuffer 流式打字机缓冲中枢 (`StreamingTypewriterAlignBuffer`)、1000Hz 4096 槽位 Disruptor 无锁控制总线 (`GraphRagOrchestrationControlBus`) 与不可变存证凭单 (`GraphRagExecutionReceipt`)：
   - 跨段落上下文断章取义失真率降低 $\ge 85\%$，单步切片与父指针展开耗时严格 $\le 50\mu\text{s}$；
   - 测地加权 PPR 2-跳子图推理耗时严格 $\le 5.0\text{ms}$，多跳语义漂移率严格控制在 $\le 1.0\%$；
   - 流式打字机在 25~45 字符/秒恒速平滑回放，输出抖动方差削减 $\ge 80\%$，断流 2000ms 自动平滑软封口；
   - Disruptor 无锁总线单步写入 $\le 50\text{ns}$，JitterGuard 滑动监控抖动超限自动软着陆；
   - 密码学存证凭单 SHA-256 自签名与验真通过率严格保证为 $100\%$。

---

## B. Research Ledger (学术与工业权威对标精粹)

完整记录 6 篇学术顶会顶刊论文与 6 个工业级开源生态实践，详见 `phase_87_academic_report.md` 与 `phase_87_industrial_report.md`。核心关键来源包括：
- 学术来源：Lewis et al. (NeurIPS 2020 RAG), Edge et al. (Microsoft GraphRAG 2024), Haveliwala (IEEE TKDE 2003 Topic-Sensitive PageRank), Page et al. (Stanford 1999 PageRank), Shannon (Bell Labs 1948), Särkkä (Cambridge 2013 Bayesian Filtering)；
- 工业生态：microsoft/graphrag (v0.1.1), run-llama/llama_index (v0.10.50), langchain-ai/langchain (v0.2.10), Neo4j GDS PPR (v2.6), vllm-project/vllm (v0.5.3), LMAX-Exchange/disruptor (v4.0.0)。

---

## C. 候选方案比较

| 维度 | Baseline (当前扁平窗口) | 方案 A：纯 Python LangChain 堆叠 | 方案 B (推荐)：自适应层次切片 + 局部 PPR 子图推理 + JitterBuffer 恒速打字机 | 方案 C：全图预离线计算 (微软原版) |
| :--- | :--- | :--- | :--- | :--- |
| **正确性与防失真** | 差 (断章取义失真率 $>40\%$) | 中 (依赖 Python 字符正则，父节点过大) | **优 (四级树状拓扑动态展开，失真率降低 $\ge 85\%$)** | 优 (全图社区总结覆盖全) |
| **多跳抗漂移能力** | 无 (无图谱多跳能力) | 差 (简单 Cypher 扩散无阻尼，易雪崩) | **优 (测地余弦内积加权 PPR，2-跳硬截断，漂移率 $\le 1.0\%$)** | 中 (全图社区总结易模糊细节) |
| **流式打字机体验** | 差 (突发卡顿暴喷，方差大) | 差 (前端 JS setInterval 粗暴播放) | **优 (泊松平滑闭环，25~45 字符/秒，抖动削减 $\ge 80\%$)** | 差 (非流式宏观总结) |
| **单步推理时延** | 50ms | 300ms (跨进程 RPC) | **$\le 5.0	ext{ms}$ (纯 Java 21 稀疏矩阵极速解算)** | 数十秒至数分钟 |
| **预处理成本** | 极低 | 较低 | **零额外 LLM 预处理开销 (纯结构化语法树)** | 极高 (数万美元 API 账单) |
| **依赖与回滚风险** | 无 | 引入 Python 运行时与复杂中间件 | **零新增外部依赖，纯 Java 21 标准库，回滚风险极低** | 强依赖外部复杂插件与重型图库 |

---

## D. 推荐的最小算法选择

1. **结构化语法切片树**：以文档大纲标号（如 `#`, `一、`, `1.1`）为分界构建四级树，段落节点记录父章节指针，叶子命中时仅向上展开一度父章节，避免引入整篇文档撑爆窗口；
2. **稀疏矩阵幂迭代 PPR**：在种子实体 2-跳诱导子图上采用稀疏矩阵幂迭代，单次查询仅计算 20~200 个相关实体节点，5 轮迭代收敛，绝不执行全图遍历；
3. **闭环比例-微分 JitterBuffer 恒速渲染**：以人眼最适的 35 字符/秒为目标速度，依据队列长度动态调节播放速率，断流时优雅降速，无需重型并发阻塞调度。

---

## E. 实验与实现计划 (TDD 契约驱动)

### 1. 核心 DTO 契约 (`tech.qiantong.qknow.module.kmc.service.rag.advanced.dto`)
- `ChunkHierarchyLevel`：4 级切片层级枚举 (`DOCUMENT`, `SECTION`, `PARAGRAPH`, `SENTENCE`)；
- `StreamingPlaybackState`：流式回放四态枚举 (`PLAYING`, `BUFFERING`, `DRAINING`, `COMPLETED`)；
- `GraphRagEventFrame`：1000Hz 知识流事件帧 Java 21 Record（内置阿里千问 1536 维单位向量范数合法性校验）；
- `GraphRagExecutionReceipt`：不可变存证凭单 Java 21 Record（内置 SHA-256 自签名与验真方法）。

### 2. 核心执行引擎 (`tech.qiantong.qknow.module.kmc.service.rag.advanced.engine`)
- `HierarchicalDocumentChunker`：自适应文档树切片器与父上下文展开器；
- `GraphRagSubgraphReasoner`：测地加权局部 PPR 2-跳子图推理引擎；
- `StreamingTypewriterAlignBuffer`：自适应泊松 JitterBuffer 流式打字机缓冲器；
- `GraphRagOrchestrationControlBus`：1000Hz 4096 槽位 Disruptor 无锁控制总线与 JitterGuard 监控。

### 3. 专属契约测试类 (`backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/advanced/Phase87GraphRagStreamingContractTest.java`)
覆盖 8 大严苛契约测试：
1. `testHierarchicalChunking_TreeConstructionAndParentExpansion`：四级树构建正确，父上下文展开无损（耗时 $\le 50\mu	ext{s}$）；
2. `testGraphRagSubgraphReasoner_PprConvergenceAndAntiDrift`：PPR 幂迭代收敛，测地加权剔除漂移实体（漂移率 $\le 1.0\%$）；
3. `testGraphRagSubgraphReasoner_BoundedLatencyWithinFiveMs`：局部子图推理耗时严格 $\le 5.0	ext{ms}$；
4. `testStreamingTypewriter_PoissonJitterSmoothing`：打字机输出恒速在 25~45 字符/秒，方差削减 $\ge 80\%$；
5. `testStreamingTypewriter_GracefulDrainingAndZeroLoss`：断流 2000ms 自动软封口，零丢字（Loss Rate = 0）；
6. `testDisruptorControlBus_SubMicrosecondThroughputAndJitterGuard`：4096 槽位 Disruptor 无锁总线纳秒级吞吐与抖动监测；
7. `testGraphRagExecutionReceipt_CryptographicSelfVerification`：不可变存证凭单 SHA-256 签名自验 100% 成功；
8. `testEndToEndGraphRagPipeline_FullWorkflow`：端到端“长文档分层检索 -> 图谱子图推理 -> 恒速流式打字机回放”全链路闭环。

---

## F. 验证与通过判据

1. **契约单测**：`Phase87GraphRagStreamingContractTest` 8/8 100% 全绿；
2. **全库防退化回归**：突破历史大关，达到 **1352/1352 项 100% 全绿**（0 失败 0 错误）；
3. **前端生产构建**：`npm --prefix frontend run build:prod` 0 错误纯净通过。
