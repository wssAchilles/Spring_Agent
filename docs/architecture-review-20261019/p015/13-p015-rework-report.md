# P0-15 多智能体生产接线返修交付报告 (Rework Delivery Report)

> **返修对象**：当前工作区 P0-15 多智能体生产受控编排接线实现（基于基线 `7b396909`）  
> **审查依据**：[`11-final-research-gate.md`](11-final-research-gate.md)、[`12-acceptance-review.md`](12-acceptance-review.md)  
> **Java 运行时环境**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`（严格隔离，零污染宿主 Java 17）  
> **模型与向量基线**：生成模型唯一使用 DeepSeek 官方活跃模型（`deepseek-flash`）；生产向量为 `text-embedding-v4`（1024 维），新路径零引入 1536 维硬编码  
> **返修结论**：全部 6 项 P0 缺陷与架构解耦/重复测试问题已 100% 修复并通过全量 Maven 验证（`BUILD SUCCESS`，0 Fail / 0 Error）。

---

## 一、P0 缺陷与审查问题逐项修复对照表

| 编号 | 验收报告指出的缺陷 | 修复位置（文件:行号） | 新实现机制 | 对应验证测试方法 |
|---|---|---|---|---|
| **P0-1** | **SWARM Worker 绕过工具与 ReactAgent**：原 `createRequestScopedSwarmWorkers` 使用匿名子类覆盖 `WorkerAgent.chat` 直接调用 `chatModel.call(...)`，未使用 `whitelistedTools`/`whitelistedResolver`，且 3 个 Worker 共享同一 `systemPrompt` | 1. [`MultiAgentOrchestrationService.java:74-98, 459-515, 709-743`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L709-L743)<br>2. [`WorkerAgent.java:24-121`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/WorkerAgent.java#L24-L121)<br>3. [`SupervisorAgent.java:301-320`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java#L301-L320) | 1. 彻底删除匿名子类覆盖，直接实例化标准 `WorkerAgent`，100% 走 `ReactAgent.builder().tools(whitelistedTools).resolver(whitelistedResolver).build().call(messages)` 真实 ReAct 工具链；<br>2. 为 `RAGWorker`（知识检索与事实核验）、`AnalysisWorker`（多步逻辑推理与对比分析）、`SynthesisWorker`（结构化综合与结论校验）注入差异化角色专属 `systemPrompt` 与能力映射；<br>3. 每次请求独立创建 `WorkerAgent`、`SupervisorAgent` 与 `SharedBlackboard`，禁止复用 `AgentRegistry` 单例，并注入租户/工作区/用户隔离元信息 Header。 | ` OrchestrationWiringContractTest#swarmWorkersExecuteRealReactAgentToolCallsAndHaveDifferentiatedPrompts` |
| **P0-2** | **无界 Map 内存泄漏与全文驻留**：`AgentOrchestrator`、`SupervisorAgent`、`LlmDebateCoordinator` 中的审计凭单与快照 Map 只 `put` 不清理，且 `DebateReceipt` 保存 `proposer/critic/final` 完整正文 | 1. [`BoundedTtlMap.java:19-109`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/BoundedTtlMap.java#L19-L109)<br>2. [`MultiAgentOrchestrationService.java:103-116`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L103-L116)<br>3. [`SupervisorAgent.java:53-56, 89-99`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java#L53-L56)<br>4. [`LlmDebateCoordinator.java:33-57, 179-203`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/LlmDebateCoordinator.java#L33-L57) | 1. 新增线程安全 `BoundedTtlMap<K,V>`（基于 `LinkedHashMap` accessOrder LRU + 惰性/主动 TTL 过期驱逐，默认上限 256 条、TTL 30 分钟）；<br>2. 将 `orchestrationReceipts`、`lastSwarmBlackboardByRequest`、`lastDebateReceiptByRequest`、`SupervisorAgent.sessionQualityReceipts/sessionBlackboardSnapshots`、`LlmDebateCoordinator.sessionReceipts` 全部替换为 `BoundedTtlMap`；<br>3. 重构 `DebateReceipt` Record，彻底移除 `proposerDraft`/`criticFeedback`/`finalVerdict` 全文字段，仅保留字符长度（`proposerLength`/`criticLength`/`finalVerdictLength`）与 64 位 SHA-256 摘要（`proposerSha256` 等）。 | `OrchestrationWiringContractTest#debateModeExecutesThreeRolesAndStoresHashSummaryWithoutFullText`<br>`OrchestrationWiringContractTest#boundedTtlMapAndCrossRequestBlackboardIsolationEnforceCapacityLimit` |
| **P0-3** | **成本预算公式错误**：原实现采用 `baseTokens * 3.0 * 0.000002`（统一 6 元/M），未区分缓存命中/未命中与输出价格，未覆盖 Planner/Aggregator/Debate 全部调用，且未使用 `ChatResponse` 真实 `Usage` | 1. [`OrchestrationProperties.java:64-109`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/OrchestrationProperties.java#L64-L109)<br>2. [`MultiAgentOrchestrationService.java:327-442, 938-1006`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L363-L442) | 1. 删除 `baseTokens * 3.0 * 0.000002` 公式；<br>2. 严格按 DeepSeek 官方价格表建模：缓存命中输入 `0.02/0.04` 元/M、缓存未命中输入 `1.00/2.00` 元/M、输出 `4.00/8.00` 元/M（空闲/高峰期，默认采用高峰期保守单价）；<br>3. 前置预估完整覆盖 SWARM（Planner + $K$ Workers + Aggregator）与 DEBATE（Proposer + $R$ Critic + $(R-1)$ Refine + Judge）全链路输入与输出 Token；<br>4. 运行期在 `CancellableBudgetedChatModel` 中优先提取 `ChatResponse.getMetadata().getUsage()` 的真实 `promptTokens`/`completionTokens` 实时折算累加成本，一旦超出 `maxCostCny`（默认 `0.05` 元）立即触发 `BUDGET_EXCEEDED` 熔断、取消后续调用并回退 `SINGLE`。 | `OrchestrationWiringContractTest#deepSeekOfficialPricingPreCheckAndRuntimeUsageBudgetGate` |
| **P0-4** | **超时不能可靠终止底层调用**：原 `future.cancel(true)` 无法终止 `AGENT_TASK_EXECUTOR` 子线程及后续阶段阻塞 LLM 调用，导致回退 `SINGLE` 后后台继续消耗双倍 Token | 1. [`MultiAgentOrchestrationService.java:555-566, 683-693, 775-816, 926-1092`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L926-L1092)<br>2. [`TopologicalPhasedDispatcher.java:43-50, 121-155`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/TopologicalPhasedDispatcher.java#L121-L155)<br>3. [`SupervisorAgent.java:162-214`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java#L162-L214)<br>4. [`LlmDebateCoordinator.java:78-158`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/LlmDebateCoordinator.java#L78-L158) | 1. 引入请求级 `OrchestrationCallTracker` 与 `CancellableBudgetedChatModel` + `wrapCancellableTool`；每次进入 `ChatModel.call` 时注册当前执行线程（覆盖 `ORCHESTRATION_EXECUTOR` 与 `AGENT_TASK_EXECUTOR` 全部工作线程）；<br>2. 超时触发 `TimeoutException` 时第一时间执行 `tracker.cancelAll()`（置位 `cancelled=true` 并对所有在途活跃线程调用 `Thread.interrupt()`），随后任何后续阶段 `ChatModel.call` 或 `ToolCallback.call` 在入口 `ensureActive()` 处立即抛出 `CancellationException`，零额外 Token 消耗；<br>3. 将 `TopologicalPhasedDispatcher` 的线程池改为静态共享守护池，并将阶段等待由不可中断的 `.join()` 改为响应中断的 `.get()`，中断时显式 `cancel(true)` 所有阶段子任务。 | `OrchestrationWiringContractTest#timeoutCancelsUnderlyingMultiAgentTasksAndPreventsSubsequentLlmCalls` |
| **P0-5** | **全局开关默认开启且不接 Spring 配置**：原代码使用 `System.getProperty("hermes.orchestration.enabled", "true")`，默认开启，非 fail-safe | 1. [`OrchestrationProperties.java:14-23`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/OrchestrationProperties.java#L14-L23)<br>2. [`MultiAgentOrchestrationService.java:283-295`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L283-L295)<br>3. [`qknow-hermes-starter/src/main/resources/application.yml:51-57`](../../../backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml#L51-L57)<br>4. [`qknow-server/src/main/resources/application.yml:222-228`](../../../backend/qknow-server/src/main/resources/application.yml#L222-L228) | 1. 彻底删除 `System.getProperty("hermes.orchestration.enabled", "true")`；<br>2. 新增 `@ConfigurationProperties(prefix = "hermes.orchestration")` 配置类 `OrchestrationProperties`，字段 `enabled` 默认值硬锁定为 `false`；<br>3. 在 `application.yml` 中配置 `hermes.orchestration.enabled: ${HERMES_ORCHESTRATION_ENABLED:false}`；当全局开关为 `false` 时，任何 `SWARM`/`DEBATE` 请求均记录 `DISABLED` 并安全回退 `SINGLE`。 | `OrchestrationWiringContractTest#globalSwitchDefaultsToFalseAndRequiresExplicitEnablement`<br>`OrchestrationWiringContractTest#unknownOrchestrationModeOrMalformedJsonSafelyFallsBackToSingle` |
| **P1-1 / P1-2** | **AgentOrchestrator 职责耦合与重复测试**：`AgentOrchestrator` 塞入近 600 行多智能体代码；`qknow-hermes-core` 与 `qknow-tests` 存在两份同名 `OrchestrationWiringContractTest` | 1. [`AgentOrchestrator.java:96-112, 483-502, 1369-1408`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java#L483-L502)<br>2. [`MultiAgentOrchestrationService.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java)<br>3. [`OrchestrationWiringContractTest.java`](../../../backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java) | 1. 将多智能体编排、预算、Worker 构建、取消控制、有界凭单全部抽离至 `MultiAgentOrchestrationService` 与 `OrchestrationProperties`，`AgentOrchestrator` 净减约 490 行，仅保留 `executeIfEnabled` 委托调用与 `SINGLE` 回退；<br>2. 物理删除 `qknow-hermes-core` 下重复的 `OrchestrationWiringContractTest.java`，仅保留 `backend/tests` 下唯一权威测试套件。 | 全套 7 项契约测试通过 |

---

## 二、关键设计决策说明

### 1. SWARM Worker 真实工具链与角色差异化（P0-1）
- **真实 ReAct 循环**：`MultiAgentOrchestrationService#createRequestScopedSwarmWorkers` 直接构造标准 `WorkerAgent`，不再使用匿名子类覆盖 `chat()`。当 `SupervisorAgent` 通过 `TopologicalPhasedDispatcher` 调度子任务时，`WorkerAgent#chat` 内部通过 `ReactAgent.builder().model(cancellableModel).systemPrompt(differentiatedPrompt).tools(cancellableTools).resolver(whitelistedResolver).build()` 执行标准 `AgentLlmNode -> AgentToolNode -> AgentLlmNode` 闭环。
- **三角色差异化 SystemPrompt 与能力绑定**：
  - `RAGWorker`：绑定 `KNOWLEDGE_RETRIEVAL` 能力，SystemPrompt 强制要求优先调用已授权检索/查询工具核实原始事实与文档依据；
  - `AnalysisWorker`：绑定 `DATA_ANALYSIS` 能力，SystemPrompt 聚焦基于上游黑板事实执行多步因果推导与定量对比；
  - `SynthesisWorker`：绑定 `REPORT_SYNTHESIS` 能力，SystemPrompt 聚焦多源证据交叉核验与结构化汇总。
- **工具白名单与租户上下文隔离**：仅将当前请求 `enabledToolNames` 白名单内的工具暴露给 Worker，`whitelistedResolver` 对任何非白名单工具名直接拦截并返回 `null`；同时将 `[租户与权限上下文: workspaceId=..., userId=..., botId=..., requestId=...]` 注入请求级 `systemPrompt` 与执行 `context`。

### 2. 有界 LRU+TTL 状态治理与零全文驻留（P0-2）
- **`BoundedTtlMap`**：内部采用同步包装的 `LinkedHashMap<>(16, 0.75f, true)`（按访问顺序 LRU）结合条目级 `expireAtMs` 时间戳。每次读写均清理过期项，且在容量超过 `maxEntries`（默认 256）时立即在 `removeEldestEntry` 中驱逐最久未访问记录。
- **DebateReceipt 摘要化**：`LlmDebateCoordinator.DebateReceipt` 仅保留 `(sessionId, roundsExecuted, totalLlmCalls, proposerLength, criticLength, finalVerdictLength, proposerSha256, criticSha256, finalVerdictSha256, durationMs)`，既满足可观测审计与一致性校验，又彻底杜绝在内存中驻留大段用户问答与辩论正文。

### 3. DeepSeek 官方价格表预算模型与运行期 Usage 熔断（P0-3）
- **前置预估公式**：
  $$\text{Cost}_{\text{CNY}} = \frac{T_{\text{in,hit}} \cdot P_{\text{hit}} + T_{\text{in,miss}} \cdot P_{\text{miss}} + T_{\text{out}} \cdot P_{\text{out}}}{10^6}$$
  其中高峰期默认采用官方标准单价（$P_{\text{hit}} = 0.04$ 元/M，$P_{\text{miss}} = 2.00$ 元/M，$P_{\text{out}} = 8.00$ 元/M），空闲期支持切换半价（$0.02 / 1.00 / 4.00$ 元/M）。SWARM 完整计入 `1 Planner + K Workers + 1 Aggregator` 共 $K+2$ 次调用的输入与输出；DEBATE 完整计入 `1 Proposer + R Critic + (R-1) Refine + 1 Judge` 共 $2R+1$ 次调用的输入与累积上下文输出。
- **运行期 Usage 实时熔断**：每次底层 `ChatModel.call` 返回后，`OrchestrationCallTracker#recordCallUsageAndCheckBudget` 优先读取 `ChatResponse.getMetadata().getUsage()` 中的真实 `promptTokens` 与 `completionTokens` 累加人民币成本；一旦累计成本超过 `maxCostCny`（默认 `0.05` 元），立即置位 `budgetExceeded = true`、调用 `cancelAll()` 中断在途任务并抛出 `CancellationException`，外层捕获后记录 `BUDGET_EXCEEDED` 并回退 `SINGLE`。

### 4. 超时取消传播与线程中断机制（P0-4）
- 每个多智能体请求独占一个 `OrchestrationCallTracker`。无论是 `ORCHESTRATION_EXECUTOR` 中的主控线程，还是 `TopologicalPhasedDispatcher`（`AGENT_TASK_EXECUTOR`）中的并行 Worker 线程，在进入 `CancellableBudgetedChatModel#call` 时均会注册到 `tracker.activeThreads`。
- 当 `future.get(timeoutMs, MILLISECONDS)` 抛出 `TimeoutException` 时，外层立即调用 `tracker.cancelAll()`：
  1. 将 `cancelled` 原子布尔值置为 `true`；
  2. 遍历 `activeThreads` 调用 `Thread.interrupt()`，唤醒并打断正在阻塞等待的底层调用；
  3. 后续任何 Worker、ToolCallback、Critic、Judge 或 Aggregator 调用在进入 `CancellableBudgetedChatModel#call` / `wrapCancellableTool#call` 前均被 `ensureActive()` 拦截并立即抛出 `CancellationException`，确保回退 `SINGLE` 后后台多智能体链路不再产生任何额外 LLM 调用。

---

## 三、修改与新增文件清单

### 修改文件（8 个）
1. [`backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto`](../../../backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto)
2. [`backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java`](../../../backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java)
3. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java)
4. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java)
5. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/WorkerAgent.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/WorkerAgent.java)
6. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/TopologicalPhasedDispatcher.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/TopologicalPhasedDispatcher.java)
7. [`backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml`](../../../backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml)
8. [`backend/qknow-server/src/main/resources/application.yml`](../../../backend/qknow-server/src/main/resources/application.yml)

### 新增文件（5 个）
1. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/BoundedTtlMap.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/BoundedTtlMap.java)
2. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/OrchestrationProperties.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/OrchestrationProperties.java)
3. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java)
4. [`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/LlmDebateCoordinator.java`](../../../backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/LlmDebateCoordinator.java)
5. [`backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java`](../../../backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java)（唯一保留的契约测试）

### 删除文件（1 个）
1. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java`（已删除重复测试）

---

## 四、测试验证证据

### 1. 执行命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am test
```

### 2. 精确测试计数与结果
- 构建状态：**`BUILD SUCCESS`**（总耗时 `01:07 min`）
- `git diff --check`：**通过（0 空白/格式错误）**
- 各模块精确测试统计：
  - `qknow-tests`：**1917 tests / 0 failures / 0 errors / 8 skipped**（其中 `OrchestrationWiringContractTest` 7 tests 全部通过）
  - `qknow-hermes-core`：**99 tests / 0 failures / 0 errors / 0 skipped**（已移除重复的 7 条测试）
  - `qknow-module-kb-biz`：**35 tests / 0 failures / 0 errors / 0 skipped**
  - `qknow-mcp-client`：**4 tests / 0 failures / 0 errors / 0 skipped**
  - `qknow-module-ai-biz`：**1 test / 0 failures / 0 errors / 0 skipped**
  - `qknow-module-kmc-biz`：**1 test / 0 failures / 0 errors / 0 skipped**
  - **`qknow-tests` + `qknow-hermes-core` 合计**：**2016 tests / 0 failures / 0 errors**
  - **全反应堆 43 模块总计**：**2057 tests / 0 failures / 0 errors**

### 3. `OrchestrationWiringContractTest` 关键测试项清单
1. `globalSwitchDefaultsToFalseAndRequiresExplicitEnablement`：验证 `OrchestrationProperties.isEnabled()` 默认 `false`，未开启时请求 `SWARM` 仅执行 1 次 `SINGLE` 回退并记录 `DISABLED`；默认 `SINGLE` 零回归。
2. `unknownOrchestrationModeOrMalformedJsonSafelyFallsBackToSingle`：验证非法模式名与损坏 JSON 均记录 `ORCHESTRATION_UNKNOWN_MODE` 并降级 `SINGLE`。
3. `swarmWorkersExecuteRealReactAgentToolCallsAndHaveDifferentiatedPrompts`：验证 `WorkerAgent` 非匿名子类、3 角色 `systemPrompt` 差异化、通过 `ReactAgent` 的 `AgentToolNode` 真实触发 `weatherQuery` `ToolCallback.call(...)`，且未授权工具 `adminSecretTool` 被拦截。
4. `debateModeExecutesThreeRolesAndStoresHashSummaryWithoutFullText`：验证三角色辩论执行 3 次调用，反射校验 `DebateReceipt` 不含全文字段，仅保存字符长度与 64 位 SHA-256 哈希。
5. `deepSeekOfficialPricingPreCheckAndRuntimeUsageBudgetGate`：验证 DeepSeek 官方峰谷/缓存/输出价格预估，以及前置成本拦截与运行期 `ChatResponse` `Usage` 超限熔断（第 1 次调用超限后立即阻断第 2/3 次调用并记录 `BUDGET_EXCEEDED`）。
6. `timeoutCancelsUnderlyingMultiAgentTasksAndPreventsSubsequentLlmCalls`：同时验证 `DEBATE` 与 `SWARM` 超时后底层阻塞线程收到中断信号，后续阶段调用次数严格为 `0`，记录 `SUPERVISOR_TIMEOUT` 并回退 `SINGLE`。
7. `boundedTtlMapAndCrossRequestBlackboardIsolationEnforceCapacityLimit`：验证连续写入 12 次请求后容量严格控制在上限 `5`，最旧记录 `1..7` 被 LRU 淘汰，最新记录 `8..12` 保留且黑板事实跨请求零串扰。

---

## 五、残余风险与限制

1. **底层 HTTP Socket I/O 中断响应依赖客户端实现**：`tracker.cancelAll()` 通过 `Thread.interrupt()` 与 `CancellableBudgetedChatModel#ensureActive` 能够 100% 阻止超时后发起任何新的多智能体阶段调用或工具调用；但对于正处于底层阻塞 Socket 读取中的单次 HTTP 请求，其物理连接立即断开的速度取决于底层 HTTP 客户端对线程中断信号的响应。
2. **多智能体合成流式粒度**：当前 `SWARM`（Supervisor 汇总）与 `DEBATE`（Judge 终审）在完成内部多角色协同后，通过 `emitSingleAnswer` 按 `StreamingChunk -> ModelFinished -> DoneSignal` 顺序一次性发射最终答案，首字节时间（TTFT）高于 `SINGLE` 逐 token 流式输出，因此必须维持默认关闭（`hermes.orchestration.enabled=false`），仅对复杂多步分析型 Bot 按需开启。
3. **生产启用授权边界**：本次返修仅完成代码缺陷修复与离线契约验证；线上开启 `hermes.orchestration.enabled=true`、Bot 级灰度放量、A/B 实验及参数调优仍须独立审批授权。

---

## 六、是否建议进入真实模型 Smoke

- **建议结论**：**建议准入真实模型（`deepseek-flash`）小样本受控 Smoke 测试**。
- **准入理由**：[`12-acceptance-review.md`](12-acceptance-review.md) 中指出的全部 6 项 P0 缺陷与耦合/测试问题已全部闭环修复，真实工具链回调、有界 LRU+TTL 内存保护、DeepSeek 官方分时/缓存/输出预算门禁、超时取消阻断以及默认 `false` 全局开关均已通过契约测试与全量 2057 条回归测试验证。
