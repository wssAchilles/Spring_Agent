# Hermes Harness² 阶段四真实探针离线 PPC 实施任务书 (FROZEN TASKBOOK)

> **任务书版本**：4.0.0-STAGE4-FROZEN  
> **任务书状态**：FROZEN（已冻结）  
> **正文冻结 SHA-256 签名**：`c34b024afa8a4739f1113055951519120fbc6c71b61c474044dc5163486a5d0f`  
> **正文签名复算命令**：`sed -n '/## 一、阶段四唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/37-harness4-taskbook.md | shasum -a 256`  
> **全文件校验文件**：`docs/architecture-review-20261019/acceptance/37-harness4-taskbook.md.sha256`  
> **关联分支基线**：`feat/hermes-harness-stage3`（Commit `ba46de4a`）  
> **实施目标分支**：`feat/hermes-harness-stage4`  
> **运行环境约束**：统一且唯一使用 SDKMAN 管理的 Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）  
> **大模型规范**：DeepSeek 官方活跃模型；阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1$，余弦度量）  
> **冻结日期**：2026-10-30  

---

## 一、阶段四唯一待验证核心假设与契约基准

### 1.1 唯一待验证核心假设
> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 `AgentOrchestrator` 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，分别落盘于 `experiment/outputs/harness4/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化；若因网络/接口故障导致无法获取真实物理证据，或候选指标出现显著退化，则假设判定不成立。”**

### 1.2 阶段四验收通过充要条件（必须同时满足）
1. **真实物理探针执行与文件落盘**：
   - 真实驱动 `AgentOrchestrator` 与 DeepSeek 官方活跃模型完成 5 个离线任务的探针执行；
   - 物理生成 `experiment/outputs/harness4/<runId>/baseline/evidence_<taskId>.json`（5 份）；
   - 物理生成 `experiment/outputs/harness4/<runId>/candidate/evidence_<taskId>.json`（5 份）；
   - 所有落盘 JSON 均包含出入参哈希与常量时间验签 `runmarkDigest`。
2. **四项物理量化指标门禁**：
   - 任务成功率：$SR_{\text{cand}} \ge SR_{\text{base}}$（严禁退化）；
   - 工具调用成功率：$SR_{\text{tool, cand}} \ge SR_{\text{tool, base}}$（严禁越权或错参）；
   - Token 消耗增量：$\Delta \text{Tokens} \le 0$（候选变异严禁正向膨胀）；
   - 执行耗时增量：$\Delta \text{Duration} \le 5\text{ms}$（允许微小网络抖动，禁止显著恶化）；
   - 显著改善：至少有一项指标表现出显著改善（例如 Token 节省 $\ge 20$ tokens、时延降低 $\ge 10\text{ms}$，或成功率提升）。
3. **双向匿名盲审（Position-Swap Dual Pass）消除位置偏见**：
   - 执行 Pass 1 与 Pass 2 双向盲审；
   - 必须达成双向一致（Quadrant 1: $W_1 = \text{Cand} \land W_2 = \text{Cand}$）方可采纳。
4. **测试套件与全量回归 100% 通过**：
   - 阶段四定向契约测试套件 `HermesHarnessStage4ContractTest` 绿灯；
   - 阶段一至阶段四联合定向测试 20+ 项全部通过；
   - 全量回归测试（连续 2 次 clean test）达成 1942 门测试 0 Failure / 0 Error。
5. **生产零污染与红线隔离**：
   - 生产模式保持 `SINGLE`，生产配置 `hermes.orchestration.enabled=false` 不可变；
   - 线上全局配置零热更，零动态修改。

---

## 二、5 个代表性离线基准任务集规范与输入沙箱

离线任务集严格定义于 `OfflineBenchmarkTestCase` 中，输入数据与评测细则物理隔离：

1. **`kb-qa-01` (知识库问答)**：
   - *问题*：“请根据企业规范，查询订单支付系统的超时重试次数与幂等键生成规则。”
   - *预期工具*：`knowledgeBase_MiddleWare_Spec`；
   - *输出断言*：包含重试 3 次、指数退避、SHA-256 幂等键；
2. **`tool-route-02` (多工具路由选择)**：
   - *问题*：“用户反馈账户扣款但未生成交易凭单，请查询最近流水并核对账户可用余额。”
   - *预期工具*：`banking_transaction_query`；
   - *输出断言*：双重查询意图识别，只读优先，严禁触发冲正写工具；
3. **`param-guard-03` (工具参数安全容错)**：
   - *问题*：“预订明天 25:99:99 的会议室。”
   - *预期工具*：`calendar_event_creator`；
   - *输出断言*：拦截非法时间格式，提示合法范围，严禁抛出未捕获 500 异常；
4. **`ctx-history-04` (长程会话上下文召回)**：
   - *问题*：“关于我们刚刚讨论的架构方案 A，针对你最担忧的网络时延瓶颈，有哪些具体的数据面优化方案？”
   - *预期工具*：无工具（纯上下文推理）；
   - *输出断言*：消除代词指代，准确召回 Sidecar 时延与 eBPF / 旁路加速方案；
5. **`comp-rag-tool-05` (复合知识与格式化生成)**：
   - *问题*：“检索微服务架构演进规范，并输出包含‘架构阶段、核心特征、运维复杂度、适用场景’的 Markdown 表格与 200 字总结。”
   - *预期工具*：`knowledgeBase_Arch_Spec`；
   - *输出断言*：结构化 4 列表格与无偏见客观总结。

---

## 三、架构组件与物理落盘标准

### 3.1 物理落盘拓扑与 JSON 契约
- 基线路径：`experiment/outputs/harness4/<runId>/baseline/evidence_<taskId>.json`
- 候选路径：`experiment/outputs/harness4/<runId>/candidate/evidence_<taskId>.json`
- 序列化数据结构：Java 21 `HarnessEvidenceRecord` 不可变 Record，包含 14 项完整物理字段：
  `evidenceId`, `requestId`, `botId`, `voucherFingerprint`, `voucherVersion`, `startTime`, `userInputSummary`, `ragDocumentCount`, `ragTopScore`, `toolExecutions`, `estimatedTokens`, `estimatedCostCny`, `durationMs`, `finalOutputSummary`。
- 写入安全：先写入临时文件，刷盘后执行 `StandardCopyOption.ATOMIC_MOVE` 原子重命名。

### 3.2 凭单租约管理与时序安全性 (`ScopedVoucherLease`)
- 实现 `AutoCloseable` 接口；
- 构造时向 `AgentOrchestrator` 注册局部覆写凭单；
- 在响应式流的 `.doFinally(...)` 中确保单次幂等卸载，彻底杜绝提前注销竞争与悬挂内存泄漏。

### 3.3 双轨制物理分流 (`HarnessExecutionDriver`)
- **真实轨道 (`ProductionAgentOrchestratorDriver`)**：调度真实 `AgentOrchestrator` 发起真实模型网络调用，生成落盘凭单；
- **确定性轨道 (`DeterministicHarnessTestDouble`)**：基于纯内存预设响应生成确定性凭据，用于日常 CI 快速回归，两者代码路径严格解耦。

---

## 四、防数据泄漏、预算熔断与盲审状态机

### 4.1 扩充版防数据泄漏门禁
- 扫描 24 类中英文敏感判词标记（含 `gold_rubric`、`ground_truth_answer`、`黄金标准`、`标准答案`、`采纳判决` 等）；
- 拦截 10 类敏感路径（含 `/experiment/outputs/`、`/qrels`、`/holdout/` 等）；
- 命中即瞬时抛出 `IllegalStateException("CRITICAL_DATA_LEAKAGE")` 熔断。

### 4.2 严格成本与超时熔断器 (`OfflineBudgetCircuitBreaker`)
- 单任务硬超时：$\le 30,000\text{ms}$；
- 单次 Token 上限：$\le 4,000\text{ tokens}$；单次成本上限：$\le 0.005\text{ CNY}$；
- 批次累计 Token 上限：$\le 80,000\text{ tokens}$；
- 批次累计成本上限：$\le 0.15\text{ CNY}$；
- 批次总超时上限：$\le 120,000\text{ms}$ (2 分钟)。

### 4.3 Position-Swap 双向匿名盲审器 (`PositionSwapBlindEvaluator`)
- 基于 SHA-256 高熵安全盐值计算置换种子 $\theta = \text{SeedByte} \pmod 2$；
- 执行 Pass 1 与 Pass 2 双向独立对称评测；
- 4 象限裁决判定：
  - Quadrant 1 ($W_1 = \text{Cand} \land W_2 = \text{Cand}$): **一致胜出，予以采纳**；
  - Quadrant 2 ($W_1 = \text{Base} \land W_2 = \text{Base}$): **一致淘汰，保持基线**；
  - Quadrant 3/4 ($W_1 \ne W_2$): **位置冲突，判定无效平局，安全回退基线**；
- 物理差分短路：若物理指标未通过门禁，直接短路跳过盲审大模型调用，节约 50% 成本。

---

## 五、最小实施代码文件清单与工程边界

阶段四工程实施严格限定在以下最小文件集合（严禁扩大修改范围）：

1. **新建契约与核心执行器**（位于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/`）：
   - `OfflineProbeExecutor.java`：执行器契约与不可变 Record（`ProbeExecutionOptions`, `ProbeExecutionReport`）；
   - `DefaultOfflineProbeExecutor.java`：双重看门狗、流式聚合与物理原子落盘标准实现；
   - `ScopedVoucherLease.java`：凭单租约管理与生命周期自动卸载；
   - `HarnessExecutionDriver.java` 与 `DeterministicHarnessTestDouble.java`：双轨抽象与确定性测试替身；
2. **新建风控与盲审器**（位于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/`）：
   - `PositionSwapBlindEvaluator.java`：实现 Position-Swap 消除偏见与短路熔断判定；
   - `OfflineBudgetCircuitBreaker.java`：实时原子累加熔断器；
3. **新建验收契约测试与真实探针运行入口**：
   - `HermesHarnessStage4ContractTest.java`：基于测试替身的 100% 确定性单测套件；
   - `OfflineProbeRunner.java`：真实离线探针运行入口（用于驱动真实模型执行并完成物理 JSON 落盘与回放）。

---

## 六、可复现构建与验证命令

在 SDKMAN Java 21 环境下执行：

```bash
# 1. 编译前置依赖
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean compile -f backend/pom.xml -pl qknow-hermes/qknow-hermes-proto -am

# 2. 执行阶段四定向契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=HermesHarnessContractTest,HermesHarnessStage2RuntimeTest,HermesHarnessStage3OfflineLoopTest,HermesHarnessStage4ContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# 3. 全量回归测试验证
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean test -f backend/pom.xml
```
