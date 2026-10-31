> **智能体存证元数据**
> - ConversationId：`f9aa7659-d27a-42b8-9f1c-ec39709df551`
> - 角色：经验感知 Proposer 理论审计员
> - Transcript：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/f9aa7659-d27a-42b8-9f1c-ec39709df551/.system_generated/logs/transcript.jsonl)

# Stage 11 理论报告勘误与撤回

> **状态：原理论报告整体撤回，不得引用为定理、论文结论或 Stage 11 实施依据。** 以下是对原报告关键主张的更正。Stage 11 当前 `BLOCKED`；11A 仅做契约收口，11B 递归 PPC 延期。权威实施边界以 [62-harness11-taskbook.md](62-harness11-taskbook.md) Revision 2 为准。

## 一、撤回的主张

原稿中的下列主张缺少必要前提或与当前代码契约不符，全部撤回：

1. 有限算子池意味着 `VC-dim <= log2(|H|)`，并由此得到开放域跨任务泛化界；
2. 自由自然语言 Prompt 假说空间具有无穷 VC 维，而受限 Prompt/算子空间必然有界 VC 维；
3. `ExperienceLeakageGuard` 等价于数学上的正交因果投影或彻底消除快捷学习；
4. 1024 维余弦相似度 `0.65` 是有理论依据的语义准入阈值；
5. `failureObserved` 可进入负掩码、全局静态经验规则，或 Stage 9 单次结果能形成因果不变式；
6. 两轮 PPC 具有开放域风险截断、单调非衰减或统计泛化保证。

这些说法不能通过换名为“命题”“机制保证”或“工程公理”继续保留为已证结论。

## 二、统计学习理论适用边界

有限的候选操作数量只说明某次候选生成过程可枚举的操作数受限。它本身不定义由这些操作诱导的分类/决策假说类，也不确定其 VC 维；候选凭单和值域、输入域、输出/损失映射均会影响假说类。

有限假说类上的 Hoeffding/union-bound 结论，至少要求预先固定假说类与损失函数、损失有界、样本来自适用的独立同分布分布，并处理数据驱动选择、重复查看 holdout 与多轮自适应选择带来的依赖。Stage 9 的一次小规模回放不能证明这些前提，也不能据此推出跨任务泛化界。

在上述定义、抽样设计与证明缺失时，本阶段不报告 VC 维、泛化误差界或收敛阶。若未来确有必要研究，应先独立形成理论契约和预注册实验；在那之前只能把候选机制称为待检验的工程启发式。

## 三、余弦与负经验接口边界

当前 `ExperienceLibrary.recallForProposer` 是文本 cue 匹配，不是 embedding 或 1024 维余弦召回。没有经过授权并实现的向量检索契约前，不定义 `cos(theta)` 阈值，也不把 1024 维几何用作当前经验筛选机制。

当前接口不提供负经验召回或负掩码。Stage 9 的 HISTORY/COMPOSITE 失败记录保持 `OBSERVED`、`failureObserved=true`、`attributionStatus=UNRESOLVED`，并处于 `UNSCOPED_REPLAY` 审计隔离域；它们既不进入正向 Proposer，也不转写成跨域负规则。`INVALID_FIXTURE` 需要独立反事实证据确认归因，Stage 9 当前计数为 0。

## 四、允许保留的有限表述

- 有界轮数、固定候选操作和确定性停止策略可以作为未来实验待验证的工程设计选项，不等于统计定理。
- 对 Stage 9 派生回放的通过测试，只能支持派生夹具内部一致性及约定字段投影，不支持原始证据真实性或跨任务泛化结论。
- 生产路径是否与经验库隔离，须以具体调用图和契约测试为证据；本文不作未验证的“零污染”强保证。

## 五、阶段状态

Stage 11A 只处理 Stage 10 稳定化契约；不实现经验感知 Proposer、余弦召回、负掩码或递归 PPC。Stage 11B 必须在 11A 验收后另行立项并获明确授权。本文为理论更正与撤回记录，不授权代码、配置、数据或付费 API 变更。
