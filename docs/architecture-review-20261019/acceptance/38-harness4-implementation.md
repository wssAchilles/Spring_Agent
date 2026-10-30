# Hermes Harness² 阶段四离线 Probe 执行器契约骨架与合成替身验证报告

> **报告版本**：4.1.0-STAGE4-REVISED  
> **交付分支**：`feat/hermes-harness-stage4`  
> **分支基线**：`feat/hermes-harness-stage3`（Commit `ac89e629`）  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **生成模型基线**：DeepSeek 官方活跃模型接口（阶段四仅使用确定性测试替身 `DETERMINISTIC_TEST_DOUBLE` 验证执行器契约，真实 DeepSeek API 调用待阶段五执行）  
> **向量模型基线**：阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0$）  
> **修订日期**：2026-10-30  

---

## 一、阶段四唯一待验证核心假设与阶段性裁定

### 1.1 阶段四核心假设
> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 `AgentOrchestrator` 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 `HarnessEvidenceRecord` 为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化。”**

### 1.2 修订后的客观裁定结论
**【阶段四完成了真实 Probe 执行器的契约骨架，并用确定性测试替身（`DETERMINISTIC_TEST_DOUBLE`）验证了落盘、熔断、盲审与回放；`ProductionAgentOrchestratorDriver` 尚未被实际调用，真实 DeepSeek Probe 待阶段五执行，核心假设尚未在真实生产轨道验证 (NOT_YET_VERIFIED_ON_PRODUCTION_TRACK)】**。

1. **离线探针执行器契约骨架与合成替身闭环 100% 达成**：
   - 构建并验证了 `OfflineProbeRunner`、`DefaultOfflineProbeExecutor`、`ScopedVoucherLease` 与 `ProductionAgentOrchestratorDriver` 的完整架构代码；
   - 为 `HarnessEvidenceRecord` 引入显式的 `ExecutionMode` 枚举（`REAL_PRODUCTION` / `DETERMINISTIC_TEST_DOUBLE` / `FAIL_SAFE`）并纳入 SHA-256 防篡改摘要；
   - 阶段四单元测试使用 `DeterministicHarnessTestDouble` 生成的 10 份合成凭单与 1 份评估总账已物理隔离至 `experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic/`，并全部显式标记 `"executionMode":"DETERMINISTIC_TEST_DOUBLE"`；
2. **合成测试替身验证的契约机制（非真实模型收益）**：
   - 在确定性测试替身注入的合成数据（固定候选减少 40 Tokens）下，验证了 `PositionSwapBlindEvaluator` 双向匿名盲审、物理指标退化硬短路门禁、`SelectionDataLeakageTripwire` 防泄漏扫描（24 个判词标记 + 10 个敏感路径）与 `OfflineBudgetCircuitBreaker` 累加熔断器的代码逻辑正确性；
   - 上述合成指标仅代表测试替身 fixture 的契约验证结果，**不代表真实 DeepSeek 模型与真实 `AgentOrchestrator` 的运行收益**。真实生产轨道探针执行移交阶段五完成。

---

## 二、确定性测试替身（`DETERMINISTIC_TEST_DOUBLE`）契约验证数据表

基于合成隔离资产 `experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic/eval/ppc_summary.json` 的测试替身契约验证数据如下（注：以下数值由 `DeterministicHarnessTestDouble` 合成构造，仅用于验证评估器与短路状态机契约）：

| 基准任务标识 | 业务场景分类 | 执行模式 (`executionMode`) | 替身 Baseline 消耗 | 替身 Candidate 消耗 | 合成差分 ($\Delta \text{Tokens} / \Delta \text{Duration}$) | 工具匹配状态 | Position-Swap 盲审状态机验证 |
|---|---|---|---|---|---|---|---|
| **kb-qa-01** | 中间件规范精确问答 | `DETERMINISTIC_TEST_DOUBLE` | 580 Tokens / 22ms | 540 Tokens / 22ms | -40 Tokens / 0ms (合成) | 匹配成功 (`MATCHED`) | Quadrant 1 一致性分支验证通过 (`ROBUST_WIN_ADOPT`) |
| **tool-route-02** | 银企多工具流水对账 | `DETERMINISTIC_TEST_DOUBLE` | 500 Tokens / 20ms | 460 Tokens / 20ms | -40 Tokens / 0ms (合成) | 未匹配 (预期外工具) | 物理差分硬短路拦截验证通过 (`SHORT_CIRCUITED_REJECT`) |
| **param-guard-03** | 工具非法参数自纠降级 | `DETERMINISTIC_TEST_DOUBLE` | 490 Tokens / 18ms | 450 Tokens / 18ms | -40 Tokens / 0ms (合成) | 匹配成功 (`MATCHED`) | Quadrant 1 一致性分支验证通过 (`ROBUST_WIN_ADOPT`) |
| **ctx-history-04** | 长程会话代词消解与召回 | `DETERMINISTIC_TEST_DOUBLE` | 550 Tokens / 20ms | 510 Tokens / 20ms | -40 Tokens / 0ms (合成) | 匹配成功 (零工具推理) | Quadrant 1 一致性分支验证通过 (`ROBUST_WIN_ADOPT`) |
| **comp-rag-tool-05** | 架构演进复合格式化输出 | `DETERMINISTIC_TEST_DOUBLE` | 500 Tokens / 20ms | 460 Tokens / 20ms | -40 Tokens / 0ms (合成) | 未匹配 (预期外工具) | 物理差分硬短路拦截验证通过 (`SHORT_CIRCUITED_REJECT`) |

---

## 三、阶段四新增与重构代码资产清单

所有新增与重构代码严格遵循 Java 21 Record 不可变对象、独占简体中文注释与防御性设计原则，限定在最小实施边界：

### 3.1 证据执行模式溯源与核心采集器 (`harness/`)
1. **`HarnessEvidenceRecord.java`**：
   新增 `ExecutionMode` 枚举（`REAL_PRODUCTION`、`DETERMINISTIC_TEST_DOUBLE`、`FAIL_SAFE`）与 `executionMode` 字段，并将其纳入 `computeDigest()` 的 SHA-256 签名计算中，从密码学层面杜绝将测试替身产物伪造为真实生产证据；
2. **`HarnessEvidenceCollector.java`**：
   在生产运行时 `finalizeEvidence()` 中显式打标 `ExecutionMode.REAL_PRODUCTION`。

### 3.2 核心执行器与凭单租约管理 (`offline/probe/`)
3. **`OfflineProbeExecutor.java`**：
   定义离线探针执行器标准契约，封装 `executeSingleProbe` 与 `executeBatchProbe`；
4. **`DefaultOfflineProbeExecutor.java`**：
   探针执行器标准实现，集成双重看门狗、原子重命名（`ATOMIC_MOVE`）JSON 落盘与常量时间回读验签，降级凭单显式打标 `ExecutionMode.FAIL_SAFE`；
5. **`ScopedVoucherLease.java`**：
   凭单生命周期作用域租约管理器（`AutoCloseable`），确保局部覆写凭单幂等卸载与零内存泄漏；
6. **`HarnessExecutionDriver.java`**：
   执行驱动接口抽象，定义 `execute`、`getLatestEvidence` 与 `DriverMode` 枚举；
7. **`ProductionAgentOrchestratorDriver.java`**：
   真实大模型驱动实现骨架，桥接 `AgentOrchestrator`（待阶段五在真实 DeepSeek 轨道上启用实测）；
8. **`DeterministicHarnessTestDouble.java`**：
   确定性单元测试替身，显式打标 `ExecutionMode.DETERMINISTIC_TEST_DOUBLE`。

### 3.3 风控门禁、盲审与端到端运行器 (`offline/`)
9. **`PositionSwapBlindEvaluator.java`**：
   Position-Swap 双向匿名盲审评估器与物理指标退化硬短路门禁；
10. **`OfflineBudgetCircuitBreaker.java`**：
    实时累加熔断器（监控批次 Token、累计成本与总耗时）；
11. **`SelectionDataLeakageTripwire.java`**：
    扩充至 24 个中英文敏感判词标记与 10 个敏感测试路径拦截模式；
12. **`OfflineProbeRunner.java` & `HermesHarnessStage4ContractTest.java`**：
    阶段四离线受控实验驱动器与 6 项契约单元测试套件。

---

## 四、合成测试替身落盘资产核验 (`harness4-synthetic`)

阶段四测试替身生成的合成资产已从原路径移除，重新隔离落盘于 `experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic/`：

```text
experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic/
├── baseline/
│   ├── evidence_comp-rag-tool-05.json  (SHA-256: a1242ec28b01282b1baaec439cc5646bb2116316f95d5cac611b80ca862944b0)
│   ├── evidence_ctx-history-04.json    (SHA-256: d972d7da1a461170e7654ce6ec2f04a5d5e260fc4bb2ae9a34be123189d9ec80)
│   ├── evidence_kb-qa-01.json          (SHA-256: 59f134af15ad80a282e9dab1abed32c61a308a4551810f68439026421342228e)
│   ├── evidence_param-guard-03.json    (SHA-256: b941513578c23fabbfddec046a75dd97435ff2f186fe1aa0e339712d5a5cbfd8)
│   └── evidence_tool-route-02.json     (SHA-256: ad361b69f85617b71ba9bd973ab31f5b45fbe4335d0a257a104bcccec88f4973)
├── candidate/
│   ├── evidence_comp-rag-tool-05.json  (SHA-256: e2f9b5d07d9524810386e1b6d025a209cb1bc653ec17e821e0bd55ca381e1179)
│   ├── evidence_ctx-history-04.json    (SHA-256: cd6f6a9fa70ea80c85461dc23f616067286000a8c61e4492794fab46f6375f8d)
│   ├── evidence_kb-qa-01.json          (SHA-256: 58255728b81668b7537af8665096040d865c47a840b6e8ac4aaac51a14a418c4)
│   ├── evidence_param-guard-03.json    (SHA-256: 3c33dcca98c2b36e7a7a3d6a09332d3f47801b22774e908428d7e5d391cd0a1d)
│   └── evidence_tool-route-02.json     (SHA-256: 75fa4a8e205def3ee6fd2d2bfe9b6dc32338bde09c6f9411ac96e6799eb00916)
├── eval/
│   └── ppc_summary.json                (SHA-256: 7e210ab4cbb058f7868d1b06e4b330211f394ad6cee508c8a007dfd307a1c738)
└── checksums.sha256                    (全量 11 个合成 JSON 文件哈希校验清单)
```

---

## 五、回归测试与生产环境零污染声明

1. **定向单测套件**：`HermesHarnessContractTest` (6) + `HermesHarnessStage2RuntimeTest` (4) + `HermesHarnessStage3OfflineLoopTest` (7) + `HermesHarnessStage4ContractTest` (6) = **23 / 23 PASS**；
2. **生产环境零污染**：`hermes.orchestration.enabled` 严格保持生产默认值 `false`，生产默认仍为不可变的 `SINGLE` 模式；真实生产轨道 DeepSeek Probe 验证将在阶段五独立分支 `feat/hermes-harness-stage5` 中执行。
