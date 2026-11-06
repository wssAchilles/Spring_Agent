# Stage 21 Self-Evolving Cognitive Core 实施任务书

> **状态：** Stage 21 自进化实现收口契约；本文件取代对 Stage 21 范围的临时描述，不修改 Stage 21A 原冻结文件 94。
>
> **实现分支：** `codex/hermes-stage21-self-evolution`
>
> **范围：** 21A PVER 底座、21B 模型反思与有界修复、21C 自动经验沉淀、21D 在线策略选择与生命周期。
>
> **正文 SHA-256：** `43f046ff9fa79273dd94c20a7192bcead75159472a9f6fb9e0f629c9c0c57058`（从 `## 一、` 起至文件末尾）。

## 一、目标与可证伪假设

Stage 21 的目标是让 Agent 在请求执行中形成策略级自进化闭环：分析并规划任务，执行失败后由当前 ChatModel 反思并提出修复，验证后有界重试；成功运行后可自动沉淀可复用经验；后续同域任务在线选择策略并用新结果更新、退役或恢复策略。

唯一待验证假设 H1：对同一 bot 和知识库集合中的重复复杂任务，模型反思产生的结构化策略能被持久化并在后续同域请求中被选择和实际使用；运行反馈能更新策略表现，并在连续失败时自动退役。离线 fake-model 测试验证流程契约，不证明真实模型能力或效果提升。

## 二、范围与运行边界

### 2.1 21A：PVER 底座

- 将计划、步骤及验证结果表示为不可变数据对象。
- 执行前检查计划非空、步骤上限、步骤 ID 唯一、目标非空及依赖仅指向前序步骤。
- 按顺序执行已验证计划；步骤失败后不得重放已成功步骤。
- 记录不含原始提示词、模型回答或工具参数的请求级 trace。

### 2.2 21B：模型反思与有界修复

- 使用当前请求注入的 ChatModel 生成计划、失败反思、结构化 RepairProposal、最终答案及最终反思。
- RepairProposal 必须先通过 PlanVerifier；非法或不匹配的修复不得执行。
- 修复次数由 `qknow.hermes.cognitive-evolution.max-repair-attempts` 有界控制，默认最多 2 次；不得无界循环。
- 计划生成或计划验证在任何步骤执行前失败时可回到既有 ReAct 路径；发生步骤执行后不得回退并重放可能有副作用的步骤。

### 2.3 21C：自动经验沉淀与采纳

- 仅在计划执行完成且最终模型反思接受时，允许将非空、通过泄漏检查的可复用策略自动写为 `ADOPTED`。
- 优先沉淀本次有效修复策略；没有修复策略时，允许沉淀最终反思提出的新策略。
- 策略 ID 稳定；同一策略再次成功时更新反馈，不重复创建条目。
- `ADOPTED` 表示运行时机制基于执行完成和模型自评自动采纳，不表示外部金标或客观正确性证明。

### 2.4 21D：在线选择、反馈与生命周期

- 仅从同域、非退役、可正向召回的 `ADOPTED` 经验中选择策略；使用可解释的 UCB 分数并在 trace 中记录利用项、探索项和总分。
- 策略实际参与请求后，记录成功或失败反馈、反馈证据引用和新版本。
- **连续 3 次失败自动退役：** 单条策略的连续运行失败计数达到 3 时软退役；任一成功反馈将连续失败计数清零。累计成功率不得替代连续失败判定。
- 退役保留历史版本，不做物理删除；可从指定归档版本恢复为更高的新版本。恢复后的非退役 `ADOPTED` 策略重新具备正向召回资格。

## 三、配置、域隔离与持久化

- 开关 `qknow.hermes.cognitive-evolution.enabled` 默认 `false`；关闭时既有 ReAct 路径行为不变。
- `qknow.hermes.cognitive-evolution.max-repair-attempts` 默认 `2`。
- 持久化根目录默认为 `${user.home}/.qknow/hermes/experience`，可由 `qknow.hermes.cognitive-evolution.experience-root` 覆盖。
- 经验域由 bot ID 和规范化知识库 ID 集合共同确定；同一集合不受输入顺序或重复 ID 影响。不同 bot 或知识库集合不得共享经验。无 bot ID 时不读写经验；不得将 `UNSCOPED_REPLAY` 用于正向召回。
- 经验更新必须保留版本历史；失败、退役、恢复均可从证据引用和版本记录追溯。

## 四、实施与测试边界

- 仅实施 Stage 21 自进化闭环，不实现 Stage 22A、Stage 23A，不修改 88/89/90 安全计划，不合并 `main`。
- 所有自动化测试使用 fake ChatModel 和本地临时目录；不读取 `.env`，不构造真实 provider，不调用 DeepSeek、Embedding API、生产数据库、Redis 或生产知识库。
- 核心测试文件：
  - `CognitiveSelfEvolutionEngineTest`
  - `AgentOrchestratorSelfEvolutionTest`
  - `ExperienceLibraryContractTest`
  - `AgentOrchestratorPlanResolverTest`
- 必须覆盖：默认关闭、计划验证、失败修复与修复上限、非法修复拒绝、执行成功后的自动 ADOPTED、后续同域经验实际注入、UCB 选择与可解释分数、运行反馈版本化、连续失败 3 次退役、退役策略不被召回、归档版本恢复后重新召回、知识库集合规范化与 bot/KB 隔离。

## 五、验收门禁

```text
[ ] 文件 94 及其冻结 checksum 与 HEAD 完全一致，shasum -c 为 OK
[ ] 本任务书正文和全文件双 hash 校验通过
[ ] 默认关闭时旧路径不变
[ ] 计划和修复计划均先验证，执行有界且不重放已完成步骤
[ ] 执行成功且模型反思接受后可自动采纳非空策略
[ ] 同域后续请求读取并实际使用已采纳策略
[ ] 反馈更新策略版本和 UCB 统计，连续失败 3 次后自动退役
[ ] 退役策略退出正向召回，历史版本可恢复并重新召回
[ ] bot 与知识库集合之间隔离
[ ] fake-model 定向测试通过且不调用真实 API
[ ] 全量单步 clean test 通过，报告实际 tests/failures/errors/skipped
[ ] 实现提交后在同 HEAD 全新 APFS 检出再次通过单步 clean test
[ ] 最终工作区 clean；不合并 main；不标记 Stage 21 CLOSED
```

定向测试：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=CognitiveSelfEvolutionEngineTest,ExperienceLibraryContractTest,AgentOrchestratorSelfEvolutionTest,AgentOrchestratorPlanResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

全量测试：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

## 六、解释边界与剩余风险

- `COMPLETED` 和 `ADOPTED` 仅表示当前执行契约成功且模型自评接受；不是独立正确性证明。
- Stage 21 的本地确定性测试证明状态机、反馈、持久化和隔离契约；不证明真实模型的反思质量、策略泛化能力或长期性能提升。
- 本任务不授权真实模型实验、生产接入、论文效果结论或 Stage 22A/23A 实施。
