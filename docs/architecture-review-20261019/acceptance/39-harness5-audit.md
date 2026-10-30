# Hermes Harness² 阶段五架构只读审计与唯一待验证假设锁定报告

> **报告版本**：5.0.0-STAGE5-AUDIT  
> **审计分支基线**：`feat/hermes-harness-stage4`（Commit `d96f33af`）  
> **实施目标分支**：`feat/hermes-harness-stage5`  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **系统生成模型**：DeepSeek 官方活跃模型（`deepseek-flash`，https://api.deepseek.com 官方标准接口，解析 `reasoning_content`）  
> **系统向量模型**：阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0$，余弦度量）  
> **审计日期**：2026-10-30  

---

## 一、阶段四交付物审计与缺口识别

### 1.1 阶段四交付物与阶段 0 修复审计
在阶段四及阶段 0 修复中，系统构建了离线探针执行器契约骨架：
1. **证据凭单防篡改分级**：为 `HarnessEvidenceRecord` 引入了 `ExecutionMode` 枚举（`REAL_PRODUCTION`、`DETERMINISTIC_TEST_DOUBLE`、`FAIL_SAFE`），并将模式纳入 SHA-256 签名计算，从密码学上杜绝把测试替身产物混淆为真实生产证据；
2. **核心执行器与风控组件**：
   - `DefaultOfflineProbeExecutor`：支持双轨驱动、超时看门狗、原子落盘（`ATOMIC_MOVE`）与回读常量时间验签；
   - `ScopedVoucherLease`：基于 `AutoCloseable` 实现局部凭单生命周期管理，根除内存泄漏；
   - `PositionSwapBlindEvaluator`：SHA-256 盐值驱动的位置对调匿名双向盲审与物理指标硬短路拦截；
   - `OfflineBudgetCircuitBreaker`：批次累加熔断器（Token 80,000 / 成本 0.15 CNY / 耗时 120s）；
   - `SelectionDataLeakageTripwire`：24 个中英文敏感判词与 10 个敏感路径防泄漏拦截；
3. **关键缺口溯源（阶段五唯一攻坚核心）**：
   - 阶段四的端到端受控 PPC 测试仅由 `DeterministicHarnessTestDouble` 驱动，生成固定 `deltaTokens = -40` 的合成数据；
   - `ProductionAgentOrchestratorDriver` 仅具备骨架，未在真实 DeepSeek 模型与生产轨道上实际触发调用；
   - 核心 PPC 假设在真实模型环境下尚未得到验证。阶段五必须在真实生产轨道上运行 5 个用例的 Baseline 与 Candidate 探针（共 10 次真实调用），获取具备不可篡改密码学凭据的 `REAL_PRODUCTION` 证据。

---

## 二、真实环境前置条件法医级审计 (Pre-flight Audit)

根据《科研与系统工程总纲》铁律，实施阶段五真实探针前，必须对底层外部依赖进行实测核验，严禁主观臆断：

### 2.1 DeepSeek 官方 API 连通性与凭据审计
1. **凭据安全检测**：审计本地 `.env` 与 `backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml`，检测到有效的 `HERMES_OPENAI_API_KEY`（非 placeholder，长度 35 字符）；
2. **网络与接口探测**：本地执行安全探测脚本对 `https://api.deepseek.com/v1/models` 进行握手认证，HTTP 状态码返回 **`200 OK`**；
3. **官方模型可用性**：接口返回模型列表明确包含活跃主干模型 `deepseek-flash`；
4. **结论**：**前置网络与 API 依赖真实有效，具备执行真实探针的物理条件，无需触发 `RESEARCH_GATE_BLOCKED`**。

### 2.2 生产编排器驱动与调用链路审计
1. **执行驱动路径**：`OfflineProbeRunner` -> `DefaultOfflineProbeExecutor` -> `ProductionAgentOrchestratorDriver` -> `AgentOrchestrator.chat(request)` -> `ChatModelFactory.getChatModel("deepseek", baseUrl, apiKey, "deepseek-flash")` -> `DeepSeekCompatibleChatModel`；
2. **凭单隔离机制**：在发送 `ChatRequest` 前，`ScopedVoucherLease` 将指定 `requestId` 注册至 `AgentOrchestrator` 的 `voucherOverrideMap` 中；`AgentOrchestrator` 在执行时优先获取该局部凭单并由 `HarnessEvidenceCollector` 收集，完成后安全自动注销，全局生产默认 `hermes.orchestration.enabled=false` 保持完全不变；
3. **真实证据沉淀**：执行结束时，`evidenceCollector.finalizeEvidence()` 生成标有 `ExecutionMode.REAL_PRODUCTION` 的不可变凭单，由 `DefaultOfflineProbeExecutor` 原子重命名落盘至 `experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`。

### 2.3 真实探针预算与成本审计
- **基准测试规模**：官方冻结的 5 大代表性离线任务用例（`kb-qa-01`、`tool-route-02`、`param-guard-03`、`ctx-history-04`、`comp-rag-tool-05`）；
- **真实调用频次**：Baseline 5 次 + Candidate 5 次 = **10 次真实 DeepSeek 调用**；
- **资源与开销预估**：
  - 单次 prompt tokens 约 200~600，completion tokens 约 200~600；
  - 10 次调用总 tokens 预计约 5,000~10,000；
  - 预计消耗成本约 **0.01 ~ 0.02 元人民币**，耗时约 15~30 秒；
  - 远低于安全熔断器阈值（80,000 Tokens、0.15 CNY、120 秒），预算充足安全。

---

## 三、阶段五唯一待验证核心假设锁定

经过前置条件审计与链路追踪，锁定阶段五**唯一待验证核心假设**（具备精确可证伪性）：

> **“在 Hermes SINGLE Agent 架构的 5 个离线小样本基准任务集（涵盖知识库问答、多工具路由选择、工具非法参数纠错、上下文理解、复合推理）上，使用真实 `ProductionAgentOrchestratorDriver` 驱动底层真实 DeepSeek 官方 API (`deepseek-flash`)，对 Baseline 与 1 个精炼候选变异 Harness（`SYSTEM_PROMPT_CONCISE`）各执行一次真实生产轨道探针执行（共 10 次真实模型调用）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，标明 `executionMode: REAL_PRODUCTION`，分别落盘于 `experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据：若 10 次真实调用均成功完成且各凭单真实记录 DeepSeek 输出的 tokens 与耗时，且候选 Harness 在总 Token 消耗或平均耗时上有显著的真实物理节省且工具匹配状态不退化，则假设成立 (VERIFIED)；若真实调用因网络或接口异常失败触发 FAIL_SAFE，或候选相比基线无物理节省甚至发生退化，则客观如实判定假设不成立 (REFUTED)；严禁使用任何测试替身或固定差值伪造实验结果。”**

---

## 四、硬性系统边界与红线规则

1. **真实性绝对红线**：严禁在阶段五受控实验中使用 `DeterministicHarnessTestDouble`，落盘证据的 `executionMode` 必须为 `REAL_PRODUCTION`（或异常时的 `FAIL_SAFE`）；
2. **客观实验裁定**：实验结果完全由真实大模型执行产物决定，若候选变异未带来节省或双向盲审未采纳，必须如实记录并在报告中判定假设不成立，绝不篡改数据；
3. **零明文泄漏**：代码、日志、JSON 证据、Markdown 报告中绝对禁止打印或持久化任何 API 密钥明文；
4. **生产隔离与不可变性**：全局生产配置、全局 Prompt 与全局参数严禁在线修改，`hermes.orchestration.enabled` 严格保持 `false`；
5. **Java 21 隔离环境**：全量编译与运行严格限定于 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
