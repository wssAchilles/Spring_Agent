# qKnow 综合补强与重构全景实施蓝图 (Implementation Plan)

> **基于多智能体(Multi-Agent)深度源码审查报告生成**
> 
> 在对 `docs/算法补强报告-SOTA对标与前沿改进路线.md` 中列出的所有 P0/P1 优化项目进行全量代码映射后，我们发现：**部分“已实现”的功能由于严重的逻辑缺陷在真实环境中根本无法运行，而另一些高阶功能则完全处于空白状态。**
> 
> 本文档旨在为后续接手的 Agent 提供一份**巨细无遗、可直接按图索骥**的代码操作蓝图。后续 Agent 应当严格根据此列表进行底层 Bug 修复与新特性开发。

---

## 一、 RAG 检索管线与知识图谱 (RAG & KG)

### 1. 修复：伪 ColBERT 真 Token-level 重排 Bug
*   **目标文件**：`ColbertScorer.java`
*   **当前状态**：【代码已写，但含有致命 Bug】
*   **操作指令**：
    *   **计算 Bug**：在 `computeMaxSim` 方法中，局部变量 `double best = 0.0;` 是极其危险的。由于基于 Hash 的伪向量点积余弦相似度可能为负数，当所有的 Token 得分全为负时，`best` 会错误地返回 `0.0`，破坏 MaxSim 核心逻辑。必须将其修正为 `double best = -Double.MAX_VALUE;`。
    *   **配置 Bug**：`ColbertConfig` 中的 `@ConfigurationProperties` 前缀错误地使用了 `hermes.rag.colbert`，必须统一修正为本项目标准的 `qknow.rag.colbert`。

### 2. 修复：微软 Global Search 内存泄露 Bug
*   **目标文件**：`GraphCommunityService.java`
*   **当前状态**：【代码已写，但存在严重内存泄漏】
*   **操作指令**：
    *   在 `detectCommunities` 方法中，复刻了 Leiden 算法并创建了 `community-graph-...` Neo4j 投影图。但是删除图的操作 `CALL gds.graph.drop` 没有放在 `finally` 块中。
    *   **必须**使用 `try...finally` 重构该代码块，确保中间计算无论发生何种异常，Neo4j 内存中的投影图都会被无条件释放，防止图数据库 OOM。

### 3. 开发：注入 DSPy 参数化提示词思想
*   **目标文件**：`QueryRouter.java` 及相关 Eval 类
*   **当前状态**：【完全缺失，进度 0%】
*   **操作指令**：
    *   移除 `QueryRouter` 内硬编码的静态 `CLASSIFY_SYSTEM` 字符串。
    *   引入 `org.springframework.ai.chat.prompt.PromptTemplate`。在 `QueryRouterConfig` 中暴露参数化的 Prompt 配置能力，从而为后期自动调优（DSPy Teleprompter 思想）打好解耦基础。

### 4. 开发：Dynamic topK (漏斗动态放大)
*   **目标文件**：`RagRetrievalService.java`
*   **当前状态**：【完全缺失，进度 0%】
*   **操作指令**：
    *   修改当前固定的 `topK` 透传机制。在执行 `retrieveOnce` 并发召回前，拉取 `queryRouter.classify(query)` 的结果。
    *   若分类为 `COMPLEX`，则通过因子将 `topK` 动态放大（例如乘以 1.5），以保证复杂多步推理能够摄入更宽泛的底层图谱与文档信息。

---

## 二、 智能体编排与记忆系统 (Agent & Memory)

### 5. 修复：双轨记忆断层 (Memory Bypass)
*   **目标文件**：`AgentOrchestrator.java`
*   **当前状态**：【底层已写，但被高层绕过】
*   **操作指令**：
    *   `AgentOrchestrator` 目前在组装 LLM 上下文时（第 259 行附近），直接读取 `request.getHistoryList()`（源自 `chat_message` 关系表）。
    *   必须将其替换为：优先从 `memoryManager.getShortTerm().getMessages(sessionId)` 读取。
    *   需要增加 Fallback 逻辑：若 Redis 无数据（已被打捞清理），则从长期记忆库或关系库重新填充上下文至 Redis。

### 6. 修复：Sleep-time 定时打捞引发 OOM 与僵尸数据
*   **目标文件**：`SleepTimeMemoryAgent.java`, `ShortTermMemory.java`
*   **当前状态**：【定时任务已写，但会引发内存爆炸】
*   **操作指令**：
    *   `SleepTimeMemoryAgent` 目前在获取所有闲置会话时，底层 Redis `SCAN` 的 `while` 循环直接全部装载到 List 中，会瞬间打爆应用内存。必须改为**分页/分批处理（Batch Processing）**。
    *   `ShortTermMemory.touchSession` 缺失了对 Key 的 Redis 过期时间（TTL）设置，导致如果定时任务挂掉，短期记忆缓存将变成僵尸数据。必须强制加入合理的 TTL 兜底。

### 7. 开发：RP-ReAct 编排统一架构
*   **目标文件**：`AgentOrchestrator.java`
*   **当前状态**：【完全缺失，进度 0%】
*   **操作指令**：
    *   打破 `planAndSolve` 和 `executeAgent` 的双分支孤岛。
    *   重构代码，使 Planner 真正变为 Supervisor：由 Planner 拆解出任务后，循环驱动 Executor (ReAct)，并在 Executor 工具调用失败时，捕获异常并交由 Planner 进行 Self-Healing（重新规划）。

### 8. 开发：调度器容量并发熔断 (Capacity Gating)
*   **目标文件**：`AgentOrchestrator.java`
*   **当前状态**：【完全缺失，进度 0%】
*   **操作指令**：
    *   随着 Evaluator 和 Retrieval 的全面并发化，LLM API 可能被瞬间打满。
    *   在类中注入全局级别的 `Semaphore` 令牌桶限流。在所有大量消耗 Token 的 `CompletableFuture.supplyAsync` (包括并行工具执行和子任务处理) 处，加入 `acquire()` 和 `release()` 保驾护航。

---

## 三、 DAG 工作流引擎与并发评估 (Workflow & Eval)

### 9. 修复：Human-in-the-loop 导致的 DAG 断链瘫痪 (0 节点 Bug)
*   **目标文件**：`FlowExecutor.java`, `DagExecutor.java`
*   **当前状态**：【代码已写，但在 JVM 运行时 0 节点执行】
*   **操作指令**：
    *   加入 `SuspendNode` 后，`DagExecutor.executeWithCheckpoint` 在遇到挂起状态时，错误地持久化了下一组索引（`groupIndex + 1`）。当被 `wakeSuspended` 唤醒时，导致越界或并发节点被跳过，直接导致图遍历断链（抛出 `expected: <1> but was: <0>`）。
    *   必须修正挂起持久化逻辑：保存当前组未执行节点的状态，确保被唤醒后不漏节点。

### 10. 修复：评估引擎并发流式退化为串行阻塞
*   **目标文件**：`RagasEvaluator.java`
*   **当前状态**：【代码已用 CompletableFuture，但写成了串行阻塞】
*   **操作指令**：
    *   当前代码 `stream().map(...supplyAsync).map(CompletableFuture::join).toList()` 因为 Stream 的惰性求值特性，导致 `.join()` 立刻阻塞了线程池提交，使并发机制完全失效退化为单线程串行。
    *   必须拆分成两次操作：先 `.toList()` 提交所有任务收集 Future，再在第二个阶段循环调用 `join()`。

---

> [!NOTE]
> **交接指南**
> 后续执行的 Agent，请**逐项**处理以上 10 大缺陷与特性。
> 处理时请遵循“先修致命 BUG（ColBERT、Neo4j泄漏、DAG图断链、评估串行退化、记忆绕过），再开发新特性（DSPy、Dynamic topK、RP-ReAct、限流熔断）”的顺序。
> 每完成一个模块，务必使用 `mvn test` 验证该模块相关用例。
