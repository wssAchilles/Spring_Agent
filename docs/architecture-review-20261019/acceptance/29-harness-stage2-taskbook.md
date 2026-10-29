# Hermes Harness² 阶段二架构实施任务书 (Stage 2 Taskbook)

> **任务书版本**：2.0.0-STAGE2-FROZEN  
> **任务书状态**：FROZEN（已冻结）  
> **冻结签名 (SHA-256)**：`f4e373fe9d4c9a94fcf0bcc02abb4c3e0b145f26137625363339657bcef9a8b6`  
> **基准分支**：`feat/hermes-harness-stage2`（从 `feat/hermes-harness-contract` 创建，Commit `770d7db9`）  
> **实施环境**：Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）、DeepSeek 官方 API、阿里千问 1024 维超球面向量空间、单 Agent 生产默认模式。  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

## A. 当前代码与失败机制

### 1. 现状与缺口分析
阶段一（Commit `770d7db9`）成功落地了 8 大不可变组件 Record 契约与 `HermesHarnessVoucher` 聚合模型，并在 `HermesHarnessAssembler` 中封死了 System Prompt 动态变量注入。然而，阶段一仍存在“有契约但未接通”的断点：
- **缺口 1：运行时参数未受契约驱动**：
  在 `AgentOrchestrator.java` 中，模型调用的记忆滑动窗口深度、工具调用引导策略与守卫阈值等，并未全面读取传入的 `HermesHarnessVoucher` 内部字段（如 `MemoryContextStrategyRecord.shortTermWindowSize`、`ToolGuidancePolicyRecord.enforcePreconditionChecks`），依然依赖类内默认静态常数；
- **缺口 2：缺乏全链路物理执行证据链 (Evidence Tracking Gap)**：
  目前 Hermes 仅能在流式传输分片透传 `harness_fingerprint`，缺乏类似 Harness² `ledger.py`、`context.py` 与 `runmark.py` 的可追溯物理执行留痕机制。单次调用的输入、RAG 检索召回分片得分、工具调用时序与入参出参、真实 Token 消耗与执行指纹无法形成结构化不可变凭单，无法满足审计合规与后续离线评测的需求。

### 2. 阶段二唯一待验证假设 (Unique Falsifiable Hypothesis)
> **在 Hermes 单 Agent (SINGLE 模式) 架构上，将阶段一建立的不可变 Harness 组件契约（`GuardrailsSpecificationRecord`、`MemoryContextStrategyRecord`、`ToolGuidancePolicyRecord`）真正接入并驱动运行时执行链路，并挂载轻量只读的 `HarnessEvidenceCollector`（证据采集器）：在保持默认生产行为与回答语义 100% 零回归的前提下，能够对单次请求产生确定性、包含完整时序/RAG/工具/成本物理证据的不可变凭单（`HarnessEvidenceRecord`），全量通过 Java 21 1942+ 项测试，为后续离线小样本下的受控 Harness 变异与探针评测提供唯一可复现的基石证据。**

---

## B. 研究结论与文献支撑

通过对 8 个来源（Harness² 论文、Harness² 官方代码、Reflexion、CLIN、DSPy 论文与代码、OPRO、LangGraph）的法医级审计，确立以下技术支撑：
1. **组件契约驱动运行时**：
   - 支撑来源：Harness² (`spec.py`) 与 DSPy (`signature.py`)；
   - 结论：组件参数必须是强类型不可变对象，并在执行流水线中统一传递，禁止业务代码硬编码离散配置。
2. **只读不可变证据链**：
   - 支撑来源：Harness² (`ledger.py`、`runmark.py`)、LangGraph (`checkpoint/base.py`)；
   - 结论：将单次调用的时序证据以只读不可变 Record 固化，利用常量时间算法校验指纹，保证证据链的可信度与不可篡改性。
3. **坚决拒绝在线篡改与任意代码执行**：
   - 支撑来源：Harness² 消融实验与 DeepSeek 官方缓存规约；
   - 结论：Harness² 论文证实微调 Prompt 仅增益 1.5 分，但在 DeepSeek 体系下会导致 1M Context Caching 命中率归零，成本增加 10 倍、首字延迟增加 3~5 倍；且在宿主执行未受控脚本违背企业零信任安全原则。

---

## C. 候选方案比较

| 比较维度 | 方案 1：保持现状 (Baseline) | 方案 2：完整照搬 Harness² | 方案 3：最小契约驱动 + 离线证据链 (推荐方案) | 方案 4：仅做证据采集，不接通参数 |
|---|---|---|---|---|
| **正确性** | 良好（但参数与契约解耦） | 极差（在线变异导致语义漂移） | **优秀（行为确定，完全可复现）** | 良好（但契约仍未发挥作用） |
| **可证伪性** | 弱（缺乏结构化物理证据） | 差（随机搜索噪声掩盖因果） | **极强（单变量控制与确定性证据）** | 中等（能采集证据但无法调控参数） |
| **数据需求** | 无 | 极高（需持续采样测试） | **极低（无额外标注需求）** | 极低 |
| **端到端延迟** | 基线（~200ms） | 严重恶化（+800ms~2000ms） | **完全受控（旁路纯内存增加 < 3ms）** | 完全受控（增加 < 3ms） |
| **Token 成本** | 基线（享受缓存优惠） | 暴涨 10 倍（击穿 KV 缓存） | **完全一致（100% 保持缓存命中）** | 完全一致 |
| **实现复杂度** | 零（无新增代码） | 极高（需构建完整探针沙盒） | **中低（仅新增 2 个核心类，修改 1 个类）** | 低 |
| **依赖变化** | 无 | 需引入大量 Python/Bash 外部依赖 | **零依赖引入（纯 Java 21 标准库）** | 零依赖引入 |
| **回滚风险** | 零 | 极高（引入在线不确定性） | **极低（可一键禁用采集器恢复默认）** | 极低 |
| **生产影响** | 无影响 | 严重破坏生产高可用与稳定性 | **零侵入（生产默认行为语义 100% 保持）** | 零侵入 |

- **裁决结论**：**方案 3** 具备压倒性优势，是当前阶段唯一推荐的最小算法工程路径。

---

## D. 推荐的最小算法与工程方案

### 1. 契约参数全面驱动运行时
- 在 `AgentOrchestrator` 执行时，由 `HermesHarnessVoucher` 提取参数驱动核心流程：
  - `memoryStrategy.shortTermWindowSize()`：控制上下文注入的短期历史轮次截断；
  - `toolPolicy.enforcePreconditionChecks()`：在触发工具调用前进行形式化前置检查；
  - `guardrails.maxModelCalls()` 与 `maxCostUsd()`：作为执行流的硬性熔断上限；
  - 若调用方未显式传入 Voucher，自动挂载系统默认不可变 `HermesHarnessVoucher`，保证向下兼容。

### 2. 离线全链路只读证据链 (`HarnessEvidenceCollector` & `HarnessEvidenceRecord`)
- **不可变凭单模型 (`HarnessEvidenceRecord`)**：
  采用纯 Java 21 Record 声明，包含字段：
  - `evidenceId` (UUID 字符串)；
  - `requestId` 与 `botId`；
  - `voucherFingerprint` (SHA-256 哈希) 与 `voucherVersion`；
  - `timestamp` (创建时间戳)；
  - `userInput` (原始用户输入摘要)；
  - `ragDocumentCount` 与 `ragTopScore` (RAG 检索召回状态)；
  - `toolExecutions` (不可变 List，记录工具名、耗时、状态、入参哈希)；
  - `estimatedTokens` 与 `estimatedCostUsd`；
  - `executionDurationMs` (全链路物理执行耗时)；
  - `finalOutputSummary` (最终产出摘要)；
  - `computeDigest()` (使用 SHA-256 计算整条证据的防篡改指纹)。
- **只读旁路采集器 (`HarnessEvidenceCollector`)**：
  - 线程安全、纯内存收集，无文件 I/O 阻塞；
  - 通过 `startCapture(requestId, voucher)` 开启，在执行各关键阶段 `recordRag()`、`recordToolExecution()`、`recordTokens()`，执行完毕调用 `finalizeEvidence()` 输出不可变 `HarnessEvidenceRecord`；
  - 支持通过监听器或上下文查询，对主流程响应式流零阻塞。

### 3. 为什么坚决不碰 MCP / SWARM / HITL
- 坚持单变量实验原则：阶段二聚焦单智能体内部的“契约参数驱动”与“证据留痕”，不引入跨进程通信、多智能体协议与人工审批交互的不确定性。

---

## E. 最小修改文件集合与禁止修改边界

### 1. 允许新增文件清单（位于 `backend/qknow-hermes/qknow-hermes-core`）
- `src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceRecord.java`
- `src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceCollector.java`
- `src/test/java/tech/qiantong/qknow/hermes/agent/harness/HermesHarnessStage2RuntimeTest.java`

### 2. 允许修改文件清单
- `src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`

### 3. 绝对禁止修改的边界（Hard Boundaries）
- 严禁修改 `main` 分支；
- 严禁修改 `backend/qknow-mcp/` 任何代码；
- 严禁修改 `backend/qknow-hermes/` 下 SWARM、DEBATE、HITL 审批流核心；
- 严禁修改任何外部第三方依赖（POM 文件零改动）；
- 严禁修改前端代码与数据库表结构。

---

## F. 实验契约与反事实设计

### 1. 实验组与对照组设计
- **Baseline（基准组）**：阶段一系统，使用默认静态常量，未挂载 `HarnessEvidenceCollector`；
- **Candidate（实验组）**：阶段二系统，由 `HermesHarnessVoucher` 动态驱动窗口大小与守卫，挂载 `HarnessEvidenceCollector` 收集证据凭单；
- **Counterfactual 1（反事实消融 1）**：动态调小 `shortTermWindowSize`（如由 20 改为 2），验证上下文历史消息严格按契约截断；
- **Counterfactual 2（反事实消融 2）**：篡改证据凭单内容，验证 `computeDigest()` 严格发生差分漂移，证明不可篡改性。

### 2. 指标定义与通过判据 (Success Criteria)
- **延迟预算**：挂载证据采集器导致的额外 CPU/内存开销在 P99 下 $\le 3\text{ms}$；
- **行为一致性**：在默认生产配置下，回答生成语义与 Token 消耗 100% 保持一致，零业务回归；
- **测试通过率**：全量测试套件连续 2 次全绿（`qknow-tests` 1942+ 项测试 0 失败、0 错误、8 跳过）。

### 3. 复现与验证命令
```bash
# 环境变量
export JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem

# 1. 阶段二新增定向测试
mvn clean test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -Dtest=HermesHarnessStage2RuntimeTest -Dsurefire.failIfNoSpecifiedTests=false

# 2. 阶段一与阶段二 Harness 联合测试
mvn clean test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -Dtest="HermesHarnessContractTest,HermesHarnessStage2RuntimeTest" -Dsurefire.failIfNoSpecifiedTests=false

# 3. 全量测试回归门禁 (连续 2 次)
mvn clean test -f backend/pom.xml -pl tests -am
```

---

## G. 风险、停止条件与后续边界

### 1. 残余风险与防护对策
- **风险**：高频大并发场景下内存证据对象累积导致 GC 压力；
- **对策**：`HarnessEvidenceCollector` 仅保存轻量级摘要与哈希，大文本使用不可变截断或哈希摘要，并提供及时的生命周期回调清理。

### 2. 立即停止条件 (Immediate Abort Conditions)
- 若 `mvn clean test` 出现既有单测失败；
- 若出现 System Prompt 动态注入或破坏 DeepSeek Context Caching 的改动；
- 若出现任何未受控脚本执行或外部模型依赖引入。
触发任一条件，立即回滚代码至 Commit `770d7db9` 并输出 `RESEARCH_GATE_BLOCKED`。

### 3. 阶段三独立授权边界
- 阶段二绝不私自推进在线变异、经验库自动注入或 SWARM 多智能体演化，相关工作必须在阶段二验收完毕后获得用户显式独立授权方可规划。
