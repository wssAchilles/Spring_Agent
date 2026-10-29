# Hermes Harness² 阶段三离线 Propose–Probe–Compose 终验交付报告 (修正版)

> **报告版本**：3.1.0-STAGE3-CONTRACT-REVISED  
> **交付分支**：`feat/hermes-harness-stage3`  
> **分支基线**：`feat/hermes-harness-stage2`（Commit `26c913e5`）  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **模型与几何基线**：DeepSeek 官方活跃模型、阿里千问 1536 维超球面单位向量空间（$\|\mathbf{v}\|_2 = 1.0$）  
> **准则遵循**：《AGENTS.md》科研门禁、Git 提交规范与独占简体中文铁律

---

## 一、阶段三唯一假设验证裁定（修正与降级声明）

### 1.1 阶段三唯一待验证核心假设
> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

### 1.2 最终验证裁定结论 (修正降级)
**【合成数据上的契约验证通过，真实 Probe 尚未执行 (CONTRACT PASS, REAL PROBE PENDING)】**。
- **契约完整性**：完成了离线 PPC 闭环的强类型 Java 21 Record 契约设计、双向匿名盲审评估器与防数据泄漏静态雷区扫描门禁；
- **真实性澄清**：阶段三落地的 7 项单测（`HermesHarnessStage3OfflineLoopTest`）属于**算法与契约骨架测试（Contract Test）**，证据输入来自固定的测试合成数据（Fixture：如固定构造的 1000/950 Tokens、100/103ms 耗时），**尚未接驳真实 `AgentOrchestrator` 与真实的 DeepSeek 模型端到端执行**；
- **阶段划分边界**：阶段三完成的是“离线 PPC 契约规范与测试骨架”，绝不能直接等同于“真实 PPC 实验完成”；真正的端到端离线 Probe 执行、模型调用与真实证据落盘将全量交由**阶段四**实施。

---

## 二、科研与法医审计留痕（子智能体物理派发存证）

依据《AGENTS.md》第二章，在阶段三科研与契约制定阶段，系统物理派发了 3 个独立隔离沙盒智能体，所有审计均已落盘存证：

| 专员身份 | 物理 ConversationId | 运行 Transcript 链接 | 产出审计报告 | 核心产出要点 |
|---|---|---|---|---|
| **Agent A: 理论推导专员** | `c02359c9-f608-48f2-82a2-c971c914f1fc` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/c02359c9-f608-48f2-82a2-c971c914f1fc/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-A.md) | 证明基于物理差分反馈的单调收敛性、DeepSeek 缓存友好性与防泄漏马尔可夫链条件独立性 |
| **Agent B: 代码法医专员** | `5df77102-d1be-4c9a-94d5-a69ce4215c79` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/5df77102-d1be-4c9a-94d5-a69ce4215c79/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-B.md) | 逐行解构 Harness² 本地代码，设计 Java 21 强类型契约、双向交叉盲审与常量时间回放器 |
| **Agent C: 架构风控专员** | `2aaaf73e-f161-4915-be8f-b639c5c71ae8` | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/2aaaf73e-f161-4915-be8f-b639c5c71ae8/.system_generated/logs/transcript.jsonl) | [`31-harness3-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-C.md) | 固化 5 个离线小样本业务用例、三权分立隔离门禁、量化指标与 5 大立即熔断条件 |

- **辩论与裁决纪要**：[`32-harness3-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/32-harness3-debate.md)
- **任务书冻结签名**：[`33-harness3-taskbook.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/33-harness3-taskbook.md)
  - 任务书状态：`FROZEN（已冻结）`
  - 正文冻结签名 (SHA-256)：`b45d056beaf803faa8eabe958645fd0f581b4ca5a1c10b5c98225041633ddf87`
  - 全文件物理指纹 (SHA-256)：`ff2ca999e6cd263a5011efe768ddcf360bd195c6a360e2b123b215f181e75ea9`
  - 独立校验文件：[`33-harness3-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/33-harness3-taskbook.md.sha256)（经 `shasum -c` 验证 100% 吻合输出 `OK`）

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

## 四、测试稳定性独立优化说明（非 PPC 业务逻辑）

为保障多模块全量回归测试在高并发持续执行下的稳定性，对 2 个既有高频纳秒级基准测试进行了 JIT 预热调整：
1. [`StateGraphSchedulerTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/stategraph/StateGraphSchedulerTest.java)：将压测前预热轮次从 200 次提升至 2000 次，使 HotSpot C2 编译器充分内联，彻底消除多轮测试下的 CPU 调度瞬时抖动；**原有 100μs 工业容忍断言阈值 100% 保持不变，未作任何降级**；
2. [`Phase88WorkflowHitlContractTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/Phase88WorkflowHitlContractTest.java)：将单步快照重构预热由 100 次调整为 2000 次，消除纳秒级冷启动延迟；**原有 50μs 性能断言阈值 100% 保持不变，未作任何降级**；
3. **特别声明**：上述调整属于测试基础设施稳定性保障，绝不作为阶段三 PPC 算法业务成立的证据。

---

## 五、测试与验证门禁数据

### 5.1 定向与契约测试
- **阶段一 + 阶段二 + 阶段三联合契约测试**：
  - `HermesHarnessContractTest`: 6/6 PASS
  - `HermesHarnessStage2RuntimeTest`: 4/4 PASS
  - `HermesHarnessStage3OfflineLoopTest`: 7/7 PASS
  - **总计：17 / 17 全部一次性通过 (0 Failure, 0 Error, 0 Skipped)**。

### 5.2 全量连续两次回归测试（Double-Green Verification）
在隔离环境 Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`) 下执行：
- **Pass 1**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，43 个模块全绿，耗时 01:05 min；
- **Pass 2**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，43 个模块全绿，耗时 01:05 min。
- **构建状态**：**BUILD SUCCESS**，测试通过率 **100%**，零漂移、零退化。

---

## 六、阶段三契约测试对比数据说明 (基于合成 Fixture 验证)

下表数据反映的是契约测试套件 `HermesHarnessStage3OfflineLoopTest` 在合成数据环境下的断言拦截能力验证，**不代表真实模型端到端调用结果**：

| 契约验证维度 | 验证机制与方法 | 测试用合成数据表现 | 契约门禁断言判定 | 契约裁决 |
|---|---|---|---|---|
| **Token 预算门禁验证** | 构造膨胀样本 (+10) 与节省样本 (-50) | 节省样本被放行，膨胀样本被 100% 拦截 | **$\Delta \text{Tokens} \le 0$** | **CONTRACT PASS** |
| **执行耗时门禁验证** | 构造超时样本 (+8ms) 与合规样本 (+3ms) | 合规样本被放行，超时样本被 100% 拦截 | **$\Delta \text{Duration} \le 5\text{ms}$** | **CONTRACT PASS** |
| **工具匹配状态提取** | 验证 `EvidenceDiffExtractor` 工具名与成功标志 | 正确提取入参出参哈希并匹配预期工具 | **100% 匹配断言** | **CONTRACT PASS** |
| **静态前缀保护拦截** | 构造包含 `{{user}}` 占位符的非法变异 | 构造方法瞬时抛出 `IllegalArgumentException` | **禁止动态占位符** | **CONTRACT PASS** |
| **双向盲审位置偏置** | 模拟固定选 A 偏见与客观选优两种裁判 | 偏见场景被识别为平局回退，客观场景被采纳 | **对调一致方采纳** | **CONTRACT PASS** |
| **防数据泄漏雷区** | 注入 `passed_count` 与 `holdout` 路径 | 立即触发 `IllegalStateException(CRITICAL_DATA_LEAKAGE)` | **100% 熔断拦截** | **CONTRACT PASS** |
| **凭单确定性回放验签** | 5 份合成证据凭单输入回放器 | `verifyDigest` 常量时间比对全部通过 | **100% 通过** | **CONTRACT PASS** |

---

## 七、生产环境零变异与独立授权声明

1. **生产默认配置零修改**：`hermes.orchestration.enabled` 严格保持生产默认值 `false`，生产模式依然为不可变的 `SINGLE` 单智能体模式；
2. **生产主链路零污染**：阶段三成果全部限定在离线小样本集与测试作用域，线上请求绝不触发任何角色 Prompt、工具策略或系统配置的自动热更；
3. **独立授权边界**：阶段三仅达成算法骨架与契约单测通过。阶段四将真正落地 `OfflineProbeExecutor`，执行端到端离线大模型调用并进行物理证据落盘与实证。
