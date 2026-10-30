# Hermes Harness Stage 7 实施与验证

## 结论

Stage 0 已在 `feat/hermes-harness-stage6` 独立提交 `19b6e647`。Stage 7 在 `feat/hermes-harness-stage7` 完成 `OFF / SHADOW / ENFORCE` 桥接与确定性测试；未修改 `main`，未调用付费模型或外网。当前结论是**代码与离线回归门禁通过**，不等于真实模型或生产推广验收。

## 生产接入

- 默认 `hermes.harness.kernel.enabled=false`、`mode=OFF`：`AgentOrchestrator` 跳过桥接，不创建会话、不改提示词、工具或 resolver。
- `SHADOW`：能力预检失败只记回执，原始模型和工具执行继续。
- `ENFORCE`：在 `createPlannerSupervision` 和 `planAndSolve` 之前完成能力预检；失败发 `ErrorEvent(403)`，记录 `FAIL_SAFE` 凭单，模型调用数为零。成功后按请求隔离冷备与循环状态，并包装工具执行。
- 预检异常对外仅返回固定错误类别，不回显可能包含请求载荷的解析器消息。
- 工具包装从现有 `ToolDefinition.inputSchema()` 提取顶层必填项和基本 JSON 类型，结合循环窗口、步数与四维护栏执行前置守卫；schema 不可解析时拒绝调用。后置守卫拒绝时不返回工具原始输出。resolver 仅允许本请求挂载或声明的工具。
- 回执保存在容量 256 的线程安全访问顺序映射，流完成或取消时汇总轨迹；不写入历史实验输出。

## 与冻结任务书的源码适配

| 任务书示例 | 实际实现 |
|---|---|
| `ToolContract`、`CycleInspection` | 既有 `HoareTripleToolGuard.Contract`、`LocallyDenseCycleAutomaton.Inspection` |
| `Centroid.meanSquaredChordalLoss()` | 既有 `Centroid.chordalLoss()`，保留 `frechetMean()` 兼容委托 |
| `Episode.episodeId()` / 冷区队列 | 既有 `Episode.id()` / token-keyed 冷区映射 |
| `HarnessEvidenceCollector.collect(...)` | 既有 `finalizeEvidence()`，新增带 `ExecutionMode` 的重载 |
| `ChatRequest` 普通 Java DTO / `agent.orchestrator` 路径 | 实际为 protobuf `ChatRequest` / `agent.AgentOrchestrator` |
| `session.evaluateTrajectory(ragScore)` | 现有 `TrajectoryEvidence` 计数接口；当前桥接只汇总工具失败率，`ragScore` 未进入归因 |

冷备归档满额时，`add` 在改动状态前拒绝新记录；已有 token 仍可读，但不提升到热区。这一有界、非破坏性选择比任务书示例的“弹出后拒绝”更严格。`CapabilityTag.COMPOSITE` 实际要求 RAG 与 TOOL，因此 RAG 加历史但无工具自动推断为 RAG。

## 验证

使用隔离 Java 21：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。

| 命令 | 结果 |
|---|---|
| `mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am -Dtest=HermesHarnessKernelArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test` | Stage 0：11 项，0 failure，0 error |
| `mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am -Dtest=HermesHarnessStage7BridgeTest,HermesHarnessKernelArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test` | Stage 7：20 项，0 failure，0 error |
| `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` | 43 模块 BUILD SUCCESS，0 failure，0 error |

全量测试按本次 clean 生成的 `TEST-*.xml` 统计，排除不属于 Reactor 的陈旧 `qknow-server` 报告：

| 轨道 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `qknow-hermes-core` | 144 | 0 | 0 | 0 |
| `qknow-tests` | 1943 | 0 | 0 | 8 |
| 其余 Reactor 依赖模块 | 41 | 0 | 0 | 0 |
| **合计** | **2128** | **0** | **0** | **8** |

第一次全量 clean 与另一个定向 Maven 构建重叠，proto `target/classes` 同时重建导致 `NoSuchFileException`，该次无效。停止并行 Maven 后串行重跑即为上表成功结果；没有因此修改 proto 源码。任务书给出的 Stage 4/5 checksum 目录名与仓库实际目录不符；已在 `experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic` 和 `experiment/outputs/harness5/run-20261030-stage5-real` 分别运行 `shasum -a 256 -c checksums.sha256`，两组各 11 项均 OK。`50-harness7-taskbook.md.sha256` 校验 OK，`git diff --check` 通过。

## 剩余边界

- 工具 schema 只验证顶层类型、必填项及 `additionalProperties=false`；嵌套 schema、数值范围和业务边界没有通用执行契约，不将其宣称为完整的工具安全认证。
- 步数和局部循环频次来自请求级会话；token/cost 估计仍取自工具调用参数，尚无可信的模型使用量回传，因此不能宣称四维预算已具备抗绕过的生产强制力。
- `ragScore` 尚未参与 Stage 7 轨迹归因；真实 DeepSeek 与真实工具 E2E、性能和生产推广均未执行。
- 本次只实现 Harness Kernel 桥接，不解封 MCP、HITL，也不合入 `main`。
