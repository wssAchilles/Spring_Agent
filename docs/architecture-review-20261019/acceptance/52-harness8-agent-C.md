# Hermes Harness² 阶段八（Stage 8-lite）：Agent C（PPC 使能审计员）独立审计报告

- **文档编号**：`52-harness8-agent-C.md`
- **子智能体角色**：`PPC Readiness & Minimal Record Auditor`（Agent C：PPC 使能审计员）
- **物理 Conversation ID**：[`670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d`](conversation://670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d)
- **物理 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d/.system_generated/logs/transcript.jsonl)
- **审计分支与提交基线**：`feat/hermes-harness-stage7` (`HEAD: 362074a0`)
- **对照开源基线**：Google Research `harness2` (`/Users/achilles/Documents/优秀开源项目/harness2`, `commit: 1cfe254e8d25b15f37048c3730134686abe60335`)

---

## 一、RAG 归因全链路打通方案（三处硬编码 `0` 断链法医剖析与最小修复设计）

### 1.1 现有源码三处硬编码 `0` 断链点精确定位

通过对 `AgentOrchestrator -> HarnessKernelBridge -> HermesHarnessKernel -> TrajectoryAttributionAnalyzer` 调用链的逐行源码核验，确认当前 RAG 归因存在以下三处物理断链：

1. **断链点一：`AgentOrchestrator.java` 终结回调硬编码 `0.0D` 且丢失 RAG 统计状态**
   - **代码位置**：[`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 `323-327`（位于 `chat()` 的 `.doFinally(...)` 中）：
     ```java
     HarnessKernelBridge.BridgeExecutionHandle handle = bridgeHandle.get();
     if (handle != null) {
         harnessKernelBridge.completeExecution(request.getRequestId(), handle, 0.0D);
     }
     ```
   - **成因剖析**：在 `executeAgent()` 内部（行 `445-477`、`607-618` 与 `1334-1390`），系统明明已经持有 `effectiveRagContexts`、通过 `buildRagContextBlock()` 过滤出了非空白 `<document>` 块，并在行 `613-617` 计算了 `ragTopScore` 写入 `evidenceCollector.recordRagEvidence(effectiveRagContexts.size(), ragTopScore)`。但首先，`effectiveRagContexts.size()` 未剔除空白或被 CRAG `isIncorrect()` 阻断的无效上下文；其次，当流式推理结束触发 `doFinally` 时，`chat()` 外层作用域既未保存实际注入文档数 `ragProvided`，也未基于 `fullAnswer` 计算实际被使用的文档数 `ragUsed`，而是直接向 `completeExecution` 硬编码传入了 `0.0D`。

2. **断链点二：`HarnessKernelBridge.java` 彻底丢弃 `ragScore` 且缺少归因入参通道**
   - **代码位置**：[`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 行 `154-168`：
     ```java
     public void completeExecution(String requestId, BridgeExecutionHandle handle, double ragScore) {
         ...
         if (handle.session() != null && handle.preparedSuccess()) {
             evaluation = Optional.of(handle.session().evaluateTrajectory(List.of()));
         }
     ```
   - **成因剖析**：`completeExecution` 方法签名虽然声明了 `double ragScore`，但在方法体内完全未使用该参数，且未向 `ExecutionSession.evaluateTrajectory(...)` 传递 `ragProvided`、`ragUsed`、`ragScore`、`reasoningTokens` 与 `answerTokens`。

3. **断链点三：`HermesHarnessKernel.java` 硬编码全 `0` 构造 `TrajectoryEvidence`，引发语义混淆**
   - **代码位置**：[`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 行 `168-171`：
     ```java
     public synchronized TrajectoryEvaluationResult evaluateTrajectory(List<ParetoFrontierComposer.Candidate> candidates) {
         var attribution = attributionAnalyzer.analyze(
                 new TrajectoryAttributionAnalyzer.TrajectoryEvidence(0, 0, 0, 0, toolExecutions));
     ```
   - **成因剖析**：`TrajectoryEvidence(reasoningTokens, answerTokens, ragProvided, ragUsed, toolExecutions)` 的前四个整型计数器被硬编码为 `0, 0, 0, 0`。传导至 [`TrajectoryAttributionAnalyzer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/TrajectoryAttributionAnalyzer.java) 行 `34-35`：
     ```java
     double ragUnderutilization = evidence.ragProvided() == 0 ? 0.0
             : 1.0 - (double) evidence.ragUsed() / evidence.ragProvided();
     ```
     导致 `ragUnderutilization` 恒等于 `0.0`。这造成了一个严重的法医级语义漏洞：**“请求本身未提供任何 RAG 上下文（`ragProvided == 0`）”** 与 **“提供了 RAG 上下文且被 100% 充分利用（`ragProvided > 0 && ragUsed == ragProvided`）”** 输出了完全相同的数值 `0.0`，用全 `0` 冒充了“已检索且零浪费”。

---

### 1.2 真实 `ragProvided`、`ragUsed`、`ragScore` 与 `RagAttributionStatus` 的最小计算契约

为在不引入额外 LLM 裁判调用、不增加网络延迟的前提下打通 RAG 归因，设计以下纯内存确定性计算机制：

#### （1）显式枚举 `RagAttributionStatus`（消除全 `0` 伪装）
在 `TrajectoryAttributionAnalyzer`（或公共凭单模型）中定义强类型状态枚举：
```java
public enum RagAttributionStatus {
    /** 未绑定知识库、检索结果为空或被 CRAG INCORRECT 阻断（ragProvided == 0） */
    NO_RAG,
    /** 所有提供的有效 RAG 文档均在最终回答或知识库工具调用中被命中（ragProvided > 0 && ragUsed == ragProvided） */
    FULLY_USED,
    /** 部分提供的有效 RAG 文档被命中（ragProvided > 0 && 0 < ragUsed < ragProvided） */
    PARTIALLY_USED,
    /** 提供了有效 RAG 文档，但最终回答与工具调用均未命中任何文档（ragProvided > 0 && ragUsed == 0） */
    UNUSED;

    public static RagAttributionStatus resolve(int ragProvided, int ragUsed) {
        if (ragProvided <= 0) {
            return NO_RAG;
        }
        if (ragUsed <= 0) {
            return UNUSED;
        }
        return ragUsed >= ragProvided ? FULLY_USED : PARTIALLY_USED;
    }
}
```
> **硬门禁约束**：当 `ragProvided == 0` 时，状态必须显式标记为 `RagAttributionStatus.NO_RAG`，绝不允许在下游 PPC 差分或归因分析中将其与 `FULLY_USED` 混为一谈。

#### （2）三项核心指标的确定性计算法则
1. **`ragProvided`（实际注入的非空文档/片段数）**：
   - 若 `retrievalEvaluation != null && retrievalEvaluation.isIncorrect()`（对照 `AgentOrchestrator.buildRagContextBlock` 行 `1335`），此时系统拒绝注入知识库内容，故 `ragProvided = 0`。
   - 否则，统计 `effectiveRagContexts` 中满足 `ctx.getPreRetrievedContent() != null && !ctx.getPreRetrievedContent().isBlank() && !"null".equalsIgnoreCase(ctx.getPreRetrievedContent().trim())` 的有效文档条目数。
2. **`ragUsed`（最终输出中实际引用或命中关键词/片段的文档数）**：
   - 当 `ragProvided == 0` 或最终输出 `finalAnswer` 为空白/错误兜底（以 `"FAIL_SAFE"` 或 `"ERROR:"` 开头）时，`ragUsed = 0`。
   - 当 `ragProvided > 0` 且存在正常输出 `finalAnswer` 时，对每个有效注入的 `RAGContext` 文档 $d_i$，若满足以下任一确定性准则，则判定该文档被有效使用（计数 $+1$）：
     - **准则 A（显式源引用命中）**：`finalAnswer` 包含 `d_i.getKnowledgeName()`（非空）或 `d_i.getKnowledgeId()`（非空）；
     - **准则 B（知识库工具调用命中）**：`toolExecutions` 中存在 `toolName.equals("knowledgeBase" + d_i.getKnowledgeId())` 或 `toolName.equals("knowledgeQuery")` 且 `success == true` 的执行记录；
     - **准则 C（核心词汇/片段重叠命中）**：从 `d_i.getPreRetrievedContent()` 中提取长度 $\ge 2$ 的连续中文词元/字母数字词元（或 4-gram 关键片段），若 `finalAnswer` 中命中的非停用词元数量 $\ge 2$（或重叠比例 $\ge 15\%$），则判定内容被实质采纳。
   - 最终 `ragUsed = Math.min(ragProvided, matchedDocCount)`，严格满足 $0 \le \text{ragUsed} \le \text{ragProvided}$。
3. **`ragScore`（最高或平均超球面余弦相似度）**：
   - 当 `ragProvided == 0` 时，强制 `ragScore = 0.0D`。
   - 当 `ragProvided > 0` 时：优先读取 `retrievalEvaluation != null ? retrievalEvaluation.getConfidence() : 0.0D`；若请求 `inputParams` / `parameters` 或上下文元数据中携带千问 `text-embedding-v4`（1024 维 $L_2$ 归一化超球面 $\cos\theta = \mathbf{u}\cdot\mathbf{v} \in [0, 1]$）的检索得分，则取有效注入文档的最高余弦相似度，并裁剪至 $[0.0, 1.0]$ 区间。

---

## 二、Stage 9 PPC 所需最小执行记录审计（对照 Harness² `Ledger` / `Runmark`）

### 2.1 对照本地 Harness² `ledger.py` 与 `runmark.py` 的差距评估

通过精读本地 `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/ledger.py`（行 `1-133`）与 `runmark.py`（行 `1-116`），提取其支撑 PPC 闭环的核心机制：
1. **`runmark.py`（完成性与配置指纹契约）**：
   - 通过 `.adapt_complete` 原子写盘（`tmp` + `os.replace`，行 `77-80`）记录 `{"version": 1, "run_id": ..., "at": ..., "harness": fingerprint(harness_dir), **extra}`。
   - 核心原则：**“存在输出文件不代表运行完成；且不同 harness 指纹产生的产物绝不可混用”**（`why_not()` 行 `105-115`）。
2. **`ledger.py`（预算恒等式与防覆盖契约）**：
   - 记录每个 `Entry(kind, label, usd, cached, attempts)`，通过 `assert_identity()`（行 `89-113`）强制断言 $R=4k, P=3k$ 且无重复 label。

对照本项目现有 `HarnessEvidenceRecord.java`（行 `18-34`，共 15 个字段）与离线 PPC 链路，现有记录缺少：
1. `usageStatus`（标识计量或执行状态）；
2. `capabilityTag`（实际生效的能力域标签）；
3. `guardBlocked` 与 `guardBlockedCount`（区分工具自身异常与守卫主动拦截）；
4. `ragAttribution`（含 `RagAttributionStatus`、`ragProvided`、`ragUsed`、`ragScore`、`ragUnderutilization`）。

---

## 三、范围收敛审计（Stage 8 必须做 vs 必须推迟的生产加固项）

| 序号 | 必须推迟/拒绝的事项 | 为什么在 Stage 8 必须坚决不做 | 为什么不做绝不会阻塞 Stage 9 真实 PPC |
| :--- | :--- | :--- | :--- |
| **1** | **完整生产认证**（OAuth2 / JWT / 多租户 RBAC 鉴权打通） | 违反 `AGENTS.md` 最高战略定位（本项目已 100% 转向顶刊论文方法论与核心框架研发，不再服务于传统企业业务交付）。认证属于外围网关职责，与 Agent 认知编排及神经符号内核正交。 | Stage 9 PPC 通过 `DefaultOfflineProbeExecutor`（行 `70-85`）在进程内直接构建确定性沙盒身份（`workspaceId=9999L, userId=8888L`）调用 `AgentOrchestrator.chat()`，完全绕过 HTTP 认证网关。 |
| **2** | **无限嵌套 JSON Schema 解析器**（全量 JSON Schema Draft-2020-12 引擎） | `HarnessKernelBridge.contractFor()`（行 `257-316`）已实现一阶扁平字段类型与 `required`、`additionalProperties` 的确定性校验。引入完整递归 Schema 解析器会显著增加复杂度与依赖面。 | 本项目所有挂载工具及 PPC 基准测试套件的入参均为一阶扁平 JSON 对象，现有契约推导 100% 覆盖 Stage 9 探针需求。 |
| **3** | **企业级 SLA / 运维体系**（Prometheus 告警、分布式追踪治理、高可用容灾） | `AGENTS.md` 明确宣告“不再服务于企业商业交付或日常运维”。当前研发算力必须 100% 聚焦于可映射为顶刊论文伪代码的核心架构与形式化机制。 | Stage 9 PPC 的可观测性完全建立在 `HarnessEvidenceRecord` 的本地原子 JSON 落盘与 `OfflineEvidenceReplayer` 的 SHA-256 确定性回放之上，对外部运维监控零依赖。 |
| **4** | **付费 Live E2E 全量测试**（在 Stage 8 消耗真实 Token 跑在线大模型套件） | `AGENTS.md` 第一章与第四章明确规定“暂缓消融基线纠缠，不在几分钱 Token 预算熔断或琐碎基线对比实验上消耗核心研发算力”。Stage 8 是底座使能阶段，不是实验打榜阶段。 | Stage 8 只需通过确定性替身 100% 验证归因数学逻辑与证据记录字段完整性；真实 API 探针留待 Stage 9 统一受控执行。 |
| **5** | **MCP 动态外部插件与 HITL（人工审批流）** | 在 `OfflineHarnessProposer.MutationSafetyValidator` 中，`McpMiddlewarePlugins` 已被硬编码锁定为不可变冻结组件；而 HITL 需要人工交互阻塞，与自动化批处理背道而驰。 | Stage 9 PPC 要求全自动、非交互式地连续执行 Baseline 与 Candidate 探针并由 `DualBlindSelectionEvaluator` 自动双盲裁决，引入 HITL 反而会阻塞自动化闭环。 |
| **6** | **在线自修改（Online Self-Modification / 生产运行态热覆写凭单）** | 在线实时自修改会导致不可控的策略漂移、破坏 DeepSeek 静态前缀缓存命中率，并可能绕过 `MutationSafetyValidator` 的安全边界。 | 对照 Harness² `runmark.py` 与本项目 `ScopedVoucherLease`，Stage 9 PPC 采用“请求级租约挂载候选凭单 + 离线双盲胜出后版本晋升”的不可变演进模式，根本不需要在线自修改。 |
| **7** | **跨语言重写**（将 Python `harness2` 桥接或重写现有评测套件） | 本项目已在 Java 21 主干与测试域中具备完整的 `HermesHarnessKernel` 与离线评测器，跨语言重写违反最小算法选择原则。 | 仅需在现有 Java 21 类上打通 RAG 归因断链并扩充最小证据字段，即可在纯 Java 21 隔离环境中无缝运行 Stage 9 PPC。 |

---

## 四、Research Ledger（14 字段规范）

1. **`RL-STAGE8-01`（`official-code`）**：`google-research/harness2` (`commit: 1cfe254e8d25b15f37048c3730134686abe60335`, Apache-2.0, 已读 `harness2/core/ledger.py` L1-133, `harness2/core/runmark.py` L1-116, `VERIFIED`)。
2. **`RL-STAGE8-02`（`production-implementation`）**：`Spring_Agent` 主干 (`commit: 362074a0`, 已读 `AgentOrchestrator.java` L1-1659, `HarnessKernelBridge.java` L1-338, `HermesHarnessKernel.java` L1-198, `TrajectoryAttributionAnalyzer.java` L1-39, `HarnessEvidenceRecord.java` L1-160, `HarnessEvidenceCollector.java` L1-171, `VERIFIED`)。
3. **`RL-STAGE8-03`（`production-implementation`）**：`Spring_Agent` 离线 PPC 组件 (`commit: 362074a0`, 已读 `DefaultOfflineProbeExecutor.java` L1-257, `EvidenceDiffExtractor.java` L1-57, `ProbeExecutionDiff.java` L1-45, `DualBlindSelectionEvaluator.java` L1-165, `VERIFIED`)。
