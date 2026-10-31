# Stage 11 研究稿勘误与撤回

> **状态：原研究稿已撤回，不得作为实施、算法或论文理论依据。** 本文件替代早期 Stage 11 研究正文中不准确的契约与推论。当前以 [62-harness11-taskbook.md](62-harness11-taskbook.md) Revision 2 为实施边界，Stage 11 继续 `BLOCKED`。

- **原研究账本**：[61-harness11-research.ledger.json](61-harness11-research.ledger.json)，只保留来源查阅记录，不代表其在原稿中的适用性结论仍然成立。
- **阶段拆分**：11A 为 Stage 10 契约收口与稳定化；11B 经验感知 Proposer 和递归 PPC 延期，须另行立项授权。

## 一、撤回项

原稿将下列内容写成已证机制或理论结论，现全部撤回：

1. 有限算子集直接推出有限 VC 维、`O(N^-1/2)` 跨任务泛化界或经验风险截断定理；
2. Stage 9 的候选处置或 HISTORY/COMPOSITE 单次失败构成全局负经验、系统级因果规则；
3. 经验召回支持负掩码、余弦相似度或 `cos(theta) >= 0.65` 阈值；
4. 两轮 PPC 已具备统计收敛、单调不退化或开放域风险保证。

有限算子数不单独定义诱导假说类。任何统计保证均需先明确固定假说类、输入/输出域、损失及其有界性、独立同分布抽样、候选选择过程和训练/评测隔离，并证明这些条件适用于实验。未具备这些证据时，不报告 VC 维或泛化保证。

## 二、已知项目边界

当前 `ExperienceLibrary.recallForProposer` 是按文本 cue 匹配的正向召回，不是向量余弦召回，也没有负经验召回接口。`UNSCOPED_REPLAY` 只用于审计隔离；Stage 9 的 HISTORY/COMPOSITE 保持 `OBSERVED`、`failureObserved=true`、`attributionStatus=UNRESOLVED`，不进入正向经验或全局规则。`INVALID_FIXTURE` 必须由独立反事实证据确认，Stage 9 导入计数为 0。

派生 Stage 9 测试夹具须覆盖 probes、summary、selected candidate 三类文件的确定性脱敏、哈希重算和语义一致性校验。夹具通过仅证明派生夹具一致性，不证明原始 live 证据真实性。

## 三、当前结论

Stage 11A 的构建根因仍为 `UNPROVEN`，不得以两步构建成功声称单步 clean 修复；单步 clean 验收及夹具生成顺序见 62 Revision 2。当前仅获准修订文档并校验双哈希，本文件不授权源码、配置、测试数据、提交、切分支或付费 API 变更。
