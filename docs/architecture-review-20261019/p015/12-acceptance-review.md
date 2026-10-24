# P0-15 工作区实现验收报告

> 验收对象：当前 git 工作区未提交实现（基线 `7b396909`，HEAD 未提交）
> 验收方式：只读 diff 审查 + 全量 Maven 测试 + 静态缺陷审计
> 日期：2026-10-23
> 结论：**REQUEST CHANGES（不通过）**。编译与测试通过，但当前实现不能作为生产多智能体接线验收。

## 1. 工作区范围

已修改：

- `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto`
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java`
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`（+599 行）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java`

新增：

- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/LlmDebateCoordinator.java`
- `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java`
- `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java`（与上一份重复）

## 2. 运行验证

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -pl tests -am test
```

结果：

- `BUILD SUCCESS`
- `qknow-tests`：1917 tests / 0 fail / 0 error
- `qknow-hermes-core`：106 tests / 0 fail / 0 error
- 合计：2023 tests / 0 fail / 0 error
- `git diff --check`：无空白错误

但测试通过不能覆盖以下生产缺陷；其中多项是测试 mock 方式导致的假绿。

## 3. 必须修复的 P0 缺陷

### P0-1 SWARM Worker 绕过工具与 ReactAgent，实际没有检索/工具能力

证据：`AgentOrchestrator.java:1742-1795`。

`createRequestScopedSwarmWorkers` 用匿名子类覆盖 `WorkerAgent.chat`，内部：

```java
ChatResponse response = chatModel.call(new Prompt(workerMessages));
```

- `whitelistedTools` / `whitelistedResolver` 只传入构造器，覆盖方法完全未使用；
- 没有调用 `ReactAgent`、工具回调或 `WorkerAgent.chat` 默认实现（仅在空结果时 `super.chat`）；
- 三个 Worker 使用相同 `systemPrompt`，只有 name/desc 不同；
- 因此多智能体路径在真实环境中**不能执行知识库检索、MCP 工具或任何 ReAct 工具调用**，只是三次普通 LLM 调用。

这直接否定了“SWARM 复用真实 Worker/工具/RAG”的核心假设。现有契约测试 mock 了 `ChatModel`，无法发现该问题。

### P0-2 无界会话/凭单 Map，生产内存泄漏

证据：

- `AgentOrchestrator.java:100-105`：`orchestrationReceipts`、`lastSwarmBlackboardByRequest`、`lastDebateReceiptByRequest` 为 `ConcurrentHashMap`，只 put，无 eviction/TTL/remove；
- `SupervisorAgent.java:54,201`：`sessionBlackboardSnapshots` 只 put；
- `LlmDebateCoordinator.java:34,171`：`sessionReceipts` 只 put，且 receipt 保存 proposer/critic/final 全文。

高并发下会持续增长并持有用户问题、检索片段、完整答案，属于生产级内存与隐私风险。

### P0-3 成本预算公式错误，无法守住 0.05 元门禁

证据：`AgentOrchestrator.java:1523-1539`。

- `estimatedCostCny = baseTokens * 3.0 * 0.000002`，等价于 **6 元/百万 token 的统一价**；
- 未区分官方 `deepseek-flash` 的缓存命中/未命中输入（0.02/0.04 vs 1/2 元/百万）与输出（4/8 元/百万）；
- 未计入 completion tokens，也未计入 Supervisor planner/aggregate 与 debate 的所有调用；
- `maxCostCny` 仅从请求参数读取，默认 0.05 元；预算判断与实际成本不一致，可能放行高成本请求。

### P0-4 超时只 cancel Future，不能可靠终止底层 LLM 调用与线程

证据：`AgentOrchestrator.java:1590-1630`（SWARM）、`1662-1700`（DEBATE）。

- `future.cancel(true)` 不保证 `CompletableFuture.supplyAsync` 的底层线程停止；`SupervisorAgent`/`LlmDebateCoordinator` 的阻塞 ChatModel 调用可能继续执行；
- 回退 SINGLE 后会与仍在运行的 SWARM/DEBATE 调用并发，产生双倍 token/费用与延迟；
- 没有每请求的 token 消耗中断 / 取消信号传递。

### P0-5 全局开关默认开启且不接 Spring 配置

证据：`AgentOrchestrator.java:1451-1452`：

```java
boolean globallyEnabled = !"false".equalsIgnoreCase(
        System.getProperty("hermes.orchestration.enabled", "true"));
```

- 默认 `true`，不是 fail-safe；虽然默认模式是 SINGLE，但“全局开关”语义应默认关闭；
- 使用 `System.getProperty` 而非 `application.yml`/`@Value`，部署环境难以配置；
- 没有明确的启动期 feature flag 校验。

## 4. P1 质量与可维护性问题

1. **AgentOrchestrator 膨胀**：单文件新增约 599 行，把编排、profile、预算、worker 构造、回退、receipt 全塞进已有类；建议抽出 `MultiAgentOrchestrationService`/`OrchestrationProperties`，Orchestrator 只保留路由。
2. **重复测试**：`backend/qknow-hermes-core/src/test/.../OrchestrationWiringContractTest.java` 与 `backend/tests/.../OrchestrationWiringContractTest.java` 同名同职责，各 7 tests；维护双份断言，必须删除一份。
3. **测试覆盖不足**：现有测试只 mock `ChatModel`，没有验证：
   - Worker 是否真实调用工具；
   - 工具白名单是否拦截越权；
   - 请求级黑板是否与并发请求隔离；
   - 超时后是否停止 LLM 调用/不重复计费；
   - 预算门禁在真实 token 下是否生效；
   - `parameters` 到控制面 `ChatRequest` 的 builder 集成。
4. **角色同质化**：三个 Worker 使用同一 systemPrompt，WorkerAgent 的 name/description 未进入 prompt，与“RAG/分析/综合”角色设计不符。
5. **上下文/租户**：Worker 与 Debate 未显式传递 workspace/tenant/user 约束；仅依赖工具白名单，审计与权限证明不足。
6. **审计凭单无上限**：`OrchestrationExecutionReceipt` 只记录最终状态，但 Map 无容量限制；也没有暴露给监控/持久化。
7. **测试导入冗余**：测试中 `ToolCallback`/`ToolDefinition` 等未使用；不影响构建但显示实现未完成。
8. **proto 双字段**：`parameters` 与 `orchestration_mode` 同时新增，控制面同时解析字符串与枚举；需明确唯一事实源，避免配置漂移。

## 5. 代码质量评分

| 维度 | 分数 | 说明 |
|---|---:|---|
| 编译与回归 | 8/10 | 全量编译、2023 tests 通过；但测试含重复且 mock 覆盖不足 |
| 默认 SINGLE 零回归 | 7/10 | 分支默认 SINGLE，测试通过；全局开关默认 true 扣分 |
| 多智能体功能正确性 | 3/10 | Worker 绕过工具/ReactAgent，SWARM 实际无 RAG/工具能力 |
| 状态与内存安全 | 2/10 | 四个无界 Map，receipt 持有全文 |
| 成本/容量治理 | 2/10 | 公式错误、未计输出/缓存、超时不能停止底层调用 |
| 可维护性 | 3/10 | Orchestrator +599 行、重复测试、角色同质 |
| 安全/租户 | 5/10 | 有工具白名单代码，但 Worker 未使用；租户上下文缺失 |
| 测试质量 | 4/10 | 契约测试有价值，但 mock 无法证明真实工具链路；重复测试 |

**综合：4.5/10，Request Changes。**

## 6. 建议修复顺序

1. 先修 P0-1：Worker 必须走真实 `WorkerAgent`/`ReactAgent` + 白名单工具，不允许直接 `chatModel.call` 绕过工具；补工具调用断言。
2. 修 P0-2：所有 Map 加 TTL/容量上限，或改为请求级局部对象；凭单不保存全文。
3. 修 P0-3：按官方价格计算 input/output/缓存；预算使用实际 usage 或保守上界；补预算拒绝测试。
4. 修 P0-4：可取消的阻塞调用/超时后终止；禁止超时后继续 LLM 调用。
5. 修 P0-5：`application.yml` 默认关闭的 `hermes.orchestration.enabled`；删除 `System.getProperty(...,"true")`。
6. 删除重复测试；补真实工具/黑板/预算/超时测试；跑全量并记录精确计数。
7. 可选但建议：抽出 `MultiAgentOrchestrationService` 与 `OrchestrationProperties`，降低 Orchestrator 耦合。

## 7. 验收结论

- **构建/测试：PASS**
- **生产代码质量：FAIL**
- **验收裁定：REQUEST CHANGES**
- 当前工作区代码不应提交/合并；完成上述 P0 修复并重新验收后才可进入真实模型 smoke。
