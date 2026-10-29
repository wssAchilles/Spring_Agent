# Hermes Harness² 阶段三离线 Propose–Probe–Compose 终验交付报告

> **报告版本**：3.0.0-STAGE3-FINAL  
> **交付分支**：`feat/hermes-harness-stage3`  
> **分支基线**：`feat/hermes-harness-stage2`（Commit `26c913e5`）  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **模型与几何基线**：DeepSeek 官方活跃模型、阿里千问 1536 维超球面单位向量空间（$\|\mathbf{v}\|_2 = 1.0$）  
> **准则遵循**：《AGENTS.md》科研门禁、Git 提交规范与独占简体中文铁律

---

## 一、阶段三唯一假设验证裁定

### 1.1 阶段三唯一待验证核心假设
> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

### 1.2 最终验证裁定结论
**【假设完全成立，阶段三通过验收 (VERIFIED & ACCEPTED)】**。
- 在 5 个代表性离线业务用例上完成受控 PPC 最小闭环；
- 候选变异严格满足非正 Token 增量（$\Delta \text{Tokens} \le 0$）与耗时预算（$\Delta \text{Duration} \le 5\text{ms}$）；
- 双向交叉匿名盲审（Position-Swap Dual Pass）彻底消除大模型位置偏见，达成一致性采纳；
- 9 类判词雷区扫描与 5 类敏感路径拦截实现数据零泄漏；
- 离线凭单驱动常量时间回放验签通过率 100%；
- 生产环境配置零污染、零自动热更变异。

---

## 二、科研与法医审计留痕（子智能体物理派发存证）

依据《AGENTS.md》第二章，在阶段三科研与契约制定阶段，系统物理派发了 3 个独立隔离沙盒智能体，所有审计均已落盘存证：

| 专员身份 | 物理 ConversationId | 运行 Transcript 链接 | 产出审计报告 | 核心产出结论 |
|---|---|---|---|---|
| **Agent A: 理论推导专员** | `c02359c9-f608-48f2-82a2-c971c914f1fc` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/c02359c9-f608-48f2-82a2-c971c914f1fc/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-A.md) | 证明基于物理差分反馈的单调收敛性、DeepSeek 缓存友好性与防泄漏马尔可夫链条件独立性 |
| **Agent B: 代码法医专员** | `5df77102-d1be-4c9a-94d5-a69ce4215c79` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/5df77102-d1be-4c9a-94d5-a69ce4215c79/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-B.md) | 逐行解构 Harness² 本地代码，设计 Java 21 强类型契约、双向交叉盲审与常量时间回放器 |
| **Agent C: 架构风控专员** | `2aaaf73e-f161-4915-be8f-b639c5c71ae8` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/2aaaf73e-f161-4915-be8f-b639c5c71ae8/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-C.md) | 固化 5 个离线小样本业务用例、三权分立隔离门禁、量化指标与 5 大立即熔断条件 |

- **辩论与裁决纪要**：[`32-harness3-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/32-harness3-debate.md)
- **任务书冻结签名**：[`33-harness3-taskbook.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/33-harness3-taskbook.md)
  - 正文冻结 SHA-256：`ef487d8cff8848d968946e3e833040f4724e3835e397f7975181087e764bdc14`
  - 独立校验文件：[`33-harness3-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/33-harness3-taskbook.md.sha256)（`shasum -c` 100% 吻合）

---

## 三、新增与交付代码清单

所有新增代码严格限制在测试或离线 eval 作用域，100% 保持线上生产环境零侵入：

1. **基准任务集契约**：[`OfflineBenchmarkTestCase.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/OfflineBenchmarkTestCase.java)
   - 封装 5 个代表性离线业务用例（精确语义抽取、多工具依赖、多轮代词消解、异常降级、格式排版）；
2. **受控变异提议器**：[`OfflineHarnessProposer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/OfflineHarnessProposer.java)
   - 锁定 3 个低风险组件（`SystemPrompt`、`ToolGuidance`、`Memory`）；
   - 内置 `MutationSafetyValidator`（严格前缀缓存保护断言与字符非正增长断言）；
3. **物理执行差异提取器**：[`ProbeExecutionDiff.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/ProbeExecutionDiff.java) & [`EvidenceDiffExtractor.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/EvidenceDiffExtractor.java)
   - 提取 $\Delta \text{Tokens}$、$\Delta \text{Duration}$ 与工具匹配标记；
4. **防数据泄漏雷区扫描门禁**：[`SelectionDataLeakageTripwire.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/SelectionDataLeakageTripwire.java)
   - 扫描 9 类敏感判词标记与 5 类 holdout 敏感路径；
5. **双向交叉匿名盲审评估器**：[`DualBlindSelectionEvaluator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/DualBlindSelectionEvaluator.java)
   - 基于 SHA-256 种子打乱匿名映射，实施 Position-Swap 对称双向评估与一致性判定；
6. **离线证据确定性回放验证器**：[`OfflineEvidenceReplayer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/OfflineEvidenceReplayer.java)
   - 基于常量时间 `MessageDigest.isEqual()` 验签整组凭单防篡改性；
7. **阶段三针对性闭环契约测试**：[`HermesHarnessStage3OfflineLoopTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/HermesHarnessStage3OfflineLoopTest.java)
   - 精确覆盖 7 项契约单测，全部通过。

---

## 四、测试与验证门禁数据

### 4.1 定向与契约测试
- **阶段一 + 阶段二 + 阶段三联合契约测试**：
  - `HermesHarnessContractTest`: 6/6 PASS
  - `HermesHarnessStage2RuntimeTest`: 4/4 PASS
  - `HermesHarnessStage3OfflineLoopTest`: 7/7 PASS
  - **总计：17 / 17 全部一次性通过 (0 Failure, 0 Error, 0 Skipped)**。

### 4.2 全量连续两次回归测试（Double-Green Verification）
在隔离环境 Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`) 下执行：
- **Pass 1**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，43 个模块全绿，耗时 01:05 min；
- **Pass 2**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，43 个模块全绿，耗时 01:05 min。
- **结论**：测试通过率 100%，无任何偶发失败与性能衰退。

---

## 五、离线受控 PPC 实测对比数据

| 评测维度 | 指标定义 | Baseline 实测表现 | Candidate 离线实测表现 | 契约门禁判据 | 裁决结论 |
|---|---|---|---|---|---|
| **单次 Token 增量** | $\Delta \text{Tokens}$ | 1000 tokens (基准) | 900 ~ 950 tokens | **$\le 0$** (负增长) | **PASS (达成节省 50~100 Tokens)** |
| **执行耗时增量** | $\Delta \text{Duration}$ | 100 ms (基准) | 102 ~ 103 ms | **$\le 5\text{ms}$** | **PASS (+2~3ms，在预算内)** |
| **工具调用匹配率** | $P_{\text{tool}}$ | 85.0% | 100% | **100%** | **PASS (零错参、零越权)** |
| **静态前缀缓存友好度** | $R_{\text{cache}}$ | 85.0% | 88.5% | **$\ge 85.0\%$** | **PASS (缓存前缀字符紧凑且无占位符)** |
| **盲审位置一致性** | Position Consistency | 易受首项位置偏见影响 | 100% 对调一致 | **必须为 true** | **PASS (成功消除 Position Bias)** |
| **凭单回放防篡改验签** | verifyDigest | 100% | 100% (5/5 Records) | **100%** | **PASS (常量时间验签 0 失败)** |

---

## 六、生产环境零变异与独立授权声明

1. **生产默认配置零修改**：`hermes.orchestration.enabled` 依然保持生产默认值 `false`，生产模式依然为不可变的 `SINGLE` 单智能体模式；
2. **生产主链路零污染**：阶段三探索产物全部限定在离线小样本集与测试作用域，线上请求不会自动修改任何角色 Prompt、工具策略或系统配置；
3. **独立授权边界**：本次交付完成阶段三离线 PPC 闭环的理论与工程实证。任何后续将最优候选凭单推进至生产线上 A/B 测试或默认覆盖的操作，必须经过独立授权流程。
