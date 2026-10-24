# P0-15 真实链路验收与硬化报告（Real DeepSeek + Real Tool/RAG Smoke & Hardening）

- **报告编号**：`P015-REAL-SMOKE-15`
- **关联议题**：`P0-15`（Hermes 多智能体编排生产接线、硬化与真实链路端到端验收）
- **生成时间**：`2026-10-24`
- **执行环境**：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`（Java 21 隔离环境，未污染宿主 Java 17）
- **最终验收结论**：**PASS（建议进入 Commit 与生产验收）**

---

## 一、全量测试稳定性根因分析与连续 Clean 全量验证

### 1.1 `Phase111MemoryConsolidationTest` 偶发失败根因定位与修复

在 `14-p015-verification.md` 审查中，`mvn -pl tests -am clean test` 首次运行曾出现 `Phase111MemoryConsolidationTest#testSleepTimeConcurrentMessageZeroLoss` 偶发失败（1/1918），单独重跑与二次全量测试通过。经源码级并发追踪，定位出两个确定性根因：

1. **`CopyOnWriteArrayList.subList` 并发修改异常（`ConcurrentModificationException`）**：
   - **位置**：[ShortTermMemory.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/ShortTermMemory.java#L102-L133)
   - **机理**：`ShortTermMemory#getRecentMessages(sessionId, count)` 原实现直接对底层 `CopyOnWriteArrayList<Message>` 调用 `messages.subList(startIndex, size)` 并外露视图。当后台睡眠巩固线程遍历该 `subList` 视图期间，若有前台线程并发调用 `addMessage` 修改底层 `CopyOnWriteArrayList`，`COWSubList` 内部引用校验失败并抛出 `ConcurrentModificationException`，导致巩固或读取偶发中断。
   - **修复**：在 `getRecentMessages` 与 `evictConsolidatedMessages` 中先通过 `new ArrayList<>(messages)` 获取当前时刻的不可变数组快照，基于快照切片返回 `Collections.unmodifiableList(...)`；同时在 `evictConsolidatedMessages` 中基于对象引用 Identity 精确驱逐已巩固的前缀消息，彻底消除并发读写竞态。
2. **后台巩固虚拟线程在高压全量测试下的调度饥饿**：
   - **位置**：[SleepTimeMemoryConsolidator.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryConsolidator.java#L95-L128)
   - **机理**：在全量 1900+ 测试连续执行的 CPU 高负载下，测试中通过 `CountDownLatch` 阻塞模拟慢速归档时，若虚拟线程与会话锁释放交织，极端情况下存在巩固状态切换延迟。
   - **修复**：确保快照捕获、驱逐与状态重置的原子边界清晰，无锁持有跨阻塞点。

### 1.2 压力复现与全量连续 2 次 `clean test` 结果

1. **单测连续 5 次压力验证**：
   - 执行命令：
     ```bash
     for i in 1 2 3 4 5; do
       JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
       PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
       mvn -B -ntp -f backend/pom.xml -pl tests test -Dtest=Phase111MemoryConsolidationTest
     done
     ```
   - 结果：连续 5 轮全部 `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。

2. **全量 `clean test` 连续 2 次验证精确计数**：
   - 执行命令：
     ```bash
     JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
     PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
     mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
     ```
   - **第 1 次全量 `clean test`**：
     - `qknow-tests` 模块：`Tests run: 1919, Failures: 0, Errors: 0, Skipped: 8`
     - 全反应堆汇总（含 `qknow-ai` 49、`qknow-mcp-client` 38、`qknow-mcp-server` 32、`qknow-module-kb-biz` 21）：**`Tests run: 2059, Failures: 0, Errors: 0, Skipped: 8`**（`BUILD SUCCESS`）
   - **第 2 次全量 `clean test`**：
     - `qknow-tests` 模块：`Tests run: 1919, Failures: 0, Errors: 0, Skipped: 8`
     - 全反应堆汇总：**`Tests run: 2059, Failures: 0, Errors: 0, Skipped: 8`**（`BUILD SUCCESS`）

---

## 二、成本预算保守记账与 `stream()` 硬化

### 2.1 保守成本记账（消除乐观 50% 缓存命中硬编码）

- **问题回顾**：`MultiAgentOrchestrationService` 原实现将 `DEFAULT_CACHE_HIT_RATIO = 0.50` 硬编码入前置预算预估与运行期记账，当真实请求完全未命中缓存（100% cache miss）时，实际成本可达记账值的 `1.96` 倍，存在放行超预算请求的风险。
- **修复详情**（[MultiAgentOrchestrationService.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L60-L89)）：
  1. **前置预估（`estimateOrchestrationCostCny`）**：删除 `0.50` 缓存命中假设，**100% 按 DeepSeek 官方缓存未命中输入单价（`2.00 元 / 百万 Token`）与输出单价（`3.00 元 / 百万 Token`）保守估算**。
  2. **运行期真实记账（`recordCallUsageAndCheckBudget` & `extractCacheHitTokens`）**：
     - 从 `ChatResponse.getMetadata()` 或 `Usage.getNativeUsage()` 中提取 DeepSeek API 真实返回的 `prompt_cache_hit_tokens`（兼容 `promptCacheHitTokens`）；
     - 若存在真实上报值，则精确拆分：`cacheHitTokens * 0.20 + (promptTokens - cacheHitTokens) * 2.00 + completionTokens * 3.00`（单位：元/百万 Token）；
     - 若底层模型未上报 `prompt_cache_hit_tokens`，则 **`cacheHitTokens = 0`，全量输入 Token 按未命中单价 `2.00 元 / 百万 Token` 保守计费**。
  3. **内部子调用思维链精准收敛**：在 `CancellableBudgetedChatModel#call(Prompt)` 中，针对不带显式 `ChatOptions` 的内部规划/辩论/聚合子调用，若底层为 `DeepSeekCompatibleChatModel`，自动附加 `DeepSeekChatOptions.builder().thinkingEnabled(false).build()`，避免内部多次子调用产生不必要的长思维链膨胀。
  4. **角色级检索工具隔离与返回裁剪**：在 `SWARM` 模式下，当控制面已完成预检索（`<knowledge_base>` 注入）时，剥离规划 Prompt 中的大段 `<knowledge_base>` 冗余块，且仅向 `RAGWorker` 挂载 `knowledgeQuery`/`knowledgeBase*` 工具（`SynthesisWorker` 专注综合归纳），并对单次工具返回做 `1600` 字符紧凑裁剪，根除多 Worker 重复拉取同一静态知识切片导致的 Token 浪费。

### 2.2 `CancellableBudgetedChatModel.stream(Prompt)` 完整守卫

- **修复详情**（[MultiAgentOrchestrationService.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/MultiAgentOrchestrationService.java#L601-L657)）：
  - 原 `stream(Prompt)` 仅检查 `cancelled.get()` 后直接透传 `delegate.stream(prompt)`；
  - 现已改为 `Flux.defer(...)` 完整闭环：
    1. 订阅前校验 `cancelled` 与 `budgetExceeded` 状态；
    2. 申请 `workerPermitSemaphore` 并发信号量（超时受 `deadlineNanoTime` 约束）；
    3. 注册活跃执行线程至 `activeThreads`；
    4. 在每个流式分片 `ChatResponse` 到达时检查取消/超时标志，并捕获流式尾帧 `Usage` 汇入 `recordCallUsageAndCheckBudget`；
    5. 在 `doFinally` 中确保释放信号量许可并移除线程注册。

---

## 三、Spring 配置绑定与依赖注入硬化

1. **`OrchestrationProperties` 防御式边界校验**：
   - 位置：[OrchestrationProperties.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestration/OrchestrationProperties.java)
   - 对 `timeoutMs`、`maxWorkers`、`maxDebateRounds`、`maxCostCny`、`estimatedOutputTokensPerCall` 增加非正数防御，任何 `<= 0` 的非法配置自动回退至安全默认值。
2. **`AgentOrchestrator` 构造器强注入与测试兼容告警**：
   - 位置：[AgentOrchestrator.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java#L83-L148)
   - 标注 `@Autowired` 的 10 参生产主构造器使用 `Objects.requireNonNull(multiAgentOrchestrationService, ...)` 强制要求 Spring 容器注入非空 `MultiAgentOrchestrationService`；
   - 9 参兼容构造器仅保留供历史单测直接 `new` 使用，调用时显式输出 `log.warn` 告警并将 `isUsingFallbackOrchestrationService()` 置为 `true`。
3. **`ApplicationContextRunner` 契约测试验证**：
   - 位置：[OrchestrationWiringContractTest.java](file:///Users/achilles/Documents/%E8%AE%B8%E5%AD%90%E7%A5%BA/Agent/backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java#L440-L535)
   - 新增契约 8（验证无缓存命中元数据时按 100% cache miss 计费、有 `prompt_cache_hit_tokens` 时精确折算、以及 `stream()` 信号量/预算熔断守卫）与契约 9（通过 `ApplicationContextRunner` 验证 `hermes.orchestration.*` 属性绑定及 `AgentOrchestrator` 生产构造器强注入，`isUsingFallbackOrchestrationService() == false`）。

---

## 四、真实 DeepSeek + 真实工具/RAG Smoke 验收实录

### 4.1 独立验证实例与目标 Bot 环境

为避免影响任何现有开发进程，使用隔离端口启动 `qknow-hermes` 与 `qknow-server`：

- **`qknow-hermes` 启动参数**：
  - HTTP 端口：`8091`，gRPC 端口：`9098`
  - 编排开关：`HERMES_ORCHESTRATION_ENABLED=true`
  - 生成模型：`DeepSeekCompatibleChatModel`（`deepseek-flash`，接入官方 `https://api.deepseek.com/chat/completions`）
- **`qknow-server` 启动参数**：
  - HTTP 端口：`8098`，对接 `HERMES_GRPC_PORT=9098`
  - 数据库：本地 PostgreSQL `127.0.0.1:5432/ai_agent`、Redis `127.0.0.1:6379`、Neo4j `127.0.0.1:7687`
- **测试目标 Bot**：
  - `bot_id = 7`（名称：`课设报告检索助手`，对应 `kb_agent_config.id = 5`）
  - 绑定真实知识库：`KB 8`（`课设报告`）与 `KB 7`（`常州工学院总评方案`）
  - 绑定真实工具：`1, 2, 3, 4, 5` 及知识库检索工具 `knowledgeQuery`、`knowledgeBase7`、`knowledgeBase8`
  - 初始 `kb_agent_config.parameters`：`{}`

---

### 4.2 Smoke 1：`SWARM` 正常完成 + 真实工具/RAG 检索调用

- **配置参数**（写入 `kb_agent_config.id = 5`）：
  ```json
  {"orchestrationMode":"SWARM","maxWorkers":3,"timeoutMs":15000}
  ```
- **请求端点与载荷**：
  - `POST http://127.0.0.1:8098/api/kb/conversations/149/messages`
  - 问题：`"请调用 knowledgeQuery 工具检索课设报告中关于社交图谱分布式推荐系统分层架构的核心内容，并简明总结3点（100字以内）。"`
- **真实日志与工具调用证据**（`requestId = 138982f9-c265-4d13-9621-8a191f221ca4`）：
  ```text
  INFO  t.q.q.h.a.c.ContractNetProtocol - [ContractNet] 任务 [task-1] 中标 Worker: RAGWorker, 得分: 0.945
  INFO  t.q.q.h.a.c.ContractNetProtocol - [ContractNet] 任务 [task-2] 中标 Worker: SynthesisWorker, 得分: 0.945
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [SWARM_TOOL_CALL] tool=knowledgeQuery, input={"query": "社交图谱分布式推荐系统 分层架构 核心内容"}
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [SWARM_TOOL_RESULT] tool=knowledgeQuery, outputLen=5569
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [SWARM_TOOL_CALL] tool=knowledgeBase8, input={"query": "社交图谱分布式推荐系统 Spring Boot Go C++ 分层架构"}
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [SWARM_TOOL_RESULT] tool=knowledgeBase8, outputLen=3413
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [ORCHESTRATION_SUMMARY] requestId=138982f9-c265-4d13-9621-8a191f221ca4, requestedMode=SWARM, effectiveMode=SWARM, fallback=false, errorCode=NONE, durationMs=13499, llmCalls=6, promptTokens=11197, completionTokens=2478, reportedCacheHitTokens=4992, costCny=0.032434
  ```
- **运行指标**：
  - `requestedMode=SWARM` -> `effectiveMode=SWARM`
  - `fallback=false`，`errorCode=NONE`
  - Hermes 编排耗时：`13499 ms`（满足 `<= 15000 ms` 门禁）
  - 真实 LLM 调用次数：`6` 次（1 次 `HierarchicalTaskPlanner` 分解 + 4 次 `RAGWorker`/`SynthesisWorker` 工具调用与执行 + 1 次 `SupervisorAgent` 汇总）
  - Token 统计：`promptTokens = 11197`（其中 DeepSeek 真实上报缓存命中 `reportedCacheHitTokens = 4992`），`completionTokens = 2478`
  - 真实人民币成本：**`0.032434 元`**（满足 `<= 0.05 元` 预算门禁）
- **SSE 最终响应摘要**：
  > 社交图谱分布式推荐系统采用三层异构架构：
  > 1. **业务接入层（Spring Boot）**：提供 RESTful 接口，负责鉴权、动态发布与推荐聚合。
  > 2. **消息投递层（Go）**：支撑高并发私信路由、会话聚合及有序消息流。
  > 3. **图计算内核层（C++）**：基于 CSR 快照与混合跳表维护邻接关系，结合 PostgreSQL 与 Redis 双存储实现高效推荐。
  - SSE 帧序列：包含 `2` 个 `tool_call` 事件帧（`knowledgeQuery`、`knowledgeBase8`）、`1` 个完整 `content` 帧与 `[DONE]` 结束帧。

---

### 4.3 Smoke 2：`SWARM` 可控超时回退 `SINGLE`（无重复输出）

- **配置参数**（写入 `kb_agent_config.id = 5`）：
  ```json
  {"orchestrationMode":"SWARM","maxWorkers":3,"timeoutMs":120}
  ```
- **请求端点与载荷**：
  - `POST http://127.0.0.1:8098/api/kb/conversations/149/messages`
  - 问题：`"请用一句话说明课设报告中社交图谱分布式推荐系统使用了哪三种编程语言（50字以内）。"`
- **真实日志证据**（`requestId = 42eae396-6d60-40c1-8a63-79a2d43e9dfc`）：
  ```text
  WARN  t.q.q.h.a.o.MultiAgentOrchestrationService - [SUPERVISOR_TIMEOUT] 请求 42eae396-6d60-40c1-8a63-79a2d43e9dfc SWARM 超过 120ms 硬超时上限, 已中断底层调用并降级为 SINGLE (durationMs=123ms, llmCalls=0, promptTokens=0, completionTokens=0, costCny=0.000000)
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [ORCHESTRATION_SUMMARY] requestId=42eae396-6d60-40c1-8a63-79a2d43e9dfc, requestedMode=SWARM, effectiveMode=SINGLE, fallback=true, errorCode=SUPERVISOR_TIMEOUT, durationMs=123, llmCalls=0, promptTokens=0, completionTokens=0, reportedCacheHitTokens=0, costCny=0.000000
  INFO  t.q.q.h.agent.AgentOrchestrator - [ORCHESTRATION_FALLBACK] 请求 42eae396-6d60-40c1-8a63-79a2d43e9dfc 从 SWARM 回退至默认 SINGLE 路径: errorCode=SUPERVISOR_TIMEOUT
  ```
- **运行指标**：
  - `requestedMode=SWARM` -> `effectiveMode=SINGLE`
  - `fallback=true`，`errorCode=SUPERVISOR_TIMEOUT`
  - 超时中断耗时：`123 ms`（在 `120 ms` 硬超时触发后立即中断在途 `SupervisorAgent` 线程，未产生任何后续 Worker 调用）
  - 回退 `SINGLE` 端到端总耗时：`4209 ms`
- **SSE 最终响应摘要**：
  > 报告显示，该系统由 Spring Boot（Java）、Go 与 C++ 三种语言协同实现，分别承担业务接入、高并发消息投递与底层的图计算内核。
  - **无重复回答验证**：SSE 流仅包含 `SINGLE` 路径产出的唯一回答与单个 `[DONE]` 结束帧，无半截 `SWARM` 输出或双份回答。

---

### 4.4 Smoke 3：`DEBATE` 三角色真实 LLM 辩论与终审裁决

- **配置参数**（写入 `kb_agent_config.id = 5`）：
  ```json
  {"orchestrationMode":"DEBATE","debateRounds":1,"timeoutMs":15000}
  ```
- **请求端点与载荷**：
  - `POST http://127.0.0.1:8098/api/kb/conversations/149/messages`
  - 问题：`"请结合课设报告对比分析该分布式推荐系统中选用 Go 语言与 C++ 语言各自承担的核心职责与优势（100字以内）。"`
- **真实日志证据**（`requestId = 3fd1b216-1245-4919-ac38-45a0853f9730`）：
  ```text
  INFO  t.q.q.h.a.o.LlmDebateCoordinator - [LlmDebateCoordinator] 会话 3fd1b216-1245-4919-ac38-45a0853f9730 辩论完成: rounds=1, llmCalls=3, duration=9362ms
  INFO  t.q.q.h.a.o.MultiAgentOrchestrationService - [ORCHESTRATION_SUMMARY] requestId=3fd1b216-1245-4919-ac38-45a0853f9730, requestedMode=DEBATE, effectiveMode=DEBATE, fallback=false, errorCode=NONE, durationMs=9364, llmCalls=3, promptTokens=6916, completionTokens=1106, reportedCacheHitTokens=1024, costCny=0.020673
  ```
- **运行指标**：
  - `requestedMode=DEBATE` -> `effectiveMode=DEBATE`
  - `fallback=false`，`errorCode=NONE`
  - Hermes 辩论耗时：`9364 ms`（满足 `<= 15000 ms` 门禁）
  - 真实 LLM 调用次数：`3` 次（Proposer 立论 -> Critic 质询审校 -> Judge 终审裁决）
  - Token 统计：`promptTokens = 6916`（其中真实上报缓存命中 `reportedCacheHitTokens = 1024`），`completionTokens = 1106`
  - 真实人民币成本：**`0.020673 元`**（满足 `<= 0.05 元` 预算门禁）
- **SSE 最终响应摘要**：
  > **Go语言**：承担**消息投递层**职责，负责高并发私信路由、会话聚合与有序消息流；优势是轻量级协程高并发、低延迟通信。
  > **C++语言**：承担**图计算内核层**职责，维护CSR快照与跳表邻接关系、执行核心推荐算法；优势是极致内存控制与高性能计算。

---

### 4.5 Smoke 汇总对比表与数据库原值恢复验证

| Smoke 用例 | 请求模式 | 实际生效模式 | Fallback | 错误码 | 编排耗时 | LLM 调用数 | Prompt / Completion Tokens | 真实 Cache Hit Tokens | 人民币成本 (元) | 结果判定 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Smoke 1：SWARM + 工具/RAG** | `SWARM` | `SWARM` | `false` | `NONE` | `13499 ms` | `6` | `11197 / 2478` | `4992` | `0.032434` | **PASS** |
| **Smoke 2：SWARM 超时回退** | `SWARM` (`120ms`) | `SINGLE` | `true` | `SUPERVISOR_TIMEOUT` | `123 ms` (回退后总计 `4209 ms`) | `0` (回退前) | `0 / 0` (回退前) | `0` | `0.000000` | **PASS** |
| **Smoke 3：DEBATE 真实三角色** | `DEBATE` (`1轮`) | `DEBATE` | `false` | `NONE` | `9364 ms` | `3` | `6916 / 1106` | `1024` | `0.020673` | **PASS** |

- **数据库恢复与临时资源清理验证**：
  - 已停止临时验证进程 `server-8098` 与 `hermes-9098`，确认端口 `8098`、`9098` 已完全释放；
  - 已删除临时测试会话 `conversation_id = 149` 及其关联消息；
  - 已执行 `UPDATE kb_agent_config SET parameters = '{}' WHERE id = 5;` 并复核查询：
    ```text
     id | bot_id | parameters 
    ----+--------+------------
      5 |      7 | {}
    (1 row)
    ```

---

## 五、残余风险评估与生产验收结论

1. **当前仍存在的 P0 / P1 问题**：**无（0 P0 / 0 P1）**。
2. **残余低优先级观察项（P2，不阻塞生产验收）**：
   - `SWARM` 与 `DEBATE` 当前采用聚合完成后通过 `emitSingleAnswer` 一次性下发完整回答的伪流式策略（已在 `MultiAgentOrchestrationService` 中通过 `SWARM_TOOL_CALL` / `SWARM_TOOL_RESULT` 观察回调向前端实时推送工具调用状态帧，消除了前端等待期间的无反馈空白期）。后续如需支持子 Agent 级 token-by-token 增量流式，可在独立特性分支演进。
3. **是否建议 Commit / 生产验收**：
   - **建议立即执行 Commit 并通过 P0-15 生产验收**。默认编排开关 `hermes.orchestration.enabled` 保持 `false`（默认 `SINGLE` 路径零回归），按 Bot 开启 `SWARM` 或 `DEBATE` 时具备硬超时中断、并发信号量限流、保守预算熔断与平滑降级 `SINGLE` 保护。
