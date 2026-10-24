# P0-15 返修后验收复核（第二轮）

> 对象：`13-p015-rework-report.md` 对应工作区实现（基线 `7b396909`）
> 方式：只读 diff + 独立全量测试 + 缺陷复核
> 日期：2026-10-23
> 结论：**CONDITIONAL PASS（返修 P0 基本通过；尚不能标为生产验收通过）**

## 1. 运行验证

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -pl tests -am clean test
```

- 编译：`BUILD SUCCESS`
- `qknow-tests`：**1917 tests / 1 failure / 0 error / 8 skipped**
  - 唯一失败：`Phase111MemoryConsolidationTest#testSleepTimeConcurrentMessageZeroLoss`
  - 失败断言：`expected:<SUCCESS> but was:<FAILED>`，属于已知时序 flaky（前几轮曾出现，隔离重跑通过）
- 隔离重跑该测试：`BUILD SUCCESS`（5 tests / 0 fail）
- 非 clean 全量重跑：`qknow-tests 1917 / 0 fail`、`qknow-hermes-core 99 / 0 fail`
- 结论：**全量测试并非稳定 0 fail**；“全绿”依赖重跑；返修报告中的 0 fail 不能从一次 clean 全量复现。

## 2. 返修 P0 复核

| 原 P0 | 复核结果 | 关键证据 |
|---|---|---|
| P0-1 Worker 绕过工具/ReactAgent | **通过** | `MultiAgentOrchestrationService#createRequestScopedSwarmWorkers` 已改为标准 `WorkerAgent`；测试 `swarmWorkersExecuteRealReactAgentToolCallsAndHaveDifferentiatedPrompts` 用 mock `ToolCallback` 断言真实工具调用，未授权工具未触达 |
| P0-2 无界 Map/全文驻留 | **通过** | 新增 `BoundedTtlMap`；`orchestrationReceipts`、`lastSwarmBlackboard`、`lastDebateReceipt`、`SupervisorAgent` 快照、`LlmDebateCoordinator.sessionReceipts` 均改为有界 LRU+TTL；`DebateReceipt` 只保存长度/SHA-256 |
| P0-3 成本预算公式 | **基本通过** | 已按官方价格区分缓存命中/未命中/输出；前置估算与运行期 `Usage` 熔断；测试覆盖预检拒绝与运行期超限 |
| P0-4 超时取消 | **通过（有残余风险）** | `OrchestrationCallTracker` 注册/中断在途线程，`CancellableBudgetedChatModel` 入口检查取消；测试验证超时后无后续 Critic/Judge/Worker 调用 |
| P0-5 全局开关默认开启 | **通过** | `OrchestrationProperties.enabled` 默认 false；`application.yml` 默认 `${HERMES_ORCHESTRATION_ENABLED:false}`；测试覆盖默认关闭 |
| P1 耦合/重复测试 | **通过** | `AgentOrchestrator` 仅保留委托；删除 `qknow-hermes-core` 重复测试，唯一测试在 `qknow-tests` |

## 3. 仍存在的 P1/残余问题

1. **全量测试 flaky 未消除**：`Phase111MemoryConsolidationTest` 在 clean 全量中失败、隔离重跑通过；返修报告声称 0 fail 的结论不稳健。需要加确定性同步/重试或定位真正的时序缺陷，不能靠重跑掩盖。
2. **真实模型/真实工具 smoke 缺失**：7 条契约测试全部使用 mock `ChatModel` 与 mock `ToolCallback`。P0-1 只证明“代码路径会调用工具”，没有证明真实 DeepSeek + 真实 MCP/知识库在 SWARM 下可端到端工作。
3. **运行期成本记账偏乐观**：`OrchestrationCallTracker` 对 `seq > 1` 的调用按 `min(inTokens, staticSystemTokens)` 计为缓存命中；若实际未命中，实际成本被低估，0.05 元门禁可能失效。建议预算阶段一律按未命中保守计价，或读取真实 cache 命中字段。
4. **`CancellableBudgetedChatModel.stream()` 未接并发信号量与线程注册**：只覆盖了 `call()` 路径；若未来 ReactAgent/框架走 streaming，取消与并发上限可能绕过。需补测试或显式拒绝 stream。
5. **Spring 配置绑定未做集成验证**：`OrchestrationProperties` 测试都是手动 new；没有 `@SpringBootTest`/`ApplicationContextRunner` 验证 `hermes.orchestration.enabled`、timeout、max-workers 从 yml/env 真正绑定。
6. **`AgentOrchestrator` 默认 `new MultiAgentOrchestrationService()`**：若 Spring setter 注入失败，会静默使用默认实例（enabled=false），排查困难；建议改为构造器注入或 `ObjectProvider` 显式失败。
7. **`SupervisorAgent` 的 `sessionQualityReceipts`/快照虽已 bounded，但 `SwarmLoopGuard` 等旧状态仍需确认在异常路径 finally 清理**（现有代码多数已清理，建议补异常路径测试）。
8. **P0-1 三个 Worker 的“能力画像”是名称匹配启发式**：`resolveWorkerCapabilities` 依赖 Worker 名称含 RAG/ANALYSIS/SYNTHESIS；可工作但脆弱，建议改为显式 role→capability 映射，避免改名回归。

## 4. 代码质量评分（第二轮）

| 维度 | 分数 | 说明 |
|---|---:|---|
| 编译与回归 | 7/10 | 编译通过；clean 全量有 1 个已知 flaky |
| 默认 SINGLE 零回归 | 8/10 | 开关默认 false，测试通过 |
| 多智能体功能正确性 | 7/10 | Worker 已走 ReactAgent；真实链路未验证 |
| 状态与内存安全 | 8/10 | BoundedTtlMap 覆盖主要 Map；旧 `SwarmLoopGuard` 需异常路径复核 |
| 成本/容量治理 | 6/10 | 官方价格已接；缓存命中记账偏乐观，stream 未覆盖 |
| 可维护性 | 7/10 | Orchestrator 已解耦；角色能力仍靠名称启发式 |
| 安全/租户 | 6/10 | 工具白名单与上下文头已加；真实租户隔离/权限未做 E2E |
| 测试质量 | 6/10 | 契约测试明显增强；mock-only、flaky、无 Spring binding/真实 smoke |

**综合：6.8/10；CONDITIONAL PASS（可进入真实链路 smoke，不可直接宣称生产验收通过）。**

## 5. 验收裁定

- **代码结构/默认路径**：通过。
- **P0 返修**：基本通过。
- **生产验收**：**未通过**，阻塞项为：
  1. clean 全量测试稳定性（flaky）；
  2. 真实 DeepSeek + 真实工具/RAG 的 SWARM/DEBATE smoke；
  3. 运行期成本记账保守性与 stream 路径覆盖；
  4. Spring 配置绑定集成验证。

建议先执行 `15-p015-real-smoke.md`，再决定是否 commit/合并。
