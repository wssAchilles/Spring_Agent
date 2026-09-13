# Phase 04: Agent 高级认知、自适应路由与 CRAG 模糊澄清机制

本文件是 Phase 04 阶段的严格实施契约，基于多智能体并行研读（学术界最新论文 + 工业界成熟框架）汇总生成。

## A. 当前代码与失败机制

**当前执行路径与痛点**：
目前系统处于 RAG 基础链路连通状态，默认机制为线性直通路径（`User Query -> Embedding Search -> LLM Gen`）。这种模式在面对真实用户时存在三大失败机制：
1. **路由延迟与成本双高**：无差别使用 LLM 判断意图会导致极高的首字节延迟（TTFT）和 Token 开销，无法应对高并发。
2. **缺乏置信度与早期熔断机制**：当用户提问模糊、多意图或超出边界时，大模型会基于不相关的文档进行强制自回归生成，导致严重的幻觉（Hallucination）。
3. **澄清流转断层（静默失败）**：当知识库判定为未命中或“模糊（Ambiguous）”时，系统缺乏与前端的标准中断协议，要么强行乱答，要么同步阻塞至连接池耗尽。

**本阶段唯一待验证假设**：
引入**两级级联路由（L1 语义向量 + L2 LLM 置信度阈值）**与**基于状态机的 SSE 中断机制（State Graph Suspend）**，能在不显著增加 P95 延迟的前提下，将模糊问题的拦截澄清率提升至 75% 以上，同时实现与前端零数据混乱的无缝澄清交互。

---

## B. Research Ledger (学术与工程双轨)

| id | sourceType | titleOrRepository | authorsOrMaintainer | venueAndYear | verificationStatus | relevantFinding | projectApplicability | limitations |
|---|---|---|---|---|---|---|---|---|
| 1 | paper | Self-RAG | Asai et al. | ICLR 2024 | VERIFIED | 通过特殊控制 Token 实现模型的自适应路由与质量批判。 | 启发通过 Prompt + Logprobs 模拟逻辑层反思。 | 需要侵入式微调模型，本项目仅能采用 API 侧控制。 |
| 2 | paper | CRAG (Corrective RAG) | Yan et al. | KDD 2024 | VERIFIED | 提出将检索置信度划分为 Correct, Incorrect, Ambiguous，并在 Ambiguous 态触发反问或搜索。 | 为“模糊澄清”触发器提供了三态状态机理论依据。 | 原文偏向文档评估，需平移至 Query 意图评估。 |
| 3 | official-code | semantic-router | Aurelio AI | 2024 | VERIFIED | 基于高维 Embedding 计算余弦相似度实现意图映射。 | **L1** 路由的绝佳工程参考，延迟 < 20ms。 | 泛化性极差，无法处理非线性逻辑演绎。 |
| 4 | official-code | LangGraph | LangChain | 2024 | VERIFIED | 支持 `interrupt_before` 断点及 Human-in-the-loop 机制。 | 完美解决与前端异步、有状态的（SSE JSON）澄清交互。 | 需增加 Thread State 的存储开销。 |

---

## C. 可迁移与不可迁移结论

**可直接采用的方案：**
1. **多阈值分区逻辑（CRAG）**：置信度 $c > \tau_{upper}$ 直接生成；$c < \tau_{lower}$ 直接拒绝；两者之间判定为“Ambiguous”，必须触发反问。
2. **状态图挂起机制（LangGraph理念）**：遇到 Ambiguous 节点，强制挂起状态，不返回常规 Markdown 流，而是抛出带有恢复 Token（Resume Token）的控制型 SSE 帧。

**需要改造的方案：**
1. 业界单纯的 LLM 路由（如 LlamaIndex `RouterQueryEngine`）过重，需改造为**级联路由 (Cascade Routing)**：利用 Semantic Router 进行 L1 高频降本过滤，再利用 DeepSeek JSON 输出的 confidence 进行 L2 低频深度判断。

**必须拒绝的方案：**
1. 拒绝纯 HTTP 请求/响应的同步长连接等待模式处理用户澄清（易导致服务端连接池雪崩）。
2. 拒绝依赖 NLI 多次采样聚类来计算严格“语义熵（Semantic Entropy）”的学术界理论（在线延迟不可接受）。

---

## D. 候选方案比较

| 维度 | Baseline (纯大模型直通路由) | 候选 1 (纯语义路由) | 候选 2 (级联阈值自适应路由 + 状态图挂起) |
|---|---|---|---|
| **正确性/防幻觉** | 低（极易产生强行幻觉） | 中 | **高** |
| **可证伪性** | 无机制支持 | 可通过相似度日志证伪 | 可通过分发日志与置信度得分 ($P$) 严格证伪 |
| **延迟** | 极高 (> 500ms TTFT) | 极低 (< 20ms) | **中低** (大部分被 L1 拦截，部分进 L2) |
| **幻觉边界判定** | 无法判定 | 仅靠余弦距离，泛化极差 | **综合速度与 Logprob 概率校验，处于最优解** |
| **前端状态流转** | 需前端正则死磕反问文本 | 无法处理 | **SSE 下发结构化 JSON 渲染澄清表单** |
| **结论** | 拒绝 (性能与体验极差) | 拒绝 (逻辑支持不足) | **推荐使用** |

---

## E. 推荐的最小算法

**架构核心：Cascade Threshold Adaptive Routing & State Graph Suspend**

1. **L1 语义向量路由 (Semantic Layer)**：
   - 使用轻量本地 Embedding 将 Query 向量化。计算 $s = Cosine(Embed(Query), Route\_Centroid)$。
   - 若 $s > 0.85$（阈值），直接进入对应的业务子流（如闲聊、明确知识查询），无 LLM 损耗。
2. **L2 逻辑判定与概率阈值 (Logical Layer)**：
   - 拦截 L1 未命中的长尾请求，输入 LLM Evaluator。
   - 请求 DeepSeek 返回严格的 JSON 包含 `confidence` 字段，设定不确定性阈值 $\tau_c = 0.6$。
   - 若最高置信对应的概率 $P < \tau_c$（模型对分类处于极度犹豫状态），直接**截断链路**，标记状态机变量 `needs_clarification = True`。
3. **CRAG 澄清挂载与前端对接 (Ambiguous State Suspend)**：
   - 系统检测到 `needs_clarification` 时挂起执行流。
   - 不输出正常回答流，而是向前端推送特殊 SSE 事件区块：
     ```json
     {
       "type": "clarification_required",
       "thread_id": "uuid-1234",
       "context": {
         "reason": "当前问题存在多个知识指代",
         "options": ["指代A", "指代B"]
       }
     }
     ```

---

## F. 实验与实现计划 (TDD 约束)

1. **最小实现文件集合**：
   - `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/agent/CascadeRouter.java` (路由核心引擎)
   - `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/agent/StateGraphExecutor.java` (状态机编排与挂起逻辑)
   - 对应 `backend/qknow-server/src/test/java/...` 下的两个 TDD 单元测试类。

2. **验证隔离与防泄漏**：
   - 构建 `ambiguous_queries.json`（50 条清晰 + 50 条模糊请求）进行回放测试。确保这些数据从未参与过 Semantic Centroid 质心的训练计算。

3. **测试验收标准 (TDD Passing Criteria)**：
   - 断言：清晰请求的路由命中率 > 90%。
   - 断言：模糊请求 100% 触发 `clarification_required` JSON 事件模型，且未调用后续大模型生成。
   - 断言：L1 拦截耗时 < 50ms。

---

## G. 风险、停止条件和后续授权边界

1. **残余风险**：大模型固有存在的“过度自信（Overconfidence）”可能导致极小部分离散的模糊请求依旧产生高于 $\tau_c$ 的错误高分，从而绕过澄清机制（逃逸幻觉）。
2. **立即停止条件 (Kill Switch)**：
   - TDD 验证中，L2 Validator 的引入导致正常请求 API 总延迟恶化超 1000ms。
   - 前端集成时由于无法正确区分纯文本 Stream 与 SSE 控制帧导致页面白屏。
3. **下一步执行边界**：
   - 当前状态：**计划已就绪，等待用户审批。**
   - **未经明确 [同意] 授权前，绝不创建或修改上述 Java 业务文件。**
