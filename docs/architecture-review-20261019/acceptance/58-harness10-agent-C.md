> **SUPERSEDED / DO NOT USE FOR IMPLEMENTATION**：本文 Stage 9 HISTORY/COMPOSITE 的 `INVALID_FIXTURE` 与“非 Agent 缺陷”表述仅为早期假设，未获反事实验证，不构成已确认归因。本文中的业务 Domain 映射也未核验；以 `59-harness10-taskbook.md` Revision 2 为准：失败记录为 `OBSERVED` + `failureObserved=true` + `attributionStatus=UNRESOLVED`，Stage 9 回放统一进入 `UNSCOPED_REPLAY` 且不参与正向检索。

> **【智能体存证元数据】**
> - 物理智能体标识 (ConversationId): `785fa860-1119-4846-a8c9-a2af2bbbad86`
> - 智能体角色: 实验与泄漏审计员 (Experiment Governance & Anti-Leakage Specialist)
> - 运行 Transcript: [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/785fa860-1119-4846-a8c9-a2af2bbbad86/.system_generated/logs/transcript.jsonl)

# 阶段十（Stage 10）经验库治理与防泄漏法医级审计报告

**报告人**：智能体 C（实验与泄漏审计员 / Experiment Governance & Anti-Leakage Specialist）  
**审计对象**：Stage 9 真实 PPC 负结果资产、Stage 10 领域经验库（Per-Domain Experience Library）入库契约、INVALID_FIXTURE 判定边界与防泄漏门禁  
**审计基准**：Stage 9 运行事实（提交 `3eb94e45`，资产目录 `experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/`）、上游开源参考 `harness2/core/context.py` 与 `harness2/core/library.py`、AGENTS.md 最高学术与工程法典。

---

## 一、Stage 9 真实产物导入与 Disposition 映射规则

### 1.1 核心原则与状态机定义
在上游 `harness2/core/library.py` 的原始实现中，经验库仅设计为记录被最终采纳的 Harness（`Library.add(task_id, harness, ...)`）。然而在顶刊级学术范式与工业级 Agent 演化架构中，**真实负向经验（Negative Results）与失效评测夹具（Invalid Fixture）具备同等重要的认知价值与安全隔离意义**。若对负结果不加区分直接废弃，会导致 Proposer 在后续任务中陷入“重复提出已知劣质变异”的无谓试错；若盲目将未采纳或未通过的产物标为成功，则会向 Proposer 注入错误的迁移先验。

因此，在 Stage 10 经验库中引入强类型不可变状态枚举 `EntryDisposition`：
```java
public enum EntryDisposition {
    ADOPTED,         // 最终被真实 PPC 采纳为新生产基线的候选（Stage 9 此集合严格为 ∅）
    OBSERVED,        // 生产基线在特定基准任务上的真实观测事实（提供基准行为锚点）
    REJECTED,        // 经实证验证劣于基线或未达采纳门槛的候选（记录被拒机理，防止重复变异）
    INVALID_FIXTURE  // 评测夹具自身存在配额/契约设计缺陷的用例（隔离分析，严禁归因为模型缺陷）
}
```

### 1.2 Stage 9 真实产物映射矩阵
依据 Stage 9 实际运行产生的 15 个探针 JSON、`ppc_summary.json` 及 `selected_candidate.json` 的实证数据，确立如下刚性映射矩阵：

| 实体标识 | 映射状态 (`EntryDisposition`) | 实证事实依据 (Stage 9 Evidence) | 经验库入库语义与知识归纳规则 |
| :--- | :--- | :--- | :--- |
| **`baseline` 凭单及其通过任务** (`kb-qa-01`, `tool-route-02`, `param-guard-03`) | **`OBSERVED`** | - `kb-qa-01`: 真实 RAG 注入，命中指数退避与 SHA-256 幂等键<br>- `tool-route-02`: 真实调用金融双工具，成功核验扣款与余额<br>- `param-guard-03`: 纯提示词零工具，成功拦截 `25:99:99` 时间越界 | **可信观测基准事实**：记录生产基线在三类标准任务下的稳定表现与开销（11,864 tokens, ¥0.02175624），作为后续领域任务横向对比的 Baseline 证据，证明当前三类能力具备零回归保障。 |
| **`candidate-a`** (变异算子: `CONCISE_SYSTEM_PROMPT`) | **`REJECTED`** | - 补全 Tokens: 5,276（较 baseline +17.0%）<br>- 总费用: ¥0.02462980（较 baseline +13.2%）<br>- 质量通过率: 3/5（未解决任何失败用例） | **真实负向经验（过度精简提示词反致发散）**：删减系统提示词中的结构化约束与负向边界，未能降低成本，反而导致模型丢失指引，诱发长思维链推理冗余发散（Prompt Tokens 略降 65，但 Completion Tokens 激增 766）。**严禁后续 Proposer 重复尝试无边界删减约束提示词**。 |
| **`candidate-b`** (变异算子: `GUARDRAIL_TIGHTENING`) | **`REJECTED`** | - 补全 Tokens: 4,791（较 baseline +6.2%）<br>- 总费用: ¥0.02080370（较 baseline -4.4%）<br>- 质量通过率: 3/5（未解决任何失败用例，未达改进 $\ge 3$ 题门槛） | **真实负向经验（单纯收紧步数无法弥补能力短板）**：将循环步数从 3 强制收紧至 2，虽然总 Token 略降，但在面对复合依赖与参数短缺时，由于缺乏前置信息补全机制，步数截断无法改善解答质量，未达成改进采纳标准。 |
| **`ctx-history-04` 探针证据** (HISTORY 任务) | **`INVALID_FIXTURE`** | - 三组探针均为：`completionTokens = reasoningTokens = 1024`<br>- `finalOutputSummary = ""`（最终答复为空）<br>- 原因：输出 Token 硬限耗尽 | **失效夹具（配额预算与思考模式不匹配）**：评测环境设定单次调用最大输出为 1,024 tokens。`deepseek-flash` 开启思考模式后，针对复杂长上下文的多轮架构分析耗尽了全部 1,024 个 token 用于内部思维链推理，导致模型无预算输出正文答复。**此为测试夹具输出配额失真，不得将此判定为 Agent 历史会话理解能力的内在缺陷**。 |
| **`comp-rag-tool-05` 探针证据** (COMPOSITE 任务) | **`INVALID_FIXTURE`** | - 模型答复：“该工具需要 clusterId，当前未提供目标集群 ID，无法执行真实基线查询”<br>- 评测判定：未调用 `arch_metrics_calculator` $\implies$ 判定失败 | **失效夹具（问题提示与工具入参契约未闭环）**：测试题目要求调用 `arch_metrics_calculator`，但问题文本与上下文未提供 `clusterId`，而该工具底层契约严格要求 `MetricsInput(clusterId)`。模型严谨指出前置参数缺失，但由于未发起工具调用被评测断言一票否决。**此为夹具题目设计与工具契约脱节，属于失效评测夹具**。 |

### 1.3 刚性准入断言
在任何导入或写入流程中，设置一票否决断言：
- **`ADOPTED` 状态硬断言**：仅当 `selectionResult.finalVerdict().equals("ADOPTED")` 且 `selectedCandidateId != "baseline"` 时，方允许生成 `ADOPTED` 实体。在 Stage 9 资产中，`ADOPTED` 导入集合**严格必须为空**！
- **`SUCCESS` 状态硬断言**：严禁将任何质量未通过（`qualityPass == false`）或被判定为 `INVALID_FIXTURE` 的探针标记为 `SUCCESS`。

---

## 二、`passedCases` 语义误导纠正方案

### 2.1 误导根源的法医级分析
在 Stage 4/Stage 5 的历史代码（如 `OfflineProbeRunner.java`）中，引入了 `passedCases: 5` 字段。
其原始工程语境是：“该轮探针调度器成功调度并执行完毕了 5 个基准用例，未出现运行期崩溃”。
然而，在 Stage 9 的真实运行中：
- 真实执行的探针总数是 **15 个**（3 个候选 $\times$ 5 个用例），模型调用 **26 次**；
- 真正质量通过的用例仅为 **3 个**（`kb-qa-01`, `tool-route-02`, `param-guard-03`），通过率仅为 **60%**；
- 剩余 2 个用例全部因为夹具配额耗尽与契约短缺而导致 `qualityPass = false`。
如果外界或后续 Agent 仅看历史摘要中的 `passedCases=5`，会直接产生“5 个任务质量全部通过”的严重误判与学术幻觉，掩盖了真实的负结果与失效夹具事实。

### 2.2 Stage 10 语义多维拆解与向后兼容契约
为了在保持历史 JSON 资产不可变的前提下彻底消除语义歧义，Stage 10 制定如下规范：

1. **历史资产不可变（Immutability Boundary）**：
   `experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/` 目录下的 `ppc_summary.json` 与 `checksums.sha256` 保持物理只读与 SHA-256 哈希冻结，**绝不进行任何原地回写或篡改**。
2. **多维统计指标显式分离模型**：
   在 Stage 10 经验库摘要契约（`ExperienceRunSummaryRecord`）中，废弃单一的 `passedCases`，全面重构为多维强类型指标：
   ```java
   public record ExperienceRunSummaryRecord(
       String runId,
       int totalProbesExecuted,       // 真实探针执行总数：15
       int totalModelCallsExecuted,    // 真实模型调用总数：26
       long totalTokensConsumed,       // 消耗 Token 总数：35,500
       double totalCostCnyConsumed,    // 真实费用总额：¥0.06718974
       int uniqueCasesEvaluated,       // 评估的基准用例总数：5
       int qualityPassedCases,         // 真实质量断言通过用例数：3
       int invalidFixtureCases,        // 确认的失效夹具用例数：2
       int evaluatedCandidates,        // 参与评估的候选数：3 (baseline, a, b)
       int rejectedCandidates,         // 被真实 PPC 拒绝的候选数：2 (candidate-a, candidate-b)
       int adoptedCandidates,          // 最终被采纳的候选数：0
       String finalVerdict,            // 最终判定："REJECTED_AFTER_REAL_PPC"
       @Deprecated(since = "Stage10", forRemoval = true)
       int passedCases                 // 兼容遗留接口：必须严格等于 qualityPassedCases (3)，严禁返回 5！
   ) {}
   ```
3. **向后兼容性保障**：
   对于必须读取 `passedCases` 的历史旧版解析器，适配器中其返回值强制对齐为 `qualityPassedCases`（即返回 3），并伴随输出 `WARN` 日志提示调用方迁移至多维显式接口；严禁任何逻辑将 `passedCases` 返回为 5。

---

## 三、零 Qrel / 零 Judge 判词泄漏铁律（Zero-Leakage Invariant）

### 3.1 泄漏危害与禁入机理
在上游 `harness2/core/context.py` 的设计哲学中，严禁任何评分器内部判词（`GRADER_VERDICT_MARKERS`）和答案金标（`assert_no_leak_paths`）进入模型上下文。
在经验库场景下，`INDEX.md` 和 `EXPERIENCE.md` 会作为先验知识（`context/library/`）挂载给后续任务的 Proposer 模型。
- **为什么 `cue` 和 `lesson` 绝对不能包含评测集的 `expectedKeywords`？**
  若经验库中出现具体的答案字符串或特定测试数据字面量（如交易流水号 `"TX-20261029-8891"`、非法时间 `"2026/10/29 25:99:99"`、金额 `"5800.00"`、注入块 ID `"chunk-pay-idem-01"`），后续的 Proposer 模型在优化其他任务或相同领域的新任务时，会发生致命的“测试集捷径拟合（Shortcut Learning）”——直接将这些魔数硬编码进 System Prompt 或注入规则中。这会导致基准评测彻底失真，形成严重的学术欺诈与工程作弊（Test Set Contamination）。
- **为什么绝对不能包含评分器判词（Grader Verdicts）？**
  若包含 `"deterministic_eval_result"`、`"qualityPass"`、`"scoring_rubric"` 等判词，LLM Proposer 可能会在生成的 System Prompt 中注入欺骗评分系统的伪造日志或对抗性标记，破坏 Harness 评测闭环的客观隔离性。

### 3.2 抽象归纳 vs 具象泄漏对比范式

| 维度 | 严禁的泄漏写法（具象字面量 / 一票否决） | 合规的抽象归纳写法（方法论先验 / 允许入库） |
| :--- | :--- | :--- |
| **`cue`** | `当遇到 TX-20261029-8891 流水号或 25:99:99 时间格式时` | `当涉及金融交易凭证核查，或涉及外部传入时间戳的合法性校验时` |
| **`lesson`** | `在参数校验中必须对 2026/10/29 25:99:99 判定越界，指出 25 小时和 99 分钟非法` | `在参数防护中，必须在提示词内明确 24 小时制（00-23/00-59）格式公理，指示模型在无需调用工具的情况下直接实施防御拦截` |
| **`lesson`** | `精简提示词后，扣款流水 TX-20261029-8891 依然返回 SUCCESS 和 ACC-9901` | `系统提示词的约束精简存在边际效益递减，过度精简核心安全原则会导致模型推理思考链冗余发散，反而增加 17% 的补全 Token 消耗` |
| **`evidence`** | `qualityPass=true, deterministic_eval_result=SUCCESS` | `三项标准能力探针均在无干预下达成业务目标，但输出 Token 配额受限导致长思考链截断` |

### 3.3 `ExperienceWriter` 中的硬拦截断言体系 (`assertNoQrelOrGraderLeakage`)
在 Stage 10 核心组件 `ExperienceWriter` 与 `Library` 中，构建逐字扫描硬拦截器：

```java
public final class ExperienceLeakageGuard {
    // 继承并扩展上游 GRADER_VERDICT_MARKERS
    private static final List<String> GRADER_VERDICT_MARKERS = List.of(
        "deterministic_eval_summary", "deterministic_eval_result",
        "judge_scope_and_deterministic_status", "Judge findings on the rendered result",
        "\"passed_count\"", "\"pass_rate\"", "\"test_pass_rate\"",
        "qualitypass", "scoring_rubric", "gold_rubric", "qrels.tsv",
        "ground_truth_answer", "expectedkeywords"
    );

    // 动态提取与静态注入的已知评测集金标 (Answer Key / Qrel Markers)
    private static final List<String> KNOWN_QREL_MARKERS = List.of(
        "tx-20261029-8891", "25:99:99", "acc-9901", "5800.00",
        "chunk-pay-idem-01", "chunk-arch-ebpf-01"
    );

    public static void assertNoQrelOrGraderLeakage(String contextName, String content) {
        if (content == null || content.isBlank()) return;
        String lower = content.toLowerCase(Locale.ROOT);

        for (String marker : GRADER_VERDICT_MARKERS) {
            if (lower.contains(marker)) {
                throw new ExperienceLeakageException(
                    "GRADER_VERDICT_LEAKAGE: Context [" + contextName + "] contains grader marker: " + marker);
            }
        }
        for (String qrel : KNOWN_QREL_MARKERS) {
            if (lower.contains(qrel)) {
                throw new ExperienceLeakageException(
                    "QREL_LEAKAGE: Context [" + contextName + "] contains test-set literal qrel: " + qrel);
            }
        }
    }
}
```
**拦截范围**：在写盘前，对 `cue`、`lesson`、`evidenceSummary`、`why` 以及随同归档的所有 Harness 配置文件内容与相对路径文件名，执行全量深度遍历。若发现任何违规 Token，立即抛出 `ExperienceLeakageException` 并中断原子写盘。

---

## 四、经验库与运行时生产配置的严格隔离（No Auto-Mutation Gate）

### 4.1 核心威胁与隔离诉求
自动化系统最忌讳“未经人类审计或离线门禁的自适应在线漂移”。如果经验库具备任何反向修改当前活跃生产凭单（`HermesHarnessVoucher`）的通道，一旦经验库受到污染或被注入恶意对抗样本，将导致线上生产服务瘫痪。

### 4.2 四重防线隔离架构（No Auto-Mutation Gate）

```
┌────────────────────────────────────────────────────────┐
│               Offline Optimization Space               │
│                                                        │
│   ┌────────────────────────┐    Read-Only              │
│   │ Per-Domain Experience  │ ──────────────┐           │
│   │ Library (Filesystem)   │               ▼           │
│   └────────────────────────┘      ┌────────────────┐   │
│               ▲                   │ Offline        │   │
│               │ Append-Only       │ Proposer Model │   │
│   ┌────────────────────────┐      └───────┬────────┘   │
│   │ Real PPC Benchmark     │              │            │
│   │ (15 Probes, Watchdog)  │              ▼            │
│   └────────────────────────┘      ┌────────────────┐   │
│                                   │ Proposed       │   │
│                                   │ Candidates     │   │
│                                   └───────┬────────┘   │
└───────────────────────────────────────────┼────────────┘
         ═══════════════════════════════════╪═════════════════════  [PHYSICAL AIR-GAP]
                                            ▼
┌────────────────────────────────────────────────────────┐
│               Production Runtime Space                 │
│                                                        │
│  [NO Auto-Mutation Gate / Strict Enforce]              │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Active HermesHarnessVoucher (Immutable Record)   │  │
│  │ - SHA-256 Signature Sealed                       │  │
│  │ - Hardcoded / Statically Configured in Spring    │  │
│  │ - NO Watcher / NO Dynamic Reloader / NO Mutator  │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌─────────────────────────┐  Enforce   ┌────────────┐ │
│  │ AgentOrchestrator.chat  │ ─────────► │ Kernel     │ │
│  └─────────────────────────┘            │ Bridge     │ │
│                                         └────────────┘ │
└────────────────────────────────────────────────────────┘
```

1. **单向只读管道（Unidirectional Pipeline）**：
   - 经验库目录（`experiment/library/`）仅以只读模式（`Read-Only`）向离线 Proposer 暴露 `context/library/INDEX.md` 与 `EXPERIENCE.md`；
   - 经验库模块不引用任何生产运行时的写接口，彻底切断反向调用依赖。
2. **凭单不可变与密码学封签（Cryptographic Immutability）**：
   - 生产环境中注入给 `AgentOrchestrator` 的凭单是纯 Java 21 Record（`HermesHarnessVoucher`），且具备不可变 SHA-256 签名（`voucherFingerprint`）；
   - 系统中严禁提供 `updateProductionVoucher()`、`reloadFromExperience()` 等任何热更新方法。
3. **零后台守护进程与零事件总线（Zero Daemon / Zero Event-Bus）**：
   - 架构内严禁注册任何监听经验库文件变动的 `WatchService`、定时轮询任务（`@Scheduled`）或响应式回调；
   - 严禁通过 Spring Event、Guava EventBus 或消息队列将经验库写入事件广播至生产服务。
4. **显式离线发布准入审批门禁（Air-Gapped Promotion Gate）**：
   - 只有在完整 PPC 运行输出 `finalVerdict = ADOPTED`，且经过离线审计生成带校验和的发布制品后，才允许由运维人员通过 Git 提交、显式修改配置项推进版本升级，杜绝任何运行期自动变异。

---

## 五、Stage 10 测试门禁与零外网保障规划

### 5.1 环境基线与网络隔离准则
- **Java 21 虚拟环境隔离铁律**：测试必须在指定 SDKMAN 路径下编译执行：
  `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
- **默认构建 100% 零外网保障**：
  默认执行 `mvn test` 时，绝对不发起任何外部网络请求，不依赖真实 `DEEPSEEK_API_KEY`，所有外部模型依赖均通过 Mock、内存契约或 Test Double 承载。
- **工作区洁净度保证（Clean Workspace Invariant）**：
  测试期间所生成的所有测试库文件、临时索引与归档文件，均必须依托 JUnit 5 的 `@TempDir` 临时目录执行，测试完成后自动销毁，严禁污染 `experiment/outputs/` 或工作区主干。

### 5.2 定向测试类规划与验证矩阵

| 测试类全限定名 | 归属类型 | 重点验证契约与审计边界 | 预期指标与断言 |
| :--- | :--- | :--- | :--- |
| `tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibraryContractTest` | 离线契约测试 | 1. **目录结构规范**：验证 `INDEX.md`, `EXPERIENCE.md`, `harness/` 目录组织与 slug 命名合规性。<br>2. **经验有效性**：验证 `hasExperience()` 强契约（`cue`, `lesson`, `evidence` 必须非空且满足最小长度）。<br>3. **零泄漏硬拦截**：向 `cue`、`lesson`、`why` 或 Harness 文件中注入 `qualityPass`、`TX-20261029-8891`、`25:99:99` 等标记时，必须 100% 触发 `ExperienceLeakageException` 拒绝写盘。<br>4. **同领域隔离与淘汰**：验证 `domain_dir` 转义、`forget(task_ids)` 与 `retain(task_ids)` 在不跨领域污染下的目录自愈与索引重构。 | - 100% 离线通过<br>- 零网络 I/O<br>- 违规字符拦截率 100% |
| `tech.qiantong.qknow.hermes.agent.harness.experience.HarnessExperienceStage10Test` | 综合治理测试 | 1. **Stage 9 真实资产回放导入**：读取 Stage 9 真实 JSON 资产，验证状态映射逻辑：<br>   - `baseline` 通过任务 $\implies$ `OBSERVED`<br>   - `candidate-a/b` $\implies$ `REJECTED` 并记录过度精简/无效收紧原因<br>   - `ctx-history-04` 与 `comp-rag-tool-05` $\implies$ `INVALID_FIXTURE`<br>   - 断言 `ADOPTED` 集合严格为空。<br>2. **多维统计解析验证**：解析回放数据，断言 `totalProbesExecuted=15`, `qualityPassedCases=3`, `invalidFixtureCases=2`, `rejectedCandidates=2`, `adoptedCandidates=0`，断言彻底杜绝 `passedCases=5` 的语义误导。<br>3. **运行时隔离验证**：断言经验库写操作完全不触发 `HermesKernelBridge` 凭单状态的任何变更。 | - 100% 离线通过<br>- 状态映射契约吻合率 100%<br>- 生产 Voucher 变异拦截率 100% |

### 5.3 门禁准入执行指令
```bash
# 1. 验证 Stage 10 经验库契约与防泄漏门禁（快速离线测试，目标耗时 < 3 秒）
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/pom.xml test \
  -Dtest=ExperienceLibraryContractTest,HarnessExperienceStage10Test

# 2. 全仓默认回归测试（确保 100% 零外网，2160+ 测试全部绿色通过）
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/pom.xml test
```

---

## 六、法医级审计裁决总结

1. **事实定性**：Stage 9 真实 PPC 是完全可信、具备哈希完整性（SHA-256）的宝贵**负结果实证资产**。保留 `baseline`、拒绝 2 个候选是完全正确的决策。
2. **入库定界**：Stage 10 必须建立 `OBSERVED`、`REJECTED`、`INVALID_FIXTURE` 三分法，严禁将未通过的任务或被拒候选记录为 `SUCCESS` 或 `ADOPTED`。
3. **消除误导**：彻底纠正 `passedCases=5` 造成的“全部通过”假象，以不可变回放方式向后兼容拆解为多维精确指标。
4. **防泄漏拦截**：通过 `assertNoQrelOrGraderLeakage` 硬门禁，严守经验库文本纯净度，杜绝测试集答案与评分器状态逆向污染 Proposer。
5. **安全隔离**：严禁经验库对生产运行凭单的任何自动热更新与变异渗透，确保生产稳定与科研严谨双重闭环。

本审计规则已具备完整的理论支撑、数学定义与工程契约，已准备就绪进入 Stage 10 架构设计与核心代码实现。
</SYSTEM_MESSAGE>
