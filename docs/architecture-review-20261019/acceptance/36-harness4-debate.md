# Hermes Harness² 阶段四架构三方独立智能体交叉辩论与对齐裁决报告

> **报告版本**：4.0.0-STAGE4-DEBATE  
> **辩论组织方**：主 Agent（ConversationId: `e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`）  
> **参与辩论独立智能体**：  
> - **Agent A**：真实 PPC 实验设计审计员 (Real PPC Experiment Specialist，`023377fa-fb38-4c73-a881-a8d931aed78f`)  
> - **Agent B**：Java 离线执行器工程审计员 (Java Offline Executor Engineering Specialist，`e52fbf2f-0b80-4292-9540-7469bda69992`)  
> - **Agent C**：防泄漏、成本与停止条件审计员 (Zero-Leakage & Governance Specialist，`44f291c7-beda-4dec-baa7-fa9099468903`)  
> **审计基准分支**：`feat/hermes-harness-stage3`（Commit `ba46de4a`）  
> **实施目标分支**：`feat/hermes-harness-stage4`  
> **运行环境隔离**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **辩论日期**：2026-10-30  

---

## 一、辩论背景与唯一待验证核心假设锁定

在阶段三验收中，系统成功验证了离线 PPC 契约测试与模拟数据逻辑，但被指出核心缺口在于**“缺乏真实 Probe 执行，未真实调起大模型与工具执行链，证据与指标来自硬编码合成数据”**。阶段四的核心使命是实现从测试骨架到真实物理落盘的实质性跨越。

三方独立智能体一致确认并锁定了阶段四**唯一待验证核心假设**：

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 `AgentOrchestrator` 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，分别落盘于 `experiment/outputs/harness4/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化；若因网络/接口故障导致无法获取真实物理证据，或候选指标出现显著退化，则假设判定不成立。”**

---

## 二、两轮结构化交叉辩论实录

### 2.1 第一轮辩论：探针调度时序与凭单卸载生命周期

- **Agent B 提出论点与工程隐患**：
  在 `AgentOrchestrator.chat()` 中返回的是冷响应式流 `Flux<ChatEvent>`。若外部执行器在调用 `chat()` 后立即执行 `unregisterRequestVoucher(requestId)`，在异步工作线程实际消费并读取凭单时，该映射已被移除，导致探针静默回退至基线凭单，使得候选变异完全失效；此外，若未将卸载严格绑定于生命周期终结，还会发生悬挂内存泄漏。
- **Agent A 回应与对齐**：
  完全同意 Agent B 的法医排查结论。实验设计中必须保证候选凭单在整个探针执行期间（包括 RAG 检索、模型交互与工具调用）持续有效，但在请求流关闭后必须立即释放。支持 Agent B 提出的 `ScopedVoucherLease implements AutoCloseable` 方案，并通过 Reactor 的 `.doFinally(signal -> lease.close())` 挂钩。
- **Agent C 回应与风控补充**：
  支持 `ScopedVoucherLease`。从防泄漏与并发安全角度看，强制自动解绑能够彻底杜绝并发会话或连续任务之间的凭单串透污染。要求在 `ScopedVoucherLease.close()` 触发时，增加原子性断言与日志存证。
- **【主 Agent 裁决 1】**：
  **一致采纳 `ScopedVoucherLease` 模式。** 执行器必须将凭单注册封装为可自动关闭的作用域租约，并在响应式流终结时确保单次、幂等解绑，杜绝提前卸载竞争与内存泄漏。

---

### 2.2 第二轮辩论：双轨分流架构与物理落盘验证规范

- **Agent B 提出分流方案**：
  既要保证科研实证中真实调用 DeepSeek API 并物理落盘 JSON，又要保证本地 CI/CD 日常构建（`mvn test`）的 100% 确定性、无网络依赖与毫秒级执行。建议建立 `HarnessExecutionDriver` 抽象，物理拆分为两轨：
  1. `ProductionAgentOrchestratorDriver`：面向真实探针执行与物理落盘；
  2. `DeterministicHarnessTestDouble`：面向日常单元测试契约验证。
- **Agent A 回应与落盘要求**：
  完全认同双轨分流。但强调：科研验收报告中记录的 Baseline 与 Candidate 指标，必须来自于真实调用生成的物理 JSON 文件（`experiment/outputs/harness4/<runId>/...`），且落盘必须满足原子性写入（先写临时文件后 `ATOMIC_MOVE`）与 SHA-256 自校验。
- **Agent C 回应与短路风控补充**：
  同意双轨制，但要求对真实调用实施多重硬熔断：
  1. 批次累计 Token 上限 80,000，成本上限 0.15 元人民币，超时上限 120 秒；
  2. **物理差分短路拦截**：若提取物理指标发现 $\Delta \text{Tokens} > 0$ 或工具调用失败，直接短路熔断，绝不调用昂贵的盲审评测大模型，节约 50% 成本；
  3. 双向匿名盲审（Position-Swap Dual Pass）必须严格执行，消除位置偏见。
- **【主 Agent 裁决 2】**：
  **一致采纳双轨分流架构与短路熔断风控。**
  - 单元测试套件 `HermesHarnessStage4ContractTest` 采用确定性测试替身，确保 CI 100% 绿灯且确定；
  - 真实探针入口 `OfflineProbeRunner` 负责发起真实大模型网络交互，物理落盘 JSON 文件；
  - 引入 `OfflineBudgetCircuitBreaker` 实时熔断与差分短路机制。

---

## 三、三方智能体对齐共识矩阵

| 辩论议题 | Agent A (实验设计) | Agent B (工程实现) | Agent C (风控防泄漏) | 主 Agent 最终裁定 |
|---|---|---|---|---|
| **待验证核心假设** | 聚焦 5 个代表性离线任务真实 Probe 改善 | 聚焦执行器与大模型/工具真实链路调度 | 聚焦四项指标门禁与严格可证伪条件 | **三方 100% 达成唯一假设共识** |
| **凭单生命周期** | 要求全执行期间有效且隔离 | 提出 `ScopedVoucherLease` + `doFinally` | 要求防会话串透与内存泄漏 | **采纳 ScopedVoucherLease 模式** |
| **双轨隔离模型** | 强调真实物理落盘证据资产 | 提出 Driver 接口与双轨物理分流 | 强调单测绝不污染正式落盘目录 | **采纳双轨分流：单测替身 + 真实执行器** |
| **防数据泄漏** | 测试集与黄金标准物理文件隔离 | 探针沙箱工具目录白名单 | 扩充 24 个判词标记 + 10 敏感路径黑名单 | **采纳扩充版雷区扫描与沙箱三权分立** |
| **盲审与偏见消除** | 验证候选 System Prompt 精简收益 | 规范两次无状态独立调用接口 | 严格证明 Position-Swap 消除偏见上界 | **采纳确定性置换与对称双向盲审** |
| **预算与停止条件** | 任务级 30s 硬超时监控 | 双重看门狗（Flux 超时 + 线程池） | 批次 0.15 元 / 80k Tokens 累加熔断 | **采纳双重看门狗与实时累加熔断器** |

---

## 四、裁决结论与准入签字

主 Agent 正式裁定：
1. **辩论充分且结论收敛**：三方子智能体在理论设计、工程架构与安全风控维度达成完全一致，所有技术分歧均已闭环化解；
2. **决策完备性达成**：形成了包含接口契约、时序模型、物理落盘标准、双轨隔离机制与风控熔断在内的完整技术方案；
3. **准予进入任务书冻结**：立即将本辩论成果沉淀为《Hermes Harness² 阶段四真实探针离线 PPC 实施任务书 (FROZEN TASKBOOK)》，并进行 SHA-256 防篡改冻结签名。
