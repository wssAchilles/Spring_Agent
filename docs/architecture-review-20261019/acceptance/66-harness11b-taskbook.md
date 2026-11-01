# Hermes Harness Stage 11B-1 离线机制任务书

- 状态：`REVISION 1 - 11B-1 AUTHORIZED`；11B-2 真实模型实验仍 `BLOCKED`。
- 基线：`feat/hermes-harness-stage10` / `fdfe6d48`；不修改 `main`。
- 构建环境：[65-harness11b-canonical-build-env.md](65-harness11b-canonical-build-env.md)。历史 clean 故障根因仍为 `UNPROVEN`。
- 正文 SHA-256：`7050225d390ca2f0236e108af9e5004e6b56f31a9643fe2bfd6a799192a0f298`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、A. 现有短板与唯一假设

现有 Stage 9 `PpcOrchestrator` 和 `PpcComposer` 绑定五题、固定候选与 live 输出；Stage 10 `recallForProposer` 是同域 cue 文本包含匹配，不是向量/余弦召回。它排除拒绝、失败、软淘汰和 `UNSCOPED_REPLAY`，但仍可能返回无失败的 `OBSERVED`。`ExperienceEntry` 没有强类型变异算子，不能解析 `lesson` 来猜算子。

唯一待验证假设 H：在一个合成业务 Domain 和固定测试集上，**仅**使用已采纳、无观察失败、未退役且能与已核验候选来源对应的经验，可以确定性地提出最多两个单算子变异；若不改善则立即停止，且任何时刻都不以未验证候选替换当前候选。反例是：无可信来源、算子不可用、探测失败或候选不严格改善时仍发生候选晋级。

H 只涉及离线状态机安全与可证伪性，不承诺真实模型质量提升、跨域泛化、因果归因或最优性。Stage 9 HISTORY/COMPOSITE 仍为 `OBSERVED + UNRESOLVED`，不得变成全局规则。

## 二、B. Research Ledger

### R1 Reflexion

- `id`: R1；`sourceType`: paper；`titleOrRepository`: Reflexion: Language Agents with Verbal Reinforcement Learning。
- `authorsOrMaintainer`: Noah Shinn, Federico Cassano, Edward Berman, Ashwin Gopinath, Karthik Narasimhan, Shunyu Yao；`venueAndYear`: arXiv preprint, 2023；`doiOrArxiv`: arXiv:2303.11366v4。
- `url`: https://arxiv.org/html/2303.11366；`commitOrTag`: N/A；`license`: CC BY 4.0（arXiv 页面标注）。
- `filesOrSectionsRead`: Sections 1, 3, 5; Algorithm 1；`verificationStatus`: VERIFIED。
- `relevantFinding`: Actor/Evaluator/反思记忆形成有界试错结构，原文承认局部最优风险。
- `projectApplicability`: 支持反馈驱动下一候选和显式轮次上限；`limitations`: 其反馈与本项目正向 ADOPTED 经验不同，不能移植效果或停止规则。

### R2 ExpeL

- `id`: R2；`sourceType`: paper；`titleOrRepository`: ExpeL: LLM Agents Are Experiential Learners。
- `authorsOrMaintainer`: Andrew Zhao, Daniel Huang, Quentin Xu, Matthieu Lin, Yong-Jin Liu, Gao Huang；`venueAndYear`: arXiv preprint, 2024 version；`doiOrArxiv`: arXiv:2308.10144v3。
- `url`: https://arxiv.org/html/2308.10144；`commitOrTag`: N/A；`license`: CC BY 4.0（arXiv 页面标注）。
- `filesOrSectionsRead`: Sections 4.1-4.3, 5.1-5.2, 5.5-5.6；`verificationStatus`: VERIFIED。
- `relevantFinding`: 推断时可检索成功轨迹，失败/成功配对规则另行抽取且可降权；训练任务有重试上限。
- `projectApplicability`: 支持正向建议与负向诊断分流；`limitations`: 原系统的 Faiss/embedding、任务和模型均不同，不移植成功率或相似度机制。

### R3 DSPy

- `id`: R3；`sourceType`: paper；`titleOrRepository`: DSPy: Compiling Declarative Language Model Calls into Self-Improving Pipelines。
- `authorsOrMaintainer`: Omar Khattab, Arnav Singhvi, Paridhi Maheshwari, Zhiyuan Zhang, Keshav Santhanam, Sri Vardhamanan, Saiful Haq, Ashutosh Sharma, Thomas T. Joshi, Hanna Moazam, Heather Miller, Matei Zaharia, Christopher Potts；`venueAndYear`: arXiv preprint, 2023；`doiOrArxiv`: arXiv:2310.03714v1。
- `url`: https://arxiv.org/html/2310.03714；`commitOrTag`: N/A；`license`: arXiv.org perpetual non-exclusive license（页面标注）。
- `filesOrSectionsRead`: Sections 3.1-3.3, 4；`verificationStatus`: VERIFIED。
- `relevantFinding`: 候选生成与显式指标筛选分层，指标不是正确性证明。
- `projectApplicability`: 支持离线 propose/probe/select 边界；`limitations`: 未完整读取后续实验与附录，不引入其训练集、优化器或性能结论。

### R4 harness2 固定代码

- `id`: R4；`sourceType`: official-code；`titleOrRepository`: google-research/harness2。
- `authorsOrMaintainer`: Google LLC；`venueAndYear`: N/A；`doiOrArxiv`: N/A。
- `url`: https://github.com/google-research/harness2；`commitOrTag`: `1cfe254e8d25b15f37048c3730134686abe60335`；`license`: Apache-2.0。
- `filesOrSectionsRead`: `LICENSE`, `pyproject.toml`, `harness2/core/library.py:15-222`, `context.py:105-170`, `recursion.py:15-255,450-578`, `prompts.py:560-595,718-745,824-890,964-1030,1105-1163`, `schedule.py:175-245,485-570`, `selection.py:93-115,420-505`；`verificationStatus`: VERIFIED（仅所列范围）。
- `relevantFinding`: 同域目录隔离经验与顺序 PPC；经验通过文本上下文进入 proposer，不存在强类型经验到算子映射；顺序运行固定 k 轮，`STAGE_ATTEMPTS=2` 是提案校验重试而非两轮 PPC，未见无改进提前停止。
- `projectApplicability`: 借鉴同域隔离和候选回退，但本项目新增显式来源绑定与停机条件；`limitations`: 未运行上游 benchmark/模型，未读全部适配器，不宣称实测收益。

## 三、C. 形式化机制与边界

令合成业务域为 d，当前凭单为 v，固定离线用例集合为 C。正向经验集合是 `E+(d) = {e | e.domain=d, e.disposition=ADOPTED, !e.failureObserved, !e.retired, !e.domain.isUnscopedReplay()}`；还必须通过既有库的低置信过滤。`recallForProposer` 只负责现有 cue 文本匹配，11B-1 在返回后再次限定 `ADOPTED`，不得改写 Stage 10 的公共召回语义。

每条 e 的算子只能来自显式 `OperatorProvenance(sourceRunId, PpcCandidate)`：`e.sourceRunId=sourceRunId`，`e.harnessFingerprint` 与候选 voucher 的指纹常量时间匹配，且 `candidate.operators` 恰有一个元素。缺失、错配、多算子或自然语言 lesson 均不产生算子。按库已定义的置信度降序/entryId 升序决定建议顺序，同一算子最多尝试一次；`UNSCOPED_REPLAY` 在召回前短路。

离线 Probe 对每个 v 返回同一固定 C 上的逐题质量 `q_c(v) in [0,1]` 与安全通过标志。仅当用例 ID 集完全相同、全部安全、每题 `q_c(v') >= q_c(v)` 且至少一题严格提高时接受 v'；否则保留 v 并停止。基线必须先得到有效 Probe；失败时不产生结果。候选 Probe 失败、缺题或非有限值时保留上次已验证 v 并停止。第 1 轮接受后可尝试第二个不同算子；最多 2 次候选 Probe、包含基线最多 3 次。有限轮数保证工程状态机终止；**只**能说已接受候选的本次测得质量单调，不能推出真实任务质量、VC 维、泛化界或全局收敛。

候选比较：baseline 是既有 Stage 9 固定单轮路径，可靠但不能检验 H；最小诊断方案只核对召回和来源、不变异，风险低但不能检验改进；11B-1 选独立离线两轮状态机，无新依赖/外网且可证伪；保持现状为零变更但 H 未测试。拒绝重写 Stage 9 Orchestrator/Composer、从 lesson 解析算子、负经验掩码、1024 维余弦召回、训练流程、生产接入与付费实验。11B-1 不改变向量模型或 DeepSeek 配置。

## 四、D. 代码蓝图

仅在 `qknow-hermes-core/src/main/java/.../harness/ppc/recursive/` 新增独立的经验建议器与离线状态机；复用 `ExperienceLibrary`、`ExperienceDomain`、`ExperienceEntry`、`PpcCandidate` 和 `AlgebraicHarnessMutator`。输入为合成 Domain、cue、基线凭单、按 entryId 显式绑定的可信 provenance、纯函数式离线 Probe。输出为不可变 Java record：最终离线凭单、基线/最终 Probe、最多两轮决策轨迹和停止原因，标识为 `OFFLINE_CANDIDATE`，**绝不**写 `ExperienceEntry.ADOPTED`。

不改 `AgentOrchestrator`、Stage 9 的 `PpcOrchestrator`/`PpcComposer`、Stage 10 Importer/Store、MCP/HITL/SWARM、现有生产开关和默认在线模型路径。11B-1 不使用 `experiment/outputs/harness9-live`，不建立网络模型工厂。调用面仅限新离线测试；测试数据用新建合成 domain，不将 Stage 9 的 7 条隔离记录冒充业务经验。

## 五、E. 验证与停止门禁

1. 定向测试覆盖：`UNSCOPED_REPLAY`、跨域、`OBSERVED`、`REJECTED`、未决失败、低置信、retired 均无建议；可信单算子 provenance 能选择算子；错误 run/指纹/多算子均不变异。
2. 状态机覆盖：首轮无严格改进即停；首轮改善后第二轮退化即停；两轮改善达到硬上限；不可变算子跳过；Probe 失败/缺题安全回退；同一固定用例集与零写库。
3. 默认测试无外网、无付费 API；Stage 9 导入仍为 7 条 `UNSCOPED_REPLAY`、`ADOPTED=0`、`INVALID_FIXTURE=0`、Proposer 正向召回为空。
4. 先在 `feat/hermes-harness-stage10` 单独提交 65/66 文档并校验双 SHA-256，再创建 `feat/hermes-harness-stage11b`。只在新分支实施；不修改 main。
5. 在 [65](65-harness11b-canonical-build-env.md) 约定的全新 APFS 检出中，固定 Java 21.0.5/Maven 3.9.16、单 Maven 进程、无 proto 预生成，直接运行 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`。记录完整日志、Reactor、proto 生成数、测试精确计数、退出码和 HEAD；不得以两步命令替代。
6. 若 proto 生成、target 删除或单步 clean 失败，立即停止 11B 候选实验并按 65 保全现场；根因继续 `UNPROVEN`，不自动归因于 11B 代码，不继续运行候选。11B-2 真实 DeepSeek、token/cost 与付费 PPC 仍需另立任务书、预算和授权。

---
本任务书的双哈希只证明冻结字节一致，不证明理论收益。用户授权范围仅为 11B-1 离线机制。
