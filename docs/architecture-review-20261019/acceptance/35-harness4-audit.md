# Hermes Harness² 阶段四架构只读审计与唯一待验证假设锁定报告

> **报告版本**：4.0.0-STAGE4-AUDIT  
> **审计分支基线**：`feat/hermes-harness-stage3`（Commit `ba46de4a`）  
> **实施目标分支**：`feat/hermes-harness-stage4`  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **系统生成模型**：DeepSeek 官方活跃模型接口（官方开放标准规范，严格解析 `reasoning_content`，参数遵循官方建议）  
> **系统向量模型**：阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1$，余弦度量）  
> **审计日期**：2026-10-30  

---

## 一、阶段二与阶段三交付物审计总结

### 1.1 阶段二交付物审计
在阶段二（Commit `26c913e5`）中，系统成功建立了不可变物理证据采集机制：
1. **轻量证据凭单**：`HarnessEvidenceRecord`（Java 21 Record 不可变对象），记录单次请求的 `evidenceId`、`requestId`、`botId`、`voucherFingerprint`、`voucherVersion`、`userInputSummary`、`ragDocumentCount`、`ragTopScore`、`toolExecutions`（含工具名、耗时、出入参 SHA-256 摘要）、`estimatedTokens`、`estimatedCostCny`、`durationMs`、`finalOutputSummary`；
2. **轻量收集器**：`HarnessEvidenceCollector`，采用纯内存同步方式记录执行轨迹，零阻塞流式通道，终结时生成不可变凭单并通知观察者监听列表；
3. **主编排器接入**：`AgentOrchestrator` 内部实现 `registerRequestVoucher` 与 `unregisterRequestVoucher`，通过不可变并发哈希表实现单次请求级凭单覆盖，确保离线探测在不污染全局生产配置的前提下执行；
4. **定向测试覆盖**：`HermesHarnessContractTest`（6 项）与 `HermesHarnessStage2RuntimeTest`（4 项）全绿。

### 1.2 阶段三交付物审计与缺口识别
在阶段三（Commit `ba46de4a`）中，系统构建了离线 Propose–Probe–Compose（PPC）闭环的契约测试骨架：
1. **离线候选提议器**：`OfflineHarnessProposer`，支持 `SYSTEM_PROMPT_CONCISE`、`TOOL_GUIDANCE_ENHANCED`、`MEMORY_WINDOW_OPTIMIZED` 等变异算子，内置静态前缀保护与 Hoare 契约逻辑；
2. **差分提取器**：`EvidenceDiffExtractor`，针对两份 `HarnessEvidenceRecord` 提取 Token 增量、耗时增量与工具匹配指标；
3. **离线凭单回放器**：`OfflineEvidenceReplayer`，基于 `MessageDigest.isEqual()` 常量时间验签实现确定性回放；
4. **核心缺口（必须在阶段四解决）**：
   - **缺乏真实 Probe 执行器**：阶段三测试套件 `HermesHarnessStage3OfflineLoopTest` 中的基线与候选证据来自于硬编码的 `createSampleEvidence` 合成数据；
   - **未真实调起大模型与工具执行链**：没有在真实离线任务上驱动 `AgentOrchestrator`，未产出真实的物理调用与开销数据；
   - **证据未持久化到物理介质**：仅在内存中暂存，没有落盘为可追溯、可审计的真实 JSON 文件资产。

---

## 二、Google Research Harness² 核心架构思想对齐审计

基于对本地源码仓库 `/Users/achilles/Documents/优秀开源项目/harness2`（Commit `1cfe254e`）的法医级审计，识别出以下可直接借鉴的核心思想与本项目 Java 架构的对齐方案：

| Harness² 机制 (Python) | 源码对应文件 | 核心机制与原理 | Hermes 阶段四落地方案 (Java 21) |
|---|---|---|---|
| **递归探索闭环 (Recursive PPC Loop)** | `core/recursion.py` | 以阶段为单位组织候选探索，重试机制、错误隔离与失败继承基线 | 在 `OfflineProbeExecutor` 中实现两阶段真实探测，候选执行异常自动安全继承 Baseline |
| **真实探索者 (Proposer Session)** | `core/proposer.py` | 独立子进程与隔离工作空间，捕获输出、耗时与模型响应 | 基于 `AgentOrchestrator` 的 `registerRequestVoucher` 隔离执行，真实触发模型/工具调用并落盘物理 JSON |
| **匿名双向盲审选择 (Position-Swap Selection)** | `core/selection.py` | 对候选与基线进行位置对调两次评估，消除大模型固有位置偏见 | 实现 `PositionSwapBlindEvaluator`，仅当两次匿名对调均判定候选更优时才采纳变异 |
| **证据账本 (Evidence Ledger)** | `core/ledger.py` | 结构化记录每一次探索的输入、变异、指标与决策原因 | 扩展 `HarnessEvidenceRecord` 物理序列化，存储于 `experiment/outputs/harness4/<runId>/` 规范目录 |

---

## 三、阶段四唯一待验证核心假设锁定

经过只读代码审计与缺陷溯源，锁定阶段四**唯一待验证核心假设**（具备精确可证伪性）：

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 `AgentOrchestrator` 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，分别落盘于 `experiment/outputs/harness4/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化；若因网络/接口故障导致无法获取真实物理证据，或候选指标出现显著退化，则假设判定不成立。”**

---

## 四、硬性系统边界与红线规则

1. **生产默认绝对隔离**：`hermes.orchestration.enabled=false` 严格保持不可变默认，默认运行模式锁定为 `SINGLE`；
2. **禁止生产配置在线篡改**：离线探测仅通过内存级局部 `requestId` 临时凭单覆盖实现，严禁在线动态热更系统配置、全局 Prompt 或全局工具策略；
3. **真实调用与测试替身严格物理分流**：
   - **真实离线执行器 (`OfflineProbeExecutor`)**：负责驱动 `AgentOrchestrator` 产生真实大模型交互与物理 JSON 落盘；
   - **确定性单元测试替身 (`DeterministicHarnessTestDouble`)**：用于本地 CI 与回归测试套件，绝不冒充真实在线大模型结果，两者代码与调用路径物理隔离；
4. **严防数据泄漏**：离线任务的评估指标与 Holdout 验证集绝对禁止传入探索或生成 Prompt；
5. **Java 21 隔离环境**：所有构建与测试严格使用 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，严禁污染宿主 Java 17。
