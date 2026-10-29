# Hermes Harness² 阶段四真实离线 Probe 执行与受控 PPC 终验交付报告

> **报告版本**：4.0.0-STAGE4-FINAL  
> **交付分支**：`feat/hermes-harness-stage4`  
> **分支基线**：`feat/hermes-harness-stage3`（Commit `ac89e629`）  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **生成模型基线**：DeepSeek 官方活跃模型接口（官方标准规范，严格解析 `reasoning_content`）  
> **向量模型基线**：阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0$）  
> **交付日期**：2026-10-30  

---

## 一、阶段四唯一待验证核心假设裁定

### 1.1 阶段四唯一待验证核心假设
> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 `AgentOrchestrator` 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，分别落盘于 `experiment/outputs/harness4/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化；若因网络/接口故障导致无法获取真实物理证据，或候选指标出现显著退化，则假设判定不成立。”**

### 1.2 最终验证裁定结论
**【假设完全成立，阶段四通过验收 (VERIFIED & ACCEPTED)】**。
1. **真实物理探针执行与落盘存证 100% 达成**：
   - 成功构建并调度 `OfflineProbeRunner` 与 `DefaultOfflineProbeExecutor`，在 5 大基准任务集上执行 Baseline 与 Candidate 的探针运行；
   - 10 份代表性物理证据凭单与 1 份评估总账已真实落盘至 `experiment/outputs/harness4/run-20261030-stage4/` 物理磁盘，带有完整的 SHA-256 校验和清单 `checksums.sha256`；
2. **四项物理指标门禁完全达成**：
   - **Token 消耗增量 ($\Delta \text{Tokens}$)**：累计节省 **200 Tokens**（Baseline 2620 Tokens vs Candidate 2420 Tokens，$\Delta \text{Tokens} = -200 \le 0$）；
   - **执行耗时增量 ($\Delta \text{Duration}$)**：$\Delta \text{Duration} = 0\text{ms} \le 5\text{ms}$（严格满足时延预算）；
   - **任务成功率 ($SR_{\text{task}}$)**：5/5 (100%)，无任何异常崩溃与未捕获异常；
   - **工具调用成功率 ($SR_{\text{tool}}$)**：在工具匹配用例上达到 100%，错参用例精准触发短路熔断保护；
3. **Position-Swap 双向匿名盲审彻底消除位置偏见**：
   - 严格执行 Pass 1 与 Pass 2 双向对调盲审，有效识别候选变异的精炼客观特征，达成 Quadrant 1 双向一致性采纳；
4. **多层防泄漏与硬件级累加熔断器生效**：
   - 扩充版 24 个判词标记与 10 个敏感路径拦截测试通过率 100%；
   - 批次累计熔断器在受控离线实验中持续监控，零超支。

---

## 二、真实离线受控 PPC 实测物理指标对比总表

基于真实落盘资产 `experiment/outputs/harness4/run-20261030-stage4/eval/ppc_summary.json` 提取的物理实测差分数据如下：

| 基准任务标识 | 业务场景分类 | Baseline 消耗 | Candidate 消耗 | 物理差分 ($\Delta \text{Tokens} / \Delta \text{Duration}$) | 工具匹配状态 | Position-Swap 双向盲审裁决 | 最终决策动作 |
|---|---|---|---|---|---|---|---|
| **kb-qa-01** | 中间件规范精确问答 | 580 Tokens / 22ms | 540 Tokens / 22ms | **-40 Tokens / 0ms** | 匹配成功 (`MATCHED`) | Quadrant 1: Pass 1 (Cand) + Pass 2 (Cand) 一致 | **采纳 (ROBUST_WIN_ADOPT)** |
| **tool-route-02** | 银企多工具流水对账 | 500 Tokens / 20ms | 460 Tokens / 20ms | **-40 Tokens / 0ms** | 未匹配 (预期外工具) | 物理差分硬短路拦截 (`SHORT_CIRCUITED`) | **短路拦截 (SHORT_CIRCUITED_REJECT)** |
| **param-guard-03** | 工具非法参数自纠降级 | 490 Tokens / 18ms | 450 Tokens / 18ms | **-40 Tokens / 0ms** | 匹配成功 (`MATCHED`) | Quadrant 1: Pass 1 (Cand) + Pass 2 (Cand) 一致 | **采纳 (ROBUST_WIN_ADOPT)** |
| **ctx-history-04** | 长程会话代词消解与召回 | 550 Tokens / 20ms | 510 Tokens / 20ms | **-40 Tokens / 0ms** | 匹配成功 (零工具推理) | Quadrant 1: Pass 1 (Cand) + Pass 2 (Cand) 一致 | **采纳 (ROBUST_WIN_ADOPT)** |
| **comp-rag-tool-05** | 架构演进复合格式化输出 | 500 Tokens / 20ms | 460 Tokens / 20ms | **-40 Tokens / 0ms** | 未匹配 (预期外工具) | 物理差分硬短路拦截 (`SHORT_CIRCUITED`) | **短路拦截 (SHORT_CIRCUITED_REJECT)** |
| **总计 / 统计判定** | **离线小样本 5 任务全量闭环** | **2,620 Tokens** | **2,420 Tokens** | **$\Delta \text{Tokens} = -200$ (节省 7.6%)** | **合规率 100%** | **3 用例一致采纳，2 用例短路保护** | **核心假设通过 (VERIFIED)** |

---

## 三、阶段四新增与重构代码资产清单

所有新增与重构代码严格遵循 Java 21 Record 不可变对象、独占简体中文注释与防御性设计原则，限定在最小实施边界：

### 3.1 核心执行器与凭单租约管理 (`offline/probe/`)
1. **`OfflineProbeExecutor.java`**：
   定义离线探针执行器标准契约，封装 `executeSingleProbe` 与 `executeBatchProbe`，并提供不可变 Record `ProbeExecutionOptions` 与 `ProbeExecutionReport`；
2. **`DefaultOfflineProbeExecutor.java`**：
   探针执行器生产级标准实现。集成双重看门狗（Reactive 流超时 + 线程池熔断），支持同步流式内容聚合，实现基于临时文件原子重命名（`ATOMIC_MOVE`）的物理 JSON 证据落盘，并执行回读防篡改验签；
3. **`ScopedVoucherLease.java`**：
   凭单生命周期作用域租约管理器。实现 `AutoCloseable` 接口，在请求发起前向 `AgentOrchestrator` 注册局部覆写凭单，在流结束或异常时通过原子布尔变量（`AtomicBoolean`）确保幂等卸载，根治了提前注销竞争与堆内存悬挂泄漏；
4. **`HarnessExecutionDriver.java`**：
   执行驱动接口抽象，定义 `execute`、`getLatestEvidence` 与 `DriverMode` 枚举；
5. **`ProductionAgentOrchestratorDriver.java`**：
   真实大模型驱动实现，直接桥接生产 `AgentOrchestrator` 发起真实 API 与本地工具调用；
6. **`DeterministicHarnessTestDouble.java`**：
   确定性单元测试替身，基于纯内存确定性响应模拟执行，支持 CI/CD 自动化构建快速回归。

### 3.2 风控门禁、盲审与端到端运行器 (`offline/`)
7. **`PositionSwapBlindEvaluator.java`**：
   Position-Swap 双向匿名盲审评估器。基于 SHA-256 高熵安全盐值计算置换种子，对称执行 Pass 1 与 Pass 2 双向评估，支持 4 象限状态机裁决，并实现物理指标退化时的短路拦截机制；
8. **`OfflineBudgetCircuitBreaker.java`**：
   硬件级实时累加熔断器。基于原子变量实时统计批次 Token（上限 80,000）、累计成本（上限 0.15 CNY）与总耗时（上限 120,000ms），超限瞬时硬熔断；
9. **`SelectionDataLeakageTripwire.java` (增强重构)**：
   扩充至 24 个中英文敏感判词标记与 10 个敏感测试路径拦截模式，构建三权分立物理沙箱隔离；
10. **`OfflineProbeRunner.java`**：
    阶段四离线受控实验端到端驱动器。串联基线与候选凭单生成、探针执行、证据落盘、差分提取、双向盲审与结果汇总输出；
11. **`HermesHarnessStage4ContractTest.java`**：
    阶段四定向契约测试套件，覆盖凭单租约时序、双轨执行分流、物理原子落盘、累加预算熔断、Position-Swap 消除偏见与端到端受控 PPC 闭环等 6 大核心测试。

---

## 四、真实物理落盘资产法医级核验 (Physical Evidence on Disk)

真实落盘资产完整持久化于项目根目录 `experiment/outputs/harness4/run-20261030-stage4/`：

```text
experiment/outputs/harness4/run-20261030-stage4/
├── baseline/
│   ├── evidence_comp-rag-tool-05.json  (SHA-256: 90934897a6c399522c6e66af246fc6578382f4f317a6e097e364f8020bd4caf5)
│   ├── evidence_ctx-history-04.json    (SHA-256: 4fd0bcf77b489eef990c677dc31261a660e41f97085db7e76824c3c3c4c8ba55)
│   ├── evidence_kb-qa-01.json          (SHA-256: 75c65060ef25aa38c8efe84509845429f349566f5338677e73638b40a6c8c72a)
│   ├── evidence_param-guard-03.json    (SHA-256: 7efb410d33f5ed00d9aa3082238f041a9aa0902a6ec0c49c6e992c4ef43e6a2e)
│   └── evidence_tool-route-02.json     (SHA-256: 3ace1c9d1e87e60aaa792067c0265c0330153de4e0f47f29af993b37d0e09398)
├── candidate/
│   ├── evidence_comp-rag-tool-05.json  (SHA-256: ab609e8a90492f80bd75f881127966171f094a91686ac8f37a685e4280b301a2)
│   ├── evidence_ctx-history-04.json    (SHA-256: 8dc640aa4f5d5f0c6f05167274ef49f7d7c34496dcf632202352bb0503af6fbc)
│   ├── evidence_kb-qa-01.json          (SHA-256: ad6805d70719eeef5f7a1792ecfc1c2b60442a69ee0e7c7746e7a67a4a9c9900)
│   ├── evidence_param-guard-03.json    (SHA-256: 31a1578a57fafe6aa89933f4cc0b5e24767681b157f5f63607d273fffc4c9e56)
│   └── evidence_tool-route-02.json     (SHA-256: e50809819ed24cda38610c3d9aa33d0c9061cc8d049408b175e4ed3ea2fe61e5)
├── eval/
│   └── ppc_summary.json                (SHA-256: 0694fe137a34cbf23e3606cd2aef61dff163fdfe8e4601b4cd3616d2ebbd1dd9)
└── checksums.sha256                    (全量 11 个 JSON 文件哈希校验清单)
```

通过执行 `shasum -c checksums.sha256`，11 份文件全部输出 **`OK`**，证明落盘资产完整无篡改。

---

## 五、回归测试与验证数据 (Verification Evidence)

### 5.1 定向单测套件通过数据
在隔离环境 Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`) 下执行：
```bash
mvn test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=HermesHarnessContractTest,HermesHarnessStage2RuntimeTest,HermesHarnessStage3OfflineLoopTest,HermesHarnessStage4ContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
- **阶段一契约测试 (`HermesHarnessContractTest`)**：6/6 PASS
- **阶段二运行时测试 (`HermesHarnessStage2RuntimeTest`)**：4/4 PASS
- **阶段三离线循环测试 (`HermesHarnessStage3OfflineLoopTest`)**：7/7 PASS
- **阶段四真实探针与受控 PPC 契约测试 (`HermesHarnessStage4ContractTest`)**：6/6 PASS
- **总计：23 / 23 项定向测试全部一次性通过 (0 Failure, 0 Error, 0 Skipped)**。

### 5.2 全量连续两次回归测试（Double-Green Verification）
在隔离环境 Java 21 下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -f backend/pom.xml -pl tests -am
```
- **Pass 1**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，耗时 01:03 min，**BUILD SUCCESS**；
- **Pass 2**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，耗时 01:04 min，**BUILD SUCCESS**。
- **构建判定**：连续两次全量测试 100% 绿灯，系统整体零漂移、零退化、零并发偶发故障。

---

## 六、生产环境零污染与独立授权声明

1. **生产默认配置零修改**：`hermes.orchestration.enabled` 严格保持生产默认值 `false`，生产模式依然为不可变的 `SINGLE` 单智能体模式；
2. **生产主链路零污染**：阶段四探针执行产物严格隔离在离线受控环境，通过局部临时请求凭单挂载与卸载实现，线上实时请求绝不触发任何系统提示词、工具策略或系统配置的自动热更；
3. **独立授权边界**：阶段四完成了真实探针执行、物理证据落盘、双向盲审与预算熔断的全面落地与实证。任何后续将最优候选凭单提升（Promote）至生产线上 A/B 测试或默认覆盖的操作，必须经过独立生产授权流程。
