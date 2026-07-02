# qKnow 遗留 SOTA 架构演进与补强蓝图 (Final Implementation Plan)

> **修订版：极度详尽的代码级施工方案**
> 
> 在对 `RagRetrievalService.java`、`ColbertScorer.java` 以及 `GraphCommunityService.java` 进行源码穿透审查后，确认这 3 个模块虽然存在逻辑，但距离真正的 SOTA 模型（真正的动态计算、真向量计算、大模型 Map-Reduce）还有差距。
> 
> 针对您的要求，本计划不涉及任何实质代码修改，而是进一步细化并完善了针对这三个遗留项的**保姆级开发与重构步骤**。下一阶段的 Agent 可以完全无脑照做，实现 100% 验收。

---

## 一、 RAG 模块遗留项详细重构计划

### 1. 动态拓扑 K 值 (Dynamic TopK) 激活重构
*   **目标文件**：`qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java`
*   **诊断说明**：内部存在完美的 `resolveTopK` 计算器，但主干路由中写死了 `(int) Math.min(topK * 1.5, 80)`。
*   **执行步骤（Action Items）**：
    1.  **意图解析前置**：在 `retrieve` 主方法的第 79 行（`QueryRouter.QueryRoute route = queryRouter.classify(query);`）下方，提前执行意图解析：
        `QueryIntent queryIntent = queryIntentAnalyzer.analyze(originalQuery);`
    2.  **移除硬编码**：删除现有的 `if (route == QueryRouter.QueryRoute.COMPLEX) { dynamicTopK = ... }` 死逻辑。
    3.  **接入动态计算**：替换为真实的计算公式：
        `int dynamicTopK = resolveTopK(topK, route, queryIntent);`
    4.  **下沉参数透传**：由于 `queryIntent` 已经被提前解析，需要将 `queryIntent` 作为参数传递给 `retrieveOnce` 方法（修改 `retrieveOnce` 的方法签名），替换掉原来在 `retrieveOnce` 内部临时解析的逻辑，避免 CPU 算力重复浪费。
    5.  **校验点**：通过 DEBUG 日志确认 `dynamicTopK` 会随着 `queryIntent.getKeywords().size()` 以及 `getDayNo()` 的变化而浮动变化。

### 2. ColBERT Token-level 真向量升级
*   **目标文件**：`qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java`
*   **诊断说明**：目前的 Token 向量是通过 `text.hashCode()` 做位运算模拟的，必须升级为通过 Embedding API 获取的真实词向量。
*   **执行步骤（Action Items）**：
    1.  **依赖注入**：在 `ColbertScorer` 中注入 Spring AI 的 `EmbeddingModel` 接口或系统的 `IEmbeddingModelService`。
    2.  **分词实现 (Tokenization)**：编写一个简单的分词器方法（按空格或标点符号分割中英文，或者使用 `cn.hutool.extra.tokenizer`）。
    3.  **向量获取 (Embedding Fetching)**：
        *   废弃基于 Hash 的 `tokenVector` 方法。
        *   将分词后的 List 传递给大模型 Embedding 服务，批量获取对应的 `List<List<Double>>` 向量组。
    4.  **相似度矩阵运算重构 (MaxSim)**：
        *   在 `computeMaxSim` 中，遍历 Query 的每一个 Token 向量，对 Document 的每一个 Token 向量进行余弦相似度（Cosine Similarity）计算。
        *   由于真实的向量是浮点数，点积有可能是负数，确保内层循环的初始值设为 `double best = -Double.MAX_VALUE;` 而不是 `0.0`，从而正确捕获最大相似度。
    5.  **性能防腐设计 (可选)**：由于对每个 Candidate 进行重排都需要调用 API，为了防止超时，应当限制最大分词数量，并引入基础的并发流 `.parallelStream()` 提升批量 Embedding 获取速度。

---

## 二、 知识图谱模块遗留项详细重构计划

### 3. 微软 GraphRAG: 大模型 Map-Reduce 全局搜索与总结
*   **目标文件**：`qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/GraphCommunityService.java`
*   **诊断说明**：当前的社区摘要和全局搜索仅仅使用了代码逻辑进行“基于规则的关键词拼接和打分”，缺乏大模型（LLM）的真正理解力，丧失了 GraphRAG 的灵魂。
*   **执行步骤（Action Items）**：
    1.  **依赖注入**：在 `GraphCommunityService` 中注入核心大模型服务 `IChatModelService`。
    2.  **重构 1：基于 LLM 的社区摘要 (`buildSummary`)**
        *   废弃原始的纯文本拼接（“社区 X 包含 Y 个实体...”）。
        *   构造 Prompt：`"你是一个图谱社区摘要专家。以下是图谱社区中的实体: {entities} 和标签: {labels}。请用一段连贯、有见地的文本总结该社区的总体概念和隐藏联系。"`
        *   调用大模型生成 Summary 并存入图数据库。
    3.  **重构 2：Map 阶段并发处理 (`globalSearch` - Map)**
        *   当用户输入 Global Query 时，加载所有社区摘要。
        *   使用 `CompletableFuture` 或并行流，针对每个（或 Top 级别）的社区摘要，分别向大模型提问：`"用户问题: {query}。参考社区信息: {summary}。如果社区信息能回答该问题，请提取关键信息；如果不相关，请回复 '无关联'。"`
        *   收集所有非空且有关联的局部答复（Partial Answers）。
    4.  **重构 3：Reduce 阶段综合生成 (`globalSearch` - Reduce)**
        *   将所有收集到的 Partial Answers 合并为一个大型上下文。
        *   再次向大模型提问：`"用户问题: {query}。以下是从不同图谱社区中提取的部分信息: {partial_answers}。请作为一个全局分析师，综合这些信息，生成一份全面、深入的最终解答。"`
        *   将大模型的最终生成的文本作为 `GlobalSearchResult` 的 `answer` 返回给用户。
    5.  **校验点**：Map 阶段必须做并发处理以解决 LLM 耗时累加问题；如果不相关必须有大模型的拒答机制以免产生幻觉融合。

---

> [!NOTE]
> **交接声明**
> 所有底层实现细节、方法签名变更、外部依赖注入需求均已穷尽。这份计划已处于最高完备度（Ready for Execution）。请检查该文档，若无异议，后续的 Agent 将直接根据以上 Action Items 执行源代码级的实质性修改。
