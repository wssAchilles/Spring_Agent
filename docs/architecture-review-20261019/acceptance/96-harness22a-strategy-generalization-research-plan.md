# Stage 22A：Strategy Generalization & Self-Evolution v2 研究与计划

> 状态：只读架构研究与实施计划；本文件不授权实现、不改源码、不调用真实模型。
>
> 审计基线：`codex/hermes-stage21-self-evolution`，HEAD `504a70a00874ee7cad114bd36a5000f30c817e8e`。
>
> Stage 21 工程复验：同 HEAD 的独立 APFS 检出；定向测试 19/19；单步 `clean test` 为 1943 tests、0 failures、0 errors、8 skipped；工作区 clean。该结果是工程契约复验，不证明策略泛化或“越用越强”。

## A. 当前策略能力审计

### A.1 真实执行与经验流

`AgentOrchestrator` 仅在 `qknow.hermes.cognitive-evolution.enabled=true`、认知引擎已注入、单 Agent 模式且问题被识别为复杂任务时进入 Cognitive 路径；该路径由 `CognitiveSelfEvolutionEngine` 生成并校验计划、顺序执行 `WorkerAgent` 步骤、合成答案并调用同一个请求 ChatModel 做最终反思。任务失败后最多按配置次数修复；Stage 21 默认修复上限为 2。经验库的配置默认关闭自进化开关，经验文件根目录默认为 `${user.home}/.qknow/hermes/experience`。

主要路径：

```text
AgentOrchestrator
  → ExperienceDomain(bot + canonical KB scope)
  → CognitiveSelfEvolutionEngine
  → ExperienceLibrary recall / ExperienceStrategySelector
  → plan + PlanVerifier + sequential WorkerAgent execution
  → model reflection / bounded repair
  → ExperienceLibrary feedback / runtime adoption
  → FileSystemExperienceStore version archive
```

### A.2 策略和迁移

当前策略是 `ExperienceEntry.lesson` 中的自由文本经验，不是有触发条件、计划骨架、验证要求和适用边界的结构化模板。引擎固定使用 `cognitive-evolution` cue；经验库按同一域、cue 子串匹配，并以置信度排序，随后 selector 以 UCB 风格分数选择一条策略。它可以在同一 bot/知识库 scope 下被不同任务再次召回，但检索不看任务结构或策略适用条件，因此这只是粗粒度同域复用，不是可判定的跨任务泛化。

当前 `ExperienceDomain` 含 bot、知识库范围与 `taskCategory`。Stage 22A 若将任务族作为新的任务特征，必须把隔离 scope 与任务特征分开：scope 精确锁定 bot 和规范化知识库 ID 集合；task family/features 用于模板适用性匹配，不能成为阻断同域跨任务复用的 scope 字段。

### A.3 采纳、验证、版本和失败处理

- 自动 `ADOPTED` 要求计划步骤执行完成、最终反思接受且产生非空策略；最终接受来自同一个 ChatModel。条目也明确注明这不是外部正确性标签。因此采纳不只是模型自评，但其核心任务完成判据仍依赖模型自评。
- `WorkerAgent.ExecutionResult.succeeded` 是执行/传输层信号；普通非空模型文本会视为执行成功，不能当作独立任务正确性验证。当前没有外部 oracle、结构化断言或独立 task outcome verifier。
- 条目已有整数版本、文件归档、恢复、反馈计数与连续失败 streak；连续 3 次失败会软退役，成功反馈清零。没有 parent version、lineage、variant 或 mutation record。恢复旧版本会生成更高版本并重置退役状态，但恢复版本反馈/streak 来源于归档版本，不等同于显式回滚谱系。
- 失败反思可能产生本次修复策略，但没有父策略派生变体流程；selector 每次只选择一条 lesson，没有策略组合语义。
- `ExperienceDomain.cognitiveScope` 以 bot 和规范化知识库 ID 集合建立域；当前 exact-domain 文件隔离可避免跨 bot/KB 的经验污染。Stage 22A 必须保留该边界，不能为了“泛化”做跨域共享。

### A.4 十个问题的直接回答

| 问题 | 审计结论 |
|---|---|
| 当前策略是 lesson 还是结构化模板？ | 自由文本 lesson。 |
| 策略能否跨任务复用？ | 同域可被其他任务召回，但 cue 固定、无任务相似性和适用条件；泛化能力不可判定。 |
| 自动 ADOPTED 是否只依赖模型自评？ | 不完全是：还要求步骤执行完成；但最终接受由同一 ChatModel 自评决定。 |
| 是否存在独立结果信号？ | 没有 task-level 独立正确性信号；WorkerAgent 成功只说明执行路径未识别到特定错误。 |
| 策略是否有版本、父策略、谱系？ | 有数字版本和归档恢复；无 parent/lineage/variant 关系。 |
| 失败策略如何变异/淘汰？ | 已选择策略可反馈并在连续失败三次后软退役；没有显式策略变异。 |
| 策略是否能组合？ | 不能；每次最多选择一条 lesson。 |
| 跨 bot/KB 是否会污染？ | 当前按 bot 与规范化 KB scope 隔离；Stage 22A 应继续 exact-scope gate。 |
| 连续失败退役能否泛化？ | 规则在 entry 层工作；未来应只消费独立结果信号，`UNKNOWN` 不应当作成功或失败。 |
| 最小泛化闭环应放在哪层？ | 经验/策略领域层定义模板、scope、谱系、检索与生命周期；认知引擎负责应用模板和提交运行信号；不把策略学习塞入 `WorkerAgent`。 |

## B. 当前自进化缺口

唯一待验证假设 H22A：若将自由文本 lesson 替换为带明确适用谓词、可版本化计划骨架与 verifier 标识的 `StrategyTemplate`，并仅按精确 bot/KB scope 检索，且仅用独立 outcome 信号更新生命周期，则系统可以在保持域隔离的条件下把策略用于同域不同任务，并通过有界变体与父版本回滚形成可追溯的策略进化闭环。

可证伪点：模板不能稳定序列化/匹配；不同任务特征导致错误召回；模型自评仍可绕过独立 verifier 影响采纳；变体或恢复无法保留谱系；未知结果被计入奖励；同域 transfer 任务无法提供可复算的独立结果。该假设不包含“成功率一定提升”或真实模型效果主张。

主要缺口：

1. lesson 没有前置条件、策略动作、期望证据和失败边界，无法静态检查适用性。
2. cue 子串与单一 UCB 不能表达 task context；当前 UCB 的均值/探索项不是 Contextual Bandit 理论保证。
3. 模型反思把候选生成与结果判断耦合；缺少独立 outcome verifier。
4. 版本没有 parent/lineage，变异和回滚不可解释为策略家谱。
5. 只有单策略入口，没有候选变体集合、变体预算、冲突规则或有限组合契约。
6. 现有 exact scope 是防污染基础，但任务族不能与隔离 scope 混为同一键。

## C. 外部研究对比与 Research Ledger

以下资料用于方法启发，不把其基准结果外推为本项目效果。网页可读取范围、版本和授权状况按实际原文标注。

### Research Ledger R1

```text
id=R1
sourceType=paper
titleOrRepository=Reflexion: Language Agents with Verbal Reinforcement Learning
authorsOrMaintainer=Noah Shinn; Federico Cassano; Edward Berman; Ashwin Gopinath; Karthik Narasimhan; Shunyu Yao
venueAndYear=NeurIPS 2023
doiOrArxiv=arXiv:2303.11366
url=https://arxiv.org/html/2303.11366
commitOrTag=N/A
license=arXiv 页面标记 CC BY 4.0
filesOrSectionsRead=arXiv v4；§3 Actor/Evaluator/Self-reflection/Memory/Reflexion process；§4.1–4.3 与 ablation；§5 Limitations；Appendix A、B.1
verificationStatus=VERIFIED
relevantFinding=轨迹和 evaluator feedback 经反思转为自然语言 episodic memory，随后影响下一轮 Actor；反思与 evaluator 是不同逻辑职责；文中说明记忆和 trial 有界，并讨论测试质量与局部最优的限制。
projectApplicability=借鉴将生成反思与结果评估分离、保存可检索经验；策略载荷改为结构化模板并绑定独立信号。
limitations=论文任务、模型、数据和 verifier 设置与本项目不同；不能据其论文结果宣称本项目跨任务效果；LLM evaluator/self-evaluation 仍非形式化正确性证明。
```

### Research Ledger R2

```text
id=R2
sourceType=paper
titleOrRepository=ExpeL: LLM Agents Are Experiential Learners
authorsOrMaintainer=Andrew Zhao; Daniel Huang; Quentin Xu; Matthieu Lin; Yong-Jin Liu; Gao Huang
venueAndYear=arXiv preprint，2023；核验版本 v3（2024-12）
doiOrArxiv=arXiv:2308.10144
url=https://arxiv.org/html/2308.10144
commitOrTag=N/A
license=arXiv 页面标记 CC BY 4.0
filesOrSectionsRead=§4.1 Gathering Experiences；§4.2 Learning from Experiences（相似任务、成功/失败比较）；§4.3 Task Inference；§4.4 Transfer Learning；§5.1、§5.4、§5.6
verificationStatus=VERIFIED
relevantFinding=经验池同时保留语言 insights 与成功轨迹；用任务相似性和成功/失败对比抽取 insight；论文中的跨任务 transfer 依赖任务集和共享 Wikipedia 工具/知识重叠，并使用特定评测协议。
projectApplicability=借鉴 insight 与具体轨迹分离、按任务特征召回、同域迁移需由独立结果验证；第一版不复制大规模经验池抽取器。
limitations=论文使用特定 ReAct agent、环境、工具和 benchmark；LLM insight 抽取本身不构成外部验证；迁移范围不能外推为任意任务泛化。
```

### Research Ledger R3

```text
id=R3
sourceType=paper
titleOrRepository=Let's Verify Step by Step
authorsOrMaintainer=Hunter Lightman; Vineet Kosaraju; Yura Burda; Harri Edwards; Bowen Baker; Teddy Lee; Jan Leike; John Schulman; Ilya Sutskever; Karl Cobbe
venueAndYear=arXiv preprint，2023
doiOrArxiv=arXiv:2305.20050
url=https://arxiv.org/html/2305.20050
commitOrTag=N/A
license=arXiv 页面标记 perpetual non-exclusive license
filesOrSectionsRead=§2.4 Data Collection；§2.6 PRMs；§4.1 Process vs Outcome Supervision；§5 OOD Generalization；§6 Discussion
verificationStatus=VERIFIED（限实际列明章节）
relevantFinding=区分可自动检查最终答案的 outcome supervision 与逐步标注的 process supervision；外部/可复算 outcome 可避免将生成器自身判断当真值；监督来源、OOD 和过程误判仍是重要边界。
projectApplicability=采纳 outcome 与 reflection 两条信号通道分离；第一版只接确定性断言、工具后置条件或显式外部确认，不训练 PRM。
limitations=论文聚焦数学 reward model，依赖人工标签/特定模型和数据；不提供通用 Agent verifier，也不支持把任意模型评分当 ground truth。
```

### Research Ledger R4

```text
id=R4
sourceType=paper
titleOrRepository=A Contextual-Bandit Approach to Personalized News Article Recommendation
authorsOrMaintainer=Lihong Li; Wei Chu; John Langford; Robert E. Schapire
venueAndYear=WWW 2010
doiOrArxiv=DOI 10.1145/1772690.1772758；arXiv:1003.0146
url=https://arxiv.org/html/1003.0146v2
commitOrTag=N/A
license=作者版 arXiv 页面标记 perpetual non-exclusive license
filesOrSectionsRead=§2 Formulation & Related Work；§3.1 LinUCB with Disjoint Linear Models；§3.2 LinUCB with Hybrid Linear Models；§4 Evaluation Methodology；§5 实验可读部分
verificationStatus=VERIFIED
relevantFinding=LinUCB 将上下文特征、线性期望回报和置信上界探索结合；其离线评价需要随机化 logging policy/动作概率等识别条件，论文将这些假设写入评测方法。
projectApplicability=可借鉴 exploitation/exploration 评分分项、context 特征和反馈日志；首版沿用有限候选的简单可解释排序，不主张 LinUCB 的 regret 或反事实评价保证。
limitations=原任务是 Yahoo 新闻点击选择，要求特征模型与适当随机日志；本项目当前 feedback 稀疏且非随机，不能直接套用其统计保证。
```

### Research Ledger R5

```text
id=R5
sourceType=paper
titleOrRepository=Promptbreeder: Self-Referential Self-Improvement via Prompt Evolution
authorsOrMaintainer=Chrisantha Fernando; Dylan Banarse; Henryk Michalewski; Simon Osindero; Tim Rocktäschel
venueAndYear=arXiv preprint，2023
doiOrArxiv=arXiv:2309.16797
url=https://arxiv.org/html/2309.16797
commitOrTag=N/A
license=arXiv 页面标记 CC BY 4.0
filesOrSectionsRead=§3 Promptbreeder（初始化、mutation、crossover）；§4 Experiments；§5 Results and Discussion
verificationStatus=VERIFIED
relevantFinding=Promptbreeder 以训练集 fitness 驱动 task prompt population，并演化 mutation prompt；报告使用 50 个个体、通常 20–30 代及多个公开基准。
projectApplicability=借鉴“变体必须有显式 parent、有限 mutation 和结果评分”的思想；Stage 22A 仅采用 schema 内单字段变异、每父版本最多两个候选。
limitations=该方法依赖训练任务集和可计算 fitness，种群/代数远大于本阶段；不适合用来证明在线自然语言策略可泛化；首版拒绝自指 mutation、任意 crossover 和大种群。
```

### Research Ledger R6

```text
id=R6
sourceType=paper
titleOrRepository=An Introduction to Case-Based Reasoning
authorsOrMaintainer=Janet L. Kolodner
venueAndYear=Artificial Intelligence Review 6, 3–34 (1992)
doiOrArxiv=DOI 10.1007/BF00155578
url=https://link.springer.com/article/10.1007/BF00155578
commitOrTag=N/A
license=N/A（publisher 页面未核验）
filesOrSectionsRead=publisher 摘要与引文/版权元数据；全文其余部分为订阅内容，未读取
verificationStatus=PARTIALLY_VERIFIED
relevantFinding=摘要将 CBR 描述为使用旧经验理解新问题，可通过适配旧解、解释新情况、批评新解或依先例推理；本轮不据摘要声称已验证具体 retrieval/reuse/revise/retain 算法细节。
projectApplicability=可作为“案例与适用情境需一起保存”的方向背景；详细 CBR 操作机制不能直接作为本阶段已核验依据。
limitations=全文未访问，具体算法、条件与实验未验证；如后续方案依赖具体 4R 实现，应先取得并阅读全文。
```

### C.1 适用性裁决

- 直接采用：独立结果反馈与模型反思拆分；版本记录和来源可追溯；精确域 scope；有限候选；成功/失败均可记录。
- 需要改造：ExpeL/CBR 的自由文本 insight 改成受限 schema；跨任务迁移基于任务结构谓词，且须有 verifier；UCB 仅在匹配候选内作为启发式排序。
- 拒绝首版：模型自评直接提供 outcome label；LinUCB/Thompson 的统计保证；无界 Promptbreeder 种群、自指变异和任意 crossover；跨 bot/KB 迁移；把 CBR 未读全文内容写成已验证算法。

## D. 候选架构

| 方案 | 正确性 | 可证伪性 | 数据需求 | 延迟 | 成本 | 实现复杂度 | 依赖变化 | 回滚风险 | 生产影响 |
|---|---|---|---|---|---|---|---|---|---|
| A. 保持 lesson + 单策略 UCB | 低：无适用谓词/独立结果 | 低：只能验证选择器运行 | 低 | 低 | 低 | 最低 | 无 | 低 | 保留现状，默认关闭 |
| B. StrategyTemplate + 同域跨任务检索 | 中：模板条件可检查，但反馈可信度不足 | 中：可测匹配与迁移路径 | 任务标签/模板结构 | 低 | 低 | 中 | 无 | 中：错误模板可广泛召回 | 只读候选后可逐步接入 |
| C. Template + 谱系 + 有界变异/受限组合 + 独立验证 | 高：来源、适用、结果可追溯 | 高：模板应用和外部信号可逐项复算 | 独立 verifier 和结构化 task descriptor | 中 | 中 | 中高 | 无 | 中低：immutable parent 与回滚 | 初期仅离线/显式 opt-in |
| D. 确定性规则匹配 + 独立验证，不做变异/bandit | 高可解释，但不能自动探索策略变体 | 高 | verifier、显式任务标签 | 最低 | 最低 | 低 | 无 | 最低 | 适合无可验证探索数据的任务 |

### D.1 维度解释

A 继续把模型自评和策略挑选绑定，最省但无法回答“为什么可迁移”。B 增加结构可判定性，但没有 lineage 和外部 outcome 时仍可能重复采纳错误经验。C 是目标架构，但首版必须缩成一个小而有界的子集。D 是无独立信号任务的正确 fallback：保留已有 Agent 路径，不强行学。上述延迟/成本为结构性判断，不是实测数字。

## E. 推荐架构

推荐 **C-reduced：同域结构化模板 + 不可变版本谱系 + 可复算 IndependentOutcomeSignal + 最多两个单字段变体 + 自动采纳/退役/恢复**。检索先做确定性适用条件过滤，再在匹配模板中采用简单、可解释的结果分数；当前 Stage 21 UCB 可作为 baseline 比较，但必须重新定义只计入独立信号的反馈统计，并明确标注为启发式而非 contextual bandit 保证。

第一版范围：

1. 将隔离 scope 定义为 `(botId, canonicalKnowledgeSetHash)`；task family 和 task features 属于检索上下文，不进入 scope key。
2. 新建受限 `StrategyTemplate`，只表达认知计划/证据检查的 typed directives，不执行任意代码。
3. 同域、适用谓词满足时才可被跨任务召回；不做跨 bot/KB 迁移。
4. 一个父版本每次最多创建 2 个单字段变体；不做自指 mutation、大种群和任意组合。
5. 由 `OutcomeVerifier` 提供 `PASS/FAIL/UNKNOWN`；只有 PASS/FAIL 进入自动评分和生命周期。Reflection 只负责分析/提出变体。
6. 连续 3 个 verifier `FAIL` 软退役；恢复父版本为新的版本记录，保留原版本和 lineage。
7. 每个 scope 最多 32 个 active template lineage；超限时按可解释排序将最弱者软退役，不物理删除历史记录。归档增长和冷存储后续单独规划，不在本轮扩展。

本阶段不引入 embedding 检索；因此不依赖向量模型，也不提出超球面余弦几何结论。后续若引入向量召回，必须单独定义归一化空间和评价契约。

## F. StrategyTemplate / Variant / Lineage 契约

### F.1 StrategyTemplateVersion

不可变版本至少包含：

```text
templateId                 同一策略 lineage 的稳定身份
versionId                  唯一版本身份
lineageId                  谱系身份
parentVersionId            根版本为空，其余必须指向已存在版本
scope                      精确 botId + canonical KB set hash
taskFamily                 任务族标签，不作为隔离域
applicability              结构化前置谓词、必需能力、排除条件
planSkeleton               有序 typed directive / step pattern / dependency refs
expectedEvidence           可观察的任务结果信号及 verifierId
mutationBudget             每父版本每代最大 2 个变体
disposition                PROPOSED | ADOPTED | RETIRED | RESTORED
sourceRunId / evidenceRef  起源运行与独立信号引用
createdAt                  版本创建时间
```

`planSkeleton` 不包含任意 Java、SQL、脚本或运行时字符串拼接；所有 action 类型来自注册的 enum/record schema。策略中的自然语言描述仅作为解释字段，不能替代可检查字段。

### F.2 Variant

```text
variantId
parentVersionId
mutationField                 仅限预注册字段枚举
beforeValue / afterValue      schema 校验的值
mutationReasonRef             关联模型 reflection；不是采纳证据
scope / lineageId             必须与父版本一致
```

同一父版本一次变异批次最多 2 个 candidate variant；拒绝缺父版本、跨 scope 变异、未知字段、无变化变异和超过候选上限的请求。

### F.3 Lineage

版本关系构成不可变有向无环图。每次编辑/变异/恢复产生新版本；旧版本只进入 archive，不覆盖。`parentVersionId` 必须存在且与 `scope`、`lineageId` 一致；恢复旧版本时创建新的 `RESTORED` 后继节点，而不是把旧版本重新改写成 current。

## G. IndependentOutcomeSignal 设计

```text
IndependentOutcomeSignal(
  runId,
  taskId,
  verifierId,
  verifierVersion,
  status: PASS | FAIL | UNKNOWN,
  evidenceRef,
  evaluatedAt,
  comparedTemplateVersionId
)
```

可接受来源：任务外 oracle/固定断言、结构化工具 postcondition、受控数据状态校验，或显式、可追溯的外部用户确认（仅当具体任务契约定义其为结果信号）。生成器 ChatModel 和 reflection ChatModel 的 `accepted` 均不是 IndependentOutcomeSignal。

规则：

- verifier 未注册、证据不足、执行中断或信号冲突 → `UNKNOWN`；UNKNOWN 不增加成功/失败次数、不清 streak、不采纳、不退役。
- WorkerAgent 成功仅作为 execution signal 记录，不得升格为 outcome PASS。
- verifier 只读已定义的结果和证据，不把被测模型自述“成功”当外部真值。
- 自动 `ADOPTED` 需要模板 schema/PlanVerifier 通过且独立信号 PASS。它只表示在 verifier 定义的范围内通过，不自动证明比 baseline 更好。
- 若要求宣称“改善”或优于 baseline，必须另做相同任务/资源约束下的配对评测；本阶段计划不包含该效果结论。

## H. 同域跨任务检索与迁移规则

1. 构造 `EvolutionScope = (botId, sortedDistinct(canonicalKnowledgeIds))`；bot 不同或规范化 KB 集合不同，候选直接拒绝。缺 bot/无法确认 scope 时不跨任务读取或写入。
2. 同一 scope 下使用 typed `TaskDescriptor`：taskFamily、能力集合、任务阶段/工具类别等。只做结构化谓词匹配，不做未验证 embedding/自由文本相似度。
3. 召回顺序：scope exact filter → status eligible → applicability match → required capability match → 排序。每次 trace 保存候选、排除原因、选中版本与 verifier。
4. 适用 taskFamily 不同但谓词匹配的模板可进入 transfer trial；只有目标任务的独立 PASS 才增加 transfer evidence。无 verifier 时记录 UNKNOWN，不计作策略成功。
5. Stage 21 自由文本 lesson 只读保留，不能自动迁移成已验证 StrategyTemplate；可由后续显式迁移工具生成 `PROPOSED` 草稿，但需 schema、scope 和独立 outcome gate。

## I. 变异/组合规则

- Reflection 可提出变异解释和候选值，但只有已验证的 schema field 能变；变异候选必须保留父版本、lineage 和 scope。
- 每次至多 2 个 variant；每个 variant 只改变一个字段，便于归因。候选上限不是可扩容配置项。
- 只在独立 verifier `FAIL` 或明确的外部过程失败后生成变体；模型自评失败可触发诊断，不可独自提供负 outcome。
- 首版禁止任意 crossover。策略组合仅允许未来以显式注册的组合模板启用：组件数最多 2、作用目标互斥/有声明顺序、组合有独立版本身份及自己的 verifier；在当前实施准入中组合保持关闭。
- 每个 scope 最多 32 个 active lineage；超限只软退役，不删除已归档版本。

## J. 自动采纳 / 退役 / 回滚规则

状态与反馈流程：

```text
ReflectionProposal
  → schema / PlanVerifier 合法
  → PROPOSED variant
  → 实际执行
  → IndependentOutcomeSignal
      PASS: 自动 ADOPTED；更新独立成功统计
      FAIL: 更新独立失败统计；连续 3 次软退役
      UNKNOWN: 只记录，不更新生命周期/奖励
```

- 同一版本连续 3 次独立 FAIL → `RETIRED`，排除正向召回；任何独立 PASS 清零失败 streak。UNKNOWN 不增加、不重置 streak。
- 自动回滚：若 retired version 有明确的 last-known-good parent，系统新增 `RESTORED` 后继版本，内容从父版本复制，保存 rollback evidence 和 lineage；若无已知良好 parent，只退役并停止正向召回。
- 被恢复版本重新参加召回，但统计限定在所选恢复版本及其明示继承策略上；不得覆盖失败版本的历史或把 archive 中的旧 success 伪装成新版本验证。
- 可使用经验均值 + 探索项作为局部排序启发式，但只用已知独立 outcome，unknown 排除；该分数是策略排序，不代表概率校准或因果增益。
- ADOPTED 是自动生命周期状态，不是“模型已学会”或“策略已证明更优”。跨任务有效性必须由 transfer outcome 另行验证。

## K. 与 Stage 21 的接口

| Stage 21 接口/对象 | Stage 22A 计划演进 |
|---|---|
| `CognitiveSelfEvolutionEngine` | 保留计划/执行/修复编排；从 ExperienceLibrary 读取结构化模板；将独立 outcome 单独提交，不再把 final reflection 当 outcome。 |
| `ExperienceStrategySelector` | 先由 task applicability 筛选，再对可用版本做解释型排序；统计只使用 independent signals。 |
| `ExperienceLibrary` / `DefaultExperienceLibrary` | 增加 template/version/lineage/variant/outcome 操作；exact scope 成为第一道硬筛选。 |
| `ExperienceEntry.lesson` | 作为历史 legacy 记录；新增模板结构，不在迁移时伪装旧 lesson 已有适用证明。 |
| `FileSystemExperienceStore` | 以不可变版本 JSON/证据引用存 lineage，新增读取/回滚契约；保留原子写和 domain marker。 |
| `AgentOrchestrator` | 仅传入 task descriptor、scope 和 outcome verifier 依赖；默认开关继续 false。 |
| `WorkerAgent` | 保持执行器角色；执行成功/失败返回仍是过程信号，不负责策略采纳或答案真值判定。 |

实现位置最小化在 `agent.harness.experience` 与 `cognitive.core.engine`；除非具体 verifier 依赖必须由 orchestration 构造，不改 `WorkerAgent` 责任边界。

## L. 离线确定性测试矩阵

| 测试 | 期望契约 |
|---|---|
| 模板 schema / 版本 | 相同内容 identity 稳定；version 递增；根版本无 parent，非根 parent 存在。 |
| 父版本谱系 | 变异与恢复保留 lineage；循环、跨 scope parent 被拒绝。 |
| 同域跨任务检索 | 相同 bot+KB scope、符合 applicability 的不同 taskFamily 可匹配；不符合条件不召回。 |
| 独立 PASS 采纳 | fake verifier PASS 自动 ADOPTED 并持久化；模型 reflection `accepted=true` 但 verifier FAIL/UNKNOWN 时不采纳。 |
| 选择器反馈 | 仅 PASS/FAIL 参与统计；UNKNOWN 不变 reward、不清失败 streak。 |
| 变体边界 | 每父最多 2 个；只可改一个注册字段；越限/任意代码/无父版本拒绝。 |
| 退役 | 同模板版本连续 3 次独立 FAIL 自动软退役，不再正向召回；PASS 清 streak。 |
| 恢复 | 从 archived parent 创建新版本；保留失败版本；新恢复版本可被适用查询重新召回。 |
| 域隔离 | bot 不同、KB 集合不同、KB 规范化集合不同的来源/候选不跨域。 |
| legacy lesson | 不自动升级到 StrategyTemplate/TRANSFER_VALIDATED。 |
| 默认 OFF | 配置 false 时 Stage 21 原路径不变，不创建模板/变体。 |
| bounded population | 单 scope active lineage 不超过 32，每父 variant 不超过 2。 |

所有测试用 fake model/fake verifier 与临时文件目录；不构造真实 provider，不调用 DeepSeek/Embedding API，不访问生产 DB/Redis/KB。测试验证契约，不作真实模型质量判断。

## M. 评测指标

研究阶段只定义指标，不运行 live：

- verifier coverage 与 `UNKNOWN` 比例；无 verifier 的任务数/占比。
- template schema validity、exact-scope rejection rate、applicability match / miss 计数。
- 独立 outcome 下的 template adoption count、transfer trial count、transfer PASS/FAIL/UNKNOWN 数量。
- 退役触发率、连续失败长度、rollback recovery 轨迹完整度。
- legacy lesson 被阻断升级次数；跨 bot/KB contamination 测试的零泄漏契约。
- 配对 candidate-vs-baseline 的成功差仅在未来有冻结任务集和 verifier 时计算；无此设计不宣称“改善”或泛化。

所有数值阈值（active 32、每父 variant 2、三次失败退役）是工程上界/初始协议，不是经数据标定的最优值。Stage 22A 不运行真实评测、不提供统计显著性结论。

## N. 风险与未验证项

1. **独立 verifier 可用性是首要阻塞。** 自由问答未必有 oracle；无 verifier 的任务应 UNKNOWN 并保留原 Agent 路径，不能把模型自评换个字段名后冒充外部信号。
2. **Verifier 自身可能错。** 每个 verifier 必须有版本、输入、规则、evidence reference 和可复算测试；PASS 只在其定义域内可信。
3. **效果未验证。** 确定性 fake verifier 测不了真实模型对策略的遵循度，也不能证明策略越用越强。
4. **跨任务特征过粗会误迁移。** 第一版宁可缩窄 applicability，记录未命中/UNKNOWN，不引入黑箱相似度。
5. **反馈数据稀疏且非随机。** 现有 UCB 不具备 contextual bandit 的数据模型和离线因果评价条件；探索项不可解释为可信收益置信区间。
6. **历史版本累积。** active lineage 有界，历史版本不物理删除；长期 archive/冷备容量与检索索引治理需另行设计，不在本计划顺手扩展。
7. **文件存储并发边界。** 当前文件锁为 JVM 内锁；多进程共享 root 的 lineage 并发写入仍需在实现计划中明确是否支持。
8. **当前执行接入面有限。** Cognitive 路径只覆盖单 Agent 复杂请求；本计划不扩展到多 Agent 或生产默认启用。

## O. 准入 / 拒绝决定

**Stage 22A 研究与计划：准入；本文件已形成计划文档。**

**进入实现：当前拒绝/暂缓。** 需先由后续独立任务书确定至少一个可复算 `OutcomeVerifier` 的任务族、正式 verifier schema/版本契约、scope migration 兼容方案和离线 fixture；之后另行授权实现。没有 verifier 的自然语言任务不得自动写为有独立证据的 ADOPTED，也不能用 Stage 21 模型自评弥补。

本结论只批准计划审阅，不批准代码变更、真实 API、权重训练、生产启用或效果结论。Stage 21 仍只能表述为“代码/工程契约在指定 APFS 检出通过，策略效果和跨任务泛化未验证”。
