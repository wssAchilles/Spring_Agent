# Stage 11A 契约复核与阻断项审计

> **状态：REVISION 2 REVIEW REQUIRED。** 本审计修订稿用于解释 Stage 11 为何继续 `BLOCKED`；本轮仅归档提交 61/62 文档，不授权源码修改、Stage 10 实现提交、切分支或调用付费 API。后续实现契约以 [62-harness11-taskbook.md](62-harness11-taskbook.md) Revision 2 为准。

- **审计对象**：Stage 10 Experience Library、Stage 9 回放导入、Maven clean 构建与旧 Stage 11 理论/辩论草稿。
- **当前分支**：`feat/hermes-harness-stage10`。
- **审计结论**：旧任务书包含会造成伪结论的契约错误；先修订冻结文档，再决定是否放行 11A。11B 递归 PPC 延期。

### Stage 10 未跟踪 Java 文件清单

主源码目录：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/experience/`，共 12 个文件：

1. `ExperienceLibrary.java`
2. `DefaultExperienceLibrary.java`
3. `ExperienceDomain.java`
4. `ExperienceEntry.java`
5. `ExperienceDisposition.java`
6. `ExperienceLeakageGuard.java`
7. `ExperienceRecall.java`
8. `ExperienceRunSummaryRecord.java`
9. `ExperienceStore.java`
10. `FileSystemExperienceStore.java`
11. `EvidenceReference.java`
12. `Stage9ExperienceImporter.java`

测试目录：`backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/experience/`，共 2 个文件：`ExperienceLibraryContractTest.java`、`HarnessExperienceStage10Test.java`。这些文件仅列入后续 11A 范围，本轮不暂存或提交。

## 一、构建证据：根因未证实

已观察到的材料包括：一次 clean 构建在 proto 生成代码编译时报错，日志列出 47 个源文件；另有两步或单独 proto 构建成功的记录。它们不能证明失败文件在 javac 读取时已存在，也不能证明 Source Root 配置正确或 Reactor 生命周期发生竞争。因此当前根因固定为 `UNPROVEN`。

不得以“两步构建成功”声称单步 clean 已修复。若要报告本环境单步验证通过，必须在固定 Java 21、Maven、依赖和 HEAD 下连续 3 次执行同一条单步 `clean test`，两次间零代码/配置/测试改动，并保存每次完整日志、Reactor 顺序、生成文件数、失败目标文件状态、编译器版本与退出码。即便三次成功，也只能证明本环境重复通过，不能倒推历史失败根因。无法复现时继续标记 `UNPROVEN`；proto 预构建若作为 CI 契约，须明确承认单步 clean 风险仍在。

## 二、派生夹具：三类文件都必须脱敏并重算

原始 Stage 9 live 资产只作本地只读证据，不能进入版本库。测试夹具必须同时覆盖 `probes`、`ppc_summary.json`、`selected_candidate.json`，对自由文本按固定 allowlist 投影或替换。summary 内嵌 probes 必须与独立 probes 一致；selected candidate 的 verdict、reason、selection 和 voucher fingerprint 必须与派生 summary/candidate 满足定义好的语义关系，不能复用已因脱敏而失效的原始指纹。

固定顺序：

```text
原始冻结文件只读核对
→ 对 probes / summary / selected 做确定性脱敏
→ 重算 summary.checksumsSha256
→ 校验 summary.probes 与独立 probes
→ 生成派生 checksums.sha256
→ 计算派生 manifest 哈希并写入测试 descriptor
→ 验证篡改、双重重算及 summary/selected 语义不一致均被拒绝
```

需记录原始相对路径/哈希到派生相对路径/哈希的映射和脱敏规则版本，不复制原始敏感内容。夹具测试通过仅证明派生夹具内部契约一致及指定投影字段相符，不证明 Stage 9 原始证据真实性。

## 三、经验召回契约：按现有实现表述

现有 `ExperienceLibrary.recallForProposer` 是基于文本 cue 的同域正向召回，并过滤不符合正向准入条件的 `REJECTED`、`failureObserved`、软淘汰及 `UNSCOPED_REPLAY` 条目。该接口不是余弦相似度召回，也不提供负经验召回或负掩码读取路径。11A 保持此接口现状，不新增 `recallForAudit`、负掩码或 embedding 召回。

Stage 9 的 HISTORY/COMPOSITE 只能记为 `OBSERVED + failureObserved=true + attributionStatus=UNRESOLVED`。`candidate-a/b` 的 `REJECTED` 是该次冻结回放中的处置结果，不证明全局因果规律。`INVALID_FIXTURE` 只有在独立反事实证据确认夹具/工具参数/能力装配问题后才允许构造；当前 Stage 9 导入计数必须为 0。任何 Stage 9 结果都不得转写为跨域正向或负向规则。

## 四、理论结论撤回

旧 Agent A、研究与辩论草稿中的 VC 维上界、跨任务泛化界、经验风险截断“定理”、余弦阈值 0.65、负掩码和 Stage 9 全局规则均撤回。有限算子池仅约束候选生成机制的操作数，不自动确定 induced hypothesis class 的 VC 维或统计泛化界。若未来保留泛化结论，必须定义固定假说类、词表/长度、损失范围、独立同分布抽样、选择过程和训练/评测隔离，并证明这些前提适用；否则只能表述为待检验启发式。

## 五、阶段拆分与准入结论

- **11A**：只收口 Stage 10 稳定化契约，完成可移植派生夹具与默认离线测试等最小验收。
- **11B**：经验感知 Proposer、递归 PPC、停止策略与相应机制测试；延期，需单独任务书及明确授权。
- **当前结论**：Stage 11 继续 `BLOCKED`。修订与双哈希校验通过后仍须用户复核及另行授权，才可实施 11A。

---
本审计仅记录契约复核结论，不构成源码实现或理论证明。
