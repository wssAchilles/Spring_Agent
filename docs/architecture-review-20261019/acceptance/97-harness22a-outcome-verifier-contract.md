# Stage 22A：OutcomeVerifier 契约规划

> 状态：只读契约规划；本文件不授权实现、不修改 `src/main/java` 或 `src/test/java`、不调用真实模型。
>
> 基线：`codex/hermes-stage21-self-evolution`，Stage 21 HEAD `504a70a0`；Stage 22A 研究计划提交 `b279429c`。
>
> Stage 21 工程复验：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`，Reactor 43 个模块全部成功，`1943 tests / 0 failures / 0 errors / 8 skipped`。该结果只证明工程契约，不证明真实模型正确性、策略泛化或越用越强。

## A. 当前结果信号审计

### A.1 当前调用路径

当前认知路径由 `AgentOrchestrator` 在配置开启、认知引擎已注入、单 Agent 模式且请求被判定为复杂任务时进入 `CognitiveSelfEvolutionEngine`。引擎依次生成计划、执行 `WorkerAgent` 步骤、合成答案、调用同一 `ChatModel` 做最终反思；反思接受后可把自由文本策略写入 `ExperienceLibrary`。Stage 21 已有有限修复、反馈、连续失败退役和文件版本归档。

当前真实路径可概括为：

```text
AgentOrchestrator
  → ExperienceDomain.cognitiveScope(bot, knowledgeIds)
  → CognitiveSelfEvolutionEngine
  → ExperienceLibrary recall / ExperienceStrategySelector
  → plan + PlanVerifier + WorkerAgent sequential execution
  → ChatModel reflection / bounded repair
  → final reflection accepted?
  → recordFeedback / adoptRuntimeStrategy
  → FileSystemExperienceStore version archive
```

### A.2 现有信号分类

| 信号 | 当前来源 | 语义 | 是否可作为独立 outcome |
|---|---|---|---|
| `PlanVerificationResult.valid` | `PlanVerifier` | 计划结构与边界合法 | 否；只是前置契约 |
| `WorkerAgent.ExecutionResult.succeeded` | `WorkerAgent` | 执行路径未报告异常，非空文本通常即成功 | 否；是过程信号 |
| `ReflectionResult.accepted` | 同一 `ChatModel` | 模型对步骤或答案的自评 | 否 |
| `CognitiveExecutionResult.Status.COMPLETED` | 认知引擎 | 计划执行与模型反思流程结束 | 否；不能等同任务正确 |
| `ExperienceEntry.feedbackSuccesses` | 运行反馈 | 当前引擎将完成/失败写成布尔反馈 | 否；待由独立信号驱动 |
| 外部任务断言/工具后置条件/状态查询 | 当前无统一入口 | 可复算的任务结果 | 是，纳入本契约 |

`ExperienceStrategySelector` 当前只筛选 `ADOPTED`、未退役且置信度不低于阈值的条目，再按均值和 UCB 风格探索项排序。该排序是工程启发式；当前没有 task context、独立 reward 或 contextual bandit 的识别条件。

`ExperienceEntry` 当前是自由文本 `lesson`，有版本、反馈次数、连续失败计数和 `ExperienceDisposition`，但没有 `parentVersionId`、策略模板、变体或独立 verifier 引用。`ExperienceDomain` 以 bot 与规范化知识库集合形成隔离 scope；`UNSCOPED_REPLAY` 不进入正向召回。`FileSystemExperienceStore` 以原子写、域标记和版本目录保留历史，恢复会创建更高版本。

### A.3 当前审计结论

1. 现有 `ADOPTED` 主要表示模型反思接受且执行链完成，不表示外部正确性。
2. `WorkerAgent` 成功不能升级为答案正确；否则任意非空模型输出都可能污染经验。
3. `UNKNOWN` 语义尚未存在；当前布尔 feedback 无法表达“没有 verifier”。
4. 同域隔离是可复用基础，但任务特征与 bot/KB scope 必须分开。
5. 文件归档可作为版本持久化底座，但不能替代 outcome 证据。

## B. 模型自评风险

### B.1 风险机制

`CognitiveSelfEvolutionEngine.reflectAnswer` 明确要求同一模型返回 `accepted` 字段；`accepted=true` 后，若有策略文本即可调用 `adoptRuntimeStrategy`。该路径把生成器、评估器和被采纳策略的来源耦合在同一请求模型内，形成自证循环：模型生成答案，模型判断答案完成，系统把判断转成 ADOPTED。

这不是说模型反思没有价值。反思可用于：

- 解释失败原因；
- 提出受限修复候选；
- 提供变体动机和可读 trace。

但它不能单独产生 `PASS`，也不能覆盖 verifier 的 `FAIL` 或 `UNKNOWN`。

### B.2 必须保持的三态语义

```text
PASS    = 独立 verifier 在其版本化契约内复算通过
FAIL    = 独立 verifier 在其版本化契约内复算不通过
UNKNOWN = verifier 缺失、证据不足、执行中断或信号冲突
```

`UNKNOWN` 不得当作 PASS、不得增加成功统计、不得清除失败 streak、不得触发 ADOPTED 或 RETIRED。模型 `accepted=true` 只能记为 `reflectionAccepted=true` 的过程字段。

## C. 外部研究对比

本节只引用已在 96 号研究计划中实际读取的范围；外部论文结果不外推为本项目效果。

| 来源 | 可借鉴点 | 与本项目的差异/限制 | 契约裁决 |
|---|---|---|---|
| Reflexion，arXiv:2303.11366，NeurIPS 2023 | 将反思与 evaluator feedback 作为不同逻辑职责，形成可检索 episodic memory | 任务、模型和 evaluator 设置不同；自然语言 evaluator 不是形式化真值 | 采用“反思与结果信号分离” |
| ExpeL，arXiv:2308.10144，2023 | 任务相似性、成功/失败轨迹与 insight 分离，支持 transfer | 依赖特定 ReAct 环境和 benchmark；不能据此保证任意跨任务迁移 | 采用 typed task descriptor 的最小匹配 |
| Let’s Verify Step by Step，arXiv:2305.20050，2023 | 区分 outcome supervision 与 process supervision，强调可检查结果 | 论文聚焦数学监督，不提供通用 Agent verifier | 采用可复算 outcome，不训练 PRM |
| LinUCB，arXiv:1003.0146 / WWW 2010 | 上下文、探索与反馈的结构化接口 | 当前日志非随机、反馈稀疏，不满足反事实识别条件 | 只保留可解释候选排序，不宣称 regret 保证 |
| Promptbreeder，arXiv:2309.16797，2023 | 变体需 parent、mutation 与 fitness | 其种群/代数远大于本阶段，任意 mutation/crossover 风险高 | 首版最多两个单字段变体 |
| Kolodner，An Introduction to Case-Based Reasoning，AI Review 1992 | 案例需与适用情境一起理解和复用 | 本轮只读摘要与元数据，全文未完全开放 | 仅作背景，不据此声称已验证 4R 算法 |

## D. 候选架构

| 方案 | 正确性 | 可证伪性 | 数据需求 | 延迟 | 成本 | 复杂度 | 依赖变化 | 回滚风险 | 生产影响 |
|---|---|---|---|---|---|---|---|---|---|
| A. 保持布尔 feedback | 低 | 低 | 低 | 低 | 低 | 最低 | 无 | 中 | 继续自证污染 |
| B. 独立 verifier 只读评测，不接生命周期 | 高 | 高 | verifier + evidence | 低 | 低 | 低 | 无 | 低 | 只能观测，不能自动进化 |
| C. 三态 OutcomeVerifier + adoption gate | 高 | 高 | 结构化任务族、verifier、证据 | 中 | 低到中 | 中 | 无 | 中低 | 默认关闭，可离线启用 |
| D. C + contextual bandit/大规模谱系 | 未标定 | 中 | 随机日志、更多样本、策略种群 | 高 | 中高 | 高 | 无 | 高 | 本轮不准入 |

方案 A 不能回答“经验是否正确”；方案 B 能测但不形成闭环；方案 D 需要尚未存在的统计条件。推荐方案 C 的最小版本。

## E. 推荐架构

推荐 **C-reduced：版本化 OutcomeVerifier + 三态独立信号 + 采纳门禁**，先不实现 StrategyTemplate/UCB/变异。

首版只解决一个缺口：把“模型自评/执行成功”与“任务结果是否通过”分离。

边界：

1. verifier 只读冻结的 `OutcomeTask`、脱敏执行证据和版本化规则。
2. verifier 输出不可变 `OutcomeSignal`，状态仅为 `PASS|FAIL|UNKNOWN`。
3. `StrategyAdoptionGate` 只在模板/计划合法且 outcome `PASS` 时返回可采纳；模型自评不能单独触发。
4. `FAIL` 可供后续 Stage 22A 退役/变异使用；`UNKNOWN` 只记录，不改变生命周期。
5. 同一 bot 与 canonical KB scope 仍是硬隔离；本契约不接生产，不调用外部 API。
6. 第一版只注册确定性 verifier，拒绝自由文本“看起来正确”的判定。

## F. OutcomeTaskFamily

`OutcomeTaskFamily` 是 verifier 注册与任务输入的稳定枚举/标识，不是经验隔离域。至少支持：

```text
STRUCTURED_OUTPUT_ASSERTION
TOOL_STATE_ASSERTION
DETERMINISTIC_LOGIC_ASSERTION
```

每个任务必须提供：

```text
taskId                  稳定任务身份
family                  任务族
scope                   bot + canonical KB scope；缺失则拒绝迁移
inputDigest             任务输入脱敏摘要
expectedContractId      预注册 verifier 契约
evidenceRefs            允许读取的证据引用集合
```

任务族不是“模型能力标签”。它只声明结果如何被独立复算。

## G. OutcomeSignal

建议使用不可变 Java 21 record：

```text
OutcomeSignal(
  runId,
  taskId,
  verifierId,
  verifierVersion,
  status: PASS | FAIL | UNKNOWN,
  evidenceRef,
  evidenceDigest,
  evaluatedAt,
  comparedStrategyVersionId,
  reasonCode
)
```

契约要求：

- `runId/taskId/verifierId/verifierVersion/evidenceRef/evidenceDigest` 非空；
- `evidenceDigest` 为固定格式 SHA-256；
- `PASS` 与 `FAIL` 必须有可读取证据；证据缺失降级 `UNKNOWN`；
- `UNKNOWN` 必须包含可解释原因码，如 `NO_VERIFIER`、`EVIDENCE_MISSING`、`EXECUTION_INTERRUPTED`、`SIGNAL_CONFLICT`；
- 信号必须记录被比较的策略版本，避免把旧版本结果计入新版本；
- 信号不可由被测模型直接构造，必须由注册 verifier 产生。

## H. OutcomeVerifier

最小接口语义：

```text
OutcomeVerifier {
  verifierId()
  verifierVersion()
  supportedFamily()
  verify(OutcomeTask task, ExecutionEvidence evidence): OutcomeSignal
}
```

实现约束：

1. 纯确定性、无网络、无随机数、无模型调用。
2. 只读取 `OutcomeTask` 声明的证据引用，不读取未授权文件、数据库或环境变量。
3. 规则、输入和输出可序列化；相同输入必须得到相同 status、reasonCode 和 evidenceDigest。
4. verifier 抛出异常、版本不匹配或证据越界时返回 `UNKNOWN`，不得把异常当 PASS。
5. verifier 自身必须有契约测试；PASS 只证明其定义域内的断言，不证明一般智能。

## I. OutcomeContract

`OutcomeContract` 是任务族与 verifier 的冻结绑定：

```text
OutcomeContract(
  contractId,
  contractVersion,
  family,
  requiredFields,
  assertionSchema,
  evidenceSchema,
  verifierId,
  verifierVersion,
  unknownPolicy
)
```

契约版本升级必须产生新 `contractVersion`；历史信号保留原版本，不允许静默重算成新版本。`unknownPolicy` 在本阶段固定为 `DO_NOT_ADOPT`。

## J. StrategyAdoptionGate

采纳门禁是生命周期与 outcome 的唯一连接点：

```text
AdoptionDecision decide(
  StrategyCandidate candidate,
  PlanVerificationResult plan,
  OutcomeSignal outcome,
  ReflectionEvidence reflection
)
```

决策规则：

| 条件 | 决策 |
|---|---|
| candidate schema 非法 | `REJECT_INVALID_CANDIDATE` |
| plan 非法 | `REJECT_INVALID_PLAN` |
| outcome `PASS` 且 evidence 完整 | `ADOPT` |
| outcome `FAIL` | `REJECT_OUTCOME_FAIL`，为后续退役/变异记录负反馈 |
| outcome `UNKNOWN` | `DEFER_UNKNOWN`，不采纳、不退役、不改 reward |
| 仅 reflection `accepted=true` | `DEFER_NO_INDEPENDENT_SIGNAL` |

`reflectionAccepted` 只能出现在审计字段，不能覆盖 `OutcomeSignal.status`。首版不比较 candidate 与 baseline 的改善幅度；`ADOPT` 表示通过该 outcome contract，不表示优于 baseline。

## K. 三类离线任务族

### K.1 结构化输出断言

输入是脱敏 JSON 输出与冻结 schema，例如必需字段、字段类型、枚举值、数值范围。verifier 使用 JSON Schema 子集或项目已有结构化解析器；缺字段、类型错或越界为 `FAIL`，无完整输出为 `UNKNOWN`。

### K.2 工具状态/文件摘要/记录字段断言

输入是受控 fake tool 的调用记录、返回状态、文件摘要或记录字段。verifier 只检查预注册 postcondition，例如 `status=COMMITTED`、指定字段等于预期摘要、调用参数与任务契约一致。未执行工具、证据缺失或状态冲突为 `UNKNOWN`；明确违反 postcondition 为 `FAIL`。

### K.3 确定性逻辑或算术结果

输入是任务声明的有限表达式、答案字段或中间结果，verifier 用 Java 标准库和冻结规则复算。相同输入必须得到同一结果；不解析任意代码、不执行脚本。结果相等为 `PASS`，明确不等为 `FAIL`，表达式非法或证据不全为 `UNKNOWN`。

三类任务族均使用 fake model/fake evidence fixture；不调用 DeepSeek、Embedding API、生产数据库、Redis 或知识库。

## L. 测试矩阵

| 测试类别 | 核心断言 |
|---|---|
| OutcomeTask schema | 缺字段、错误 family、scope 缺失、未知 contract 被拒绝 |
| Structured verifier | 合法 JSON PASS；字段缺失/类型错 FAIL；不可解析 UNKNOWN |
| Tool/state verifier | postcondition 满足 PASS；明确违反 FAIL；调用记录缺失 UNKNOWN |
| Logic/arithmetic verifier | 正确结果 PASS；错误结果 FAIL；非法表达式 UNKNOWN |
| Determinism | 相同 task/evidence/version 重复运行信号完全一致 |
| Evidence binding | evidence digest 与证据内容绑定；篡改或越界降级 UNKNOWN |
| Reflection separation | `accepted=true` + UNKNOWN 不采纳；`accepted=true` + FAIL 不采纳；`accepted=false` + PASS 可按 contract 采纳并记录反思差异 |
| Adoption gate | 只有合法 candidate + 合法 plan + PASS 才 ADOPT |
| Unknown policy | UNKNOWN 不增加成功/失败，不触发退役，不污染 UCB/统计 |
| Version binding | verifier/contract/strategy 版本不匹配不计入当前策略 |
| Scope isolation | bot、KB 集合不同不得读取或写入同一 outcome/strategy |
| Offline boundary | fake model/fake verifier 不读取 `.env`，不创建真实 provider |
| Regression | Stage 21 原有 19 项定向测试和全量 Reactor 测试保持通过 |

## M. 评测指标

本阶段只定义离线可计算指标，不运行 live：

- verifier coverage：有注册 contract 的任务比例；
- `PASS/FAIL/UNKNOWN` 分布与 `UNKNOWN` 原因占比；
- outcome evidence completeness 与 digest mismatch 次数；
- adoption gate precision（在冻结 fixture 中不得出现 UNKNOWN/PASS 混淆）；
- model-self-eval disagreement：reflection 与 independent outcome 的分歧数；
- scope rejection 与跨 bot/KB 零泄漏计数；
- verifier latency 与单任务证据读取量；
- 版本绑定错误、过期 verifier、未知 contract 的拒绝计数。

本阶段不计算真实成功率提升、因果收益、UCB regret、统计显著性或“越用越强”。这些需要后续冻结任务集、独立验证和另行授权。

## N. 风险与未验证项

1. **Verifier 可能错误。** PASS 只在版本化 contract 定义域内可信，必须保留规则、证据和测试。
2. **Verifier 覆盖不足。** 自由问答没有确定 oracle 时必须 UNKNOWN，不能为了产生 ADOPTED 强行结构化。
3. **证据伪造。** evidence 必须来自受控执行路径并带 digest；模型自述不是证据。
4. **任务族过宽。** 首版使用有限字段/状态/算术断言，不接受任意代码、SQL 或脚本。
5. **版本漂移。** contract/verifier/strategy 版本必须绑定；升级不能重写历史信号。
6. **误把 PASS 当改善。** 本契约只证明“通过定义的结果断言”，不证明候选胜过 baseline。
7. **反馈稀疏。** 在独立信号数量不足时不启用 UCB 或变体淘汰结论。
8. **现有文件存储并发限制。** JVM 内锁不能自动证明多进程安全；实现阶段必须另定边界。

## O. 准入 / 拒绝决定

**研究/契约规划：准入。** 本文件定义了三态独立结果、任务族、verifier、契约和采纳门禁，并给出离线测试矩阵。

**进入实现：当前有条件拒绝。** 在以下前置项完成并得到单独授权前，不修改源码：

1. 冻结至少三类离线 fixture 与 expected evidence；
2. 为每个 fixture 冻结 `OutcomeContract` 和 verifier 版本；
3. 明确与 Stage 21 `ExperienceEntry`/`ExperienceLibrary` 的兼容迁移，不把 legacy lesson 伪装成模板；
4. 明确 `OutcomeSignal` 与现有 boolean feedback 的双写/切换方案；
5. 增加定向测试任务书和独立 hash。

本契约不授权 StrategyTemplate、UCB、变异/组合、真实 API、模型训练或生产启用。模型自评不能作为外部正确性证明；`UNKNOWN` 永远不能作为 PASS。
