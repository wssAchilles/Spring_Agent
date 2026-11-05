# Stage 21A 实施任务书：最小 PVER

> **状态：范围冻结，待用户单独授权实施。** 本文只冻结 Stage 21A 契约，不授权源码实现；Stage 22A、Stage 23A 及所有扩展项均不在本授权范围内。
>
> **源码基线：** `main` 上的计划冻结提交 `4864109c`。实施时须从任务书冻结提交切出独立分支；不得在 `main` 实施。
>
> **路线图：** [91-harness21-cognitive-core-research-plan.md](91-harness21-cognitive-core-research-plan.md)。该文档仅作研究路线图；与本任务书冲突时，以本任务书为准。
>
> **正文 SHA-256：** `7b5048ab8813bfdf916ebb5c09e4a9c14a2e1dd79ad95dbe7437678b37665da6`（从 `## 一、` 起至文件末尾的 UTF-8 字节）。

## 一、唯一假设与目标

**唯一待验证假设 H1：** 对现有确定性复杂任务入口，加入结构化计划验证和严格顺序执行后，可以保证未经验证的步骤不执行、失败步骤后的步骤不启动，并产生与实际状态转换一致的请求级结构化 trace；关闭新开关时，现有执行路径保持不变。

当前失败机制限定为：现有 `AgentOrchestrator.executePlanTasks` 对同一就绪层默认使用 `CompletableFuture` 并行执行；现有反思路径 `reflectOnce` 可按 `maxReflectionRetries` 再调用模型；执行结果由 `WorkerAgent.chat` 以字符串返回。H1 不涉及答案质量、任务成功率提升或真实模型效果。

## 二、代码基线与集成边界

基于提交 `4864109c` 的源码核对结果：

| 位置 | 已确认事实 | Stage 21A 约束 |
|---|---|---|
| `AgentOrchestrator.executeAgent` | 先调用 planner supervision，再尝试 `planAndSolve`，未接管时进入 ReAct | 新路径必须在旧 planner supervision 之前分流，避免同一请求重复生成计划 |
| `PlanSolveConfig` | 配置前缀为 `qknow.hermes.plan-solve`；`enabled` 默认 `false`；已有复杂度关键词及 `maxTasks`（默认 5） | 保持旧开关和旧路径不变；新增独立 `qknow.hermes.cognitive-core.enabled=false` 开关 |
| `AgentOrchestrator.executePlanTasks` | 非 DFA 强制模式下，同一就绪层并行执行 | 不复用该并发执行方法 |
| `AgentOrchestrator.reflectOnce` | 使用 `AiJudgeService`，并可能触发额外模型调用 | 不复用；Stage 21A 反思为一次确定性结构归纳，不调用模型 |
| `WorkerAgent.chat` | 返回文本；运行异常会映射为 `执行失败:` 前缀 | 新增 typed 执行结果，`chat` 保持兼容；不得解析回答文本来判定运行状态 |
| `PlanAndSolveOrchestratorTest` | 覆盖复杂度规则、JSON 提取、环检测和配置默认值，未覆盖端到端顺序闭环 | 保留旧测试并补充新核心契约与接线测试 |

新开关默认关闭时，当前所有路由、模型调用、工具行为和回退行为必须保持不变。新开关开启且 `plan-solve.enabled=true` 时，须在认知规划或步骤执行前拒绝冲突配置，不允许静默选择其中一条路径。

新开关开启时，仅对现有 `isComplexQuestion` 判定为多步的请求运行 Stage 21A；该判定只作为路由门槛，不宣称具备语义意图识别能力。此路径跳过旧 planner supervision 和旧 `planAndSolve`，避免重复规划。

## 三、实施范围

### 3.1 主干代码

只在 `qknow-hermes-core/src/main/java` 实现以下最小组件：

| 类型 | 责任 |
|---|---|
| `CognitiveCoreConfig` | 绑定 `qknow.hermes.cognitive-core.enabled`，默认关闭 |
| `TaskAnalysis` | 记录请求是否进入多步路径及稳定原因码；复用现有复杂度门槛，不新增分类模型 |
| `PlanStep`、`Plan` | 保存不可变、显式排序的步骤及依赖 ID；不表示并行 wave/DAG 调度 |
| `PlanVerificationResult`、`PlanVerifier` | 在执行前检查非空计划、步数上限、ID 唯一、目标非空、依赖存在且只引用先前步骤 |
| `StepResult` | 记录单步运行状态和安全原因码；不据自然语言输出推断语义正确性 |
| `WorkerAgent.execute` 与嵌套不可变 `ExecutionResult` | 返回显式运行状态；保留现有 `chat` 字符串接口及调用方行为 |
| `SequentialCognitiveExecutor` | 按已验证顺序逐步调用 `WorkerAgent.execute`；运行失败后停止后续步骤，不重试、不重规划 |
| `ReflectionResult` | 一次确定性汇总步骤状态；不作质量评分，不调用模型 |
| `CognitiveTrace`、`CognitiveExecutionResult` | 以不可变结果返回状态转换和最终结果；trace 不含原始提示词、回答或工具参数，不持久化 |
| `CognitiveCoreEngine` | 编排分析、一次计划生成、验证、顺序执行、确定性反思和终态结果 |
| `AgentOrchestrator` | 在既有请求路径增加默认关闭的分支，并传递当前模型、工具 resolver 与 DFA dispatcher |

新类置于 `tech.qiantong.qknow.hermes.cognitive.core` 下的 `model`、`engine` 子包；配置类放在现有 `hermes.config` 包。只使用现有依赖和 `WorkerAgent`，不新增外部依赖。

计划由当前请求注入的 `ChatModel` 单次生成，格式为 JSON 步骤数组；每步必须显式提供非空 `stepId` 和 `objective`，`dependencies` 可省略或为空。解析失败、空计划、必需字段缺失不得被默认 ID/目标掩盖，也不得自动修复后继续执行。

`PlanVerifier` 只承诺结构规则：不承诺语义覆盖率、工具能力映射、预期产物正确性或整体计划合法率。当前代码没有供本阶段复用的权威 capability 注册表或 artifact 契约，故这些检查不纳入本阶段。

### 3.2 执行和失败语义

状态路径固定为：

```text
ANALYZING → PLANNING → VERIFYING → EXECUTING(step 1..n) → REFLECTING → COMPLETED
                                      └─显式运行失败→ FAILED
```

- 计划生成、解析或验证在任何步骤执行前失败：记录对应终态/原因，并允许进入既有 ReAct 基线；此请求不再调用旧 plan-solve 重规划。
- 任一步骤运行失败：立即停止后续步骤并返回受控失败结果；不得回退到 ReAct 重放可能已发生副作用的工具动作。
- 所有步骤状态均由 `StepResult` 记录；后续步骤只在其依赖已完成时执行。
- 步数上限复用 `PlanSolveConfig.maxTasks`；本阶段不增加 token、延迟、重试或并发调参项。
- 反思仅按结构化运行状态生成 `ReflectionResult`。`COMPLETED` 代表步骤调用按运行契约完成，不代表答案语义正确。
- trace 作为 `CognitiveExecutionResult` 的请求级内存返回值，不进入 `HarnessEvidenceRecord`、数据库、日志原文或用户可见 SSE。Stage 23A 如需收集 trace，另行定义只读评测接线。

### 3.3 测试文件

在 `qknow-hermes-core/src/test/java` 增加 `CognitiveCoreContractTest`，验证计划验证、严格执行顺序、依赖结果传递、typed 运行失败判定（包括正文以 `执行失败:` 开头仍不被误判）、失败停止、单次确定性反思及 trace 状态一致性。

在 `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent` 增加 `AgentOrchestratorCognitiveCoreTest`，使用确定性 fake `ChatModel` 验证：默认关闭不改变既有路径；冲突配置在认知规划/步骤执行前拒绝；开启时跳过旧 planner supervision/plan-solve；计划在执行前验证；计划拒绝时可回到 ReAct 基线；步骤失败不会重放工具步骤。

测试必须完全离线，不读取 `.env`，不构造真实 provider，不访问生产 KB/数据库/Redis，不调用真实 DeepSeek 或 Embedding API。

## 四、明确不做

- wave、并行步骤、多 Agent 并发和任意 DAG 调度；
- 自动重规划、局部 retry、反思驱动修复、策略演化、经验读取或写入；
- `AgentOrchestrator` 默认启用或生产配置启用；
- 修改 `HarnessEvidenceRecord` 持久化结构、数据库 schema、生产工具或生产 KB；
- 余弦死锁检测、VC/泛化界、效果 KPI 或任何未经测量的成功率承诺；
- Stage 22A、Stage 23A、Stage 21B/22B/23B；
- 真实模型/API、付费调用、论文效果结论或外部依赖。

## 五、验收门禁

实现分支须从包含本任务书的冻结基线单独创建。只有用户另行授权后才创建 `codex/hermes-stage21a` 并开始源码修改。

```text
[ ] 关闭开关时默认路径行为不变，默认值为 false
[ ] 开启与旧 plan-solve 同时配置时，在认知规划/步骤执行前拒绝
[ ] 计划结构错误在步骤执行前被拒绝
[ ] 步骤严格顺序执行，依赖结果只向后续步骤传递
[ ] 任一步骤失败后停止，不重试、不重规划、不重放副作用
[ ] Reflection 恰好一次、确定性、无模型调用
[ ] CognitiveTrace 与实际状态序列一致，且不含原始输入/输出/工具参数
[ ] fake ChatModel 测试全离线，未接真实 API
[ ] 全量单步 clean test 通过，报告实际测试总数/失败/错误/跳过数，不预设旧总数
[ ] 变更只涉及本任务书列出的代码和测试边界；工作区干净
```

唯一全量验证命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

本任务书不要求、也不授权运行该命令或任何 Stage 21A 测试；命令仅供后续获批实施使用。Stage 21A 完成后须报告实际结果，不沿用 Stage 16/17/其他阶段的测试总数。

## 六、阶段顺序与冻结语义

1. 当前只冻结 Stage 21A 实施契约，不实施源码。
2. Stage 21A 完成并验收后，才进入 Stage 23A 最小过程评测的独立任务书与授权。
3. Stage 22A 必须等待 Stage 23A 产出可用描述性基线，再另行评估；当前不授权。
4. Stage 21A 通过只证明 PVER 的结构和执行契约，不证明真实模型推理质量或算法效果。
