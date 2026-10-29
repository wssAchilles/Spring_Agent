# Agent B: Hermes 工程适配审计员 (Code Forensic & Benchmark Specialist) 独立审计报告

> **智能体角色**：Agent B: Hermes 工程适配审计员 (Code Forensic & Benchmark Specialist)  
> **物理 ConversationId**：`5df77102-d1be-4c9a-94d5-a69ce4215c79`  
> **审计基准与分支**：`feat/hermes-harness-stage2` -> 阶段三推进契约  
> **阶段二实施代码基线**：Commit `26c913e5`（包含真实时序、成本、RAG 分数与 Proto 构建修复）  
> **目标开源仓库**：Google Research `harness2`（Commit `1cfe254e8d25b15f37048c3730134686abe60335`，Apache License 2.0）  
> **本地审计源码路径**：`/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/`  
> **生产工程基线**：SDKMAN 隔离 Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)、DeepSeek 官方活跃模型、阿里千问 1536 维超球面单位向量空间  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范  
> **输出目标文件**：`docs/architecture-review-20261019/acceptance/31-harness3-agent-B.md`

---

## 阶段三唯一待验证假设 (Unique Falsifiable Hypothesis)

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

---

# 第一部分：Harness² 核心组件法医级代码审计 (Code Forensics)

围绕阶段三的闭环演进机制，对 Google Research `harness2`（commit `1cfe254e`）的 4 大核心模块开展逐行法医解构：

### 1. `harness2/core/proposer.py`：变异会话隔离与工作区收割机制

#### (1) 数据结构与生命周期模型
- **`Reply` 数据契约（Lines 71–89）**：定义单次改进会话结果，封装 `label`、`text`、`ok`、`cost_usd`、`wall_s`、`attempts`、`cached`、`error`、`workspace`（提取的工作区路径）。其 `__bool__` 运算符绑定 `self.ok`，防止空应答或异常被静默吞没。
- **`_call_fingerprint` 缓存与幂等设计（Lines 143–147）**：变异调用的缓存键由 `(label, mission, system, context_dir)` 的 SHA-1 摘要驱动。若本地审计目录中存在有效响应与工作区，直接短路返回，确保变异生成的确定性与可复现性。
- **`harvest` 工作区收割（Lines 313–326）**：通过 `_harvest(tmp, sub, label, cache_key)` 在临时会话容器销毁前，将其内部修改的组件目录（如 `context/harness`）原子性拷贝至持久化审计路径，并清洗非法字符，保证变异产物安全持久化。

#### (2) 异常控制与重试退避（Lines 154–208）
- 采用显式循环重试（默认 `attempts=2`），并在第 2 次尝试前引入线性退避（`time.sleep(10 * (attempt - 1))`）；
- 单次调用严格受控于 `wall` 超时看门狗（`subprocess.TimeoutExpired`），捕获后不会抛出破坏调用栈，而是记录至 `error`，仅在所有重试耗尽后抛出携带完整开销的 `ProposerError`；
- **并发信号量约束（Lines 42–43）**：通过 `threading.BoundedSemaphore(MAX_CONCURRENCY)` 控制多会话并发，防止本地或 API 客户端瞬时过载。

#### (3) 对照 Hermes 的工程审判
- **可移植精要**：变异器的输入必须严格基于不可变快照；变异产物必须在独立沙盒内生成，经合法性校验后收割；
- **坚决拒绝**：`proposer.py` 底层调用 CLI 子进程（`subprocess.run(["claude", ...])` 或 Bun 脚本），在企业级 Java 21 微服务中具有极高进程开销与安全漏洞风险。Hermes 必须采用 Java 21 强类型策略接口，使用 DeepSeek 官方客户端或标准模板生成变异候选，坚决禁止宿主机进程生成与动态执行。

---

### 2. `harness2/core/selection.py`：匿名盲审与防偏置选择器

#### (1) SHA-256/SHA-1 种子确定性洗牌（Lines 71–86）
- **算法还原**：
  ```python
  def assign_ids(labels: Sequence[str], *, task_id: str, k: int, mode: str) -> dict[str, str]:
      seed = int(hashlib.sha1(f"{task_id}|{k}|{mode}".encode()).hexdigest()[:12], 16)
      ordered = sorted(labels)
      random.Random(seed).shuffle(ordered)
      return {_anon_id(i): lbl for i, lbl in enumerate(ordered)}
  ```
- **核心逻辑**：候选者被分配形如 `A`、`B`、`C`... 的匿名代号。先对原始标签排序，再基于任务与轮次的固定哈希种子进行伪随机打乱。完全抹去候选护具的来源身份（如 `base` vs `cand_inv`），从根本上消除大模型的主观偏好。

#### (2) 候选池过滤与静默回退（Lines 114–153, 237–254）
- **`_render_pool` 产物门禁**：遍历候选池，检查 `rv.ok and rv.delivered`。若某候选因异常未交付产物，或产物渲染为空，直接加入 `sel.dropped` 排除列表，严禁坏损候选进入评选；
- **`_fallback` 中立降级**：若评选器调用失败或解析错误，优先安全回退至已交付的 `base` 基线；若无基线产物则标记回退至首个可用候选，确保执行流程永不崩溃。

#### (3) 盲审提示词解析与反思经验抽取（Lines 333–357, 454–506）
- **选择块解析**：通过正则 ````choice\s*\n?\s*([A-Za-z0-9_-]+)\s*\n?\s*```` 与 `<why>(.*?)</why>` 严格解析裁决；
- **经验卡片验证**：提取 `<experience>` 结构化四元组（`cue`, `lesson`, `evidence`, `confidence`），并在入库前强制触发 `assert_tree_has_no_grader_verdicts()`。

#### (4) 对照 Hermes 的工程审判
- **可移植精要**：匿名代号映射、严格排除未交付执行、平局与失败中立回退机制；
- **演进增强**：Harness² 官方实现为单向提示评选，存在大模型固有的“位置偏置（Position Bias）”（倾向于选择选项 A 或靠前的选项）。Hermes 阶段三落地必须实现**双向交叉盲审（Position-Swap Dual Pass）**：对同一对候选执行 `(A, B)` 与 `(B, A)` 两次对称评测，只有结论一致才认定胜出，否则判定为平局并保持 Baseline。

---

### 3. `harness2/core/recursion.py`：递归调度与树级验证

#### (1) 预算守恒与调度时序（Lines 41–105）
- **两层预算约束**：明确 $k$ 轮任务级递归的总 Rollout 预算 $R = 4k$，改进会话预算 $P = 3k$；
- **阶段封装 `Stage` 与结果 `ModeResult`**：记录每个变异维度的开销、回退状态（`fell_back`）与决策说明（`notes`）。

#### (2) 护具树修复与门禁验证（Lines 108–134）
- **`_accept_tree`**：
  1. 调用 `autotrigger()` 自动补全可能缺失的触发标记；
  2. 计算组件差异摘要 `diff_summary()`；
  3. 触发多重校验（字符上限、合法脚本、防泄漏标记）；
  4. 验证不通过时提供一次重试机会（`STAGE_ATTEMPTS = 2`），重试仍不通过则继承 fallback 基线。

#### (3) 对照 Hermes 的工程审判
- **可移植精要**：$k=1$ 时的极简闭环拓扑（1 次基线执行 + 变异生成 + 探针执行 + 差分提取 + 盲审）；
- **简化裁剪**：阶段三聚焦单一任务维度的组件微调，无需运行高复杂度的跨任务领域经验库持久化（`library.py`），严格控制单变量实验边界。

---

### 4. `harness2/core/context.py`：证据树隔离与零泄漏铁律

#### (1) 外置化证据树拓扑（Lines 43–103）
- 证据全部写入文件系统，包含 `task.md`、`rollouts/`、`deliverables/`、`harness/`；
- 向大模型发送的 `mission()` 仅包含证据文件的短指针路径（Short Pointer）与目录布局说明，强制模型根据需要阅读，避免将全量执行轨迹硬塞进 Prompt 导致超长溢出与缓存失效。

#### (2) Grader 评分信息零泄漏防御（Lines 419–491）
- **硬编码敏感词标记清单**：
  ```python
  GRADER_VERDICT_MARKERS = (
      "deterministic_eval_summary",
      "deterministic_eval_result",
      "judge_scope_and_deterministic_status",
      "Judge findings on the rendered result",
      '"passed_count"',
      '"pass_rate"',
      '"test_pass_rate"',
  )
  ```
- **双重雷区扫描（Tripwire Defense）**：
  - **文件名扫描**：遍历上下文目录中的每一个文件相对路径，一旦命中敏感标记直接抛出 `ValueError`；
  - **文本内容扫描**：对包含在白名单后缀（`.md`, `.json`, `.txt`, `.py` 等）中的文件进行全文本扫描，一旦发现评分判定词立刻拦截并阻断任务；
  - **设计宗旨**：绝不将“基准评测的分数判定”透传给大模型作为优化依据，迫使大模型完全依靠物理执行表现、工具调用痕迹与交付物完整性进行真实归纳。

---

# 第二部分：Hermes 阶段二工程资产法医对照与差距诊断

### 1. 阶段二落地资产逐行核验（Commit `26c913e5`）

| 核心组件 | 物理文件路径 | 核心特征与法医核验结论 |
| :--- | :--- | :--- |
| **聚合根凭单** | `HermesHarnessVoucher.java` | 纯不可变 Java 21 Record；8 大组件强内聚；`canonical()` 使用 `\u001e` 组装；`computeFingerprint()` 生成确定性 SHA-256；`matchesFingerprint()` 常量时间比对。 |
| **执行证据凭单** | `HarnessEvidenceRecord.java` | 不可变 Record，包含 14 维真实物理字段（真实耗时、工具调用摘要哈希、真实 Token 成本估算、RAG 置信度）；提供 `computeDigest()` 与 `verifyDigest()` 常量时间校验。 |
| **证据采集器** | `HarnessEvidenceCollector.java` | 内存无阻塞并发收集；支持 `addCompletionListener` 观察者模式；`finalizeEvidence()` 终结后即锁定为不可变对象。 |
| **运行时装配器** | `HermesHarnessAssembler.java` | 静态 Prompt 分离组装；动态上下文进 UserMessage；内置出厂与请求凭单装配工厂。 |
| **核心调度器** | `AgentOrchestrator.java` | 全面接驳 `effectiveVoucher`；支持 `registerRequestVoucher` 请求级凭单覆盖；通过 `wrapWithCycleGuard` 采集真实工具耗时与出入参摘要哈希；退出点 100% 触发 `finalizeEvidence`。 |

### 2. 阶段三落地工程差距（Gap Analysis）

对照阶段三唯一假设，当前代码库尚缺乏以下 5 大核心机制：
1. **基准任务集缺失**：缺乏标准化的离线小样本基准测试用例集（包含标准输入、依赖工具、预期业务完备性判定维度）；
2. **离线变异器缺失**：缺乏针对 `InvariantSystemPromptRecord`、`ToolGuidancePolicyRecord`、`MemoryContextStrategyRecord` 三大低风险组件的类型安全受控变异器；
3. **隔离探针与差分提取缺失**：缺乏能安全调用 `AgentOrchestrator` 执行候选凭单、获取 `HarnessEvidenceRecord` 并计算物理差分（$\Delta \text{Tokens}$, $\Delta \text{Duration}$, 工具调用召回差异）的探针执行器；
4. **双向盲审选择器缺失**：缺乏实现 SHA-256 种子打乱、双向评估（Dual-Pass Swap）与零泄漏扫描的选择器契约；
5. **离线凭单确定性回放器缺失**：缺乏离线凭单确定性 JSON 序列化与不依赖大模型调用的回放验证器。

---

# 第三部分：Hermes 阶段三 Java 21 落地契约设计

遵循 Java 21 最佳工程实践与《AGENTS.md》铁律，设计高内聚、类型安全、零污染的离线评测与优化契约体系：

### 1. 离线基准任务集数据契约 (`OfflineBenchmarkTestCase`)

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 离线小样本基准任务用例契约（不可变 Record）。
 */
public record OfflineBenchmarkTestCase(
        String taskId,
        TaskCategory category,
        String question,
        Map<String, Object> inputParams,
        List<String> availableTools,
        String expectedToolName,
        int minContextTokensThreshold
) {
    public enum TaskCategory {
        KNOWLEDGE_QA,          // 知识库精确问答
        MULTI_TOOL_SELECTION,  // 多工具选择与动态路由
        TOOL_PARAM_GUARD,      // 工具参数校验与容错
        CONTEXT_HISTORY_RECALL,// 多轮对话上下文历史理解
        COMPOSITE_REASONING    // 复杂推理与检索工具组合
    }

    public OfflineBenchmarkTestCase {
        taskId = Objects.requireNonNull(taskId, "taskId 不能为空");
        category = Objects.requireNonNull(category, "category 不能为空");
        question = Objects.requireNonNull(question, "question 不能为空");
        inputParams = inputParams == null ? Map.of() : Map.copyOf(inputParams);
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }
}
```

#### 5 个代表性离线基准真实用例定义：
1. **TC-01（知识库问答）**：`taskId="kb-qa-01"`，关于企业报销政策的严谨回答，要求不得捏造事实，优先引用 RAG 文档；
2. **TC-02（多工具路由）**：`taskId="tool-route-02"`，给定用户需求从天气、汇率换算、企业员工查询 3 个工具中精准选择员工查询；
3. **TC-03（工具参数纠错）**：`taskId="param-guard-03"`，输入模糊或非标准的日期参数，验证工具参数前置约束是否能引导 Agent 自纠；
4. **TC-04（短期上下文理解）**：`taskId="ctx-history-04"`，多轮对话“上一句提到的项目负责人是谁？”，验证短期记忆滑动窗口截断是否准确保留前序实体；
5. **TC-05（检索与工具复合推理）**：`taskId="comp-rag-tool-05"`，先检索制度规范，再调用计算器工具核算违约金，验证复合推理的完整性。

---

### 2. 离线 Proposer 变异器契约 (`OfflineHarnessProposer`)

变异器严格锁定在 **3 个低风险组件**，严禁触碰危险全局状态：
1. **`InvariantSystemPromptRecord`**：进行结构化与角色约束微调（如补充 `<role_focus>`，精简冗余套话以节省 Prompt Token）；
2. **`ToolGuidancePolicyRecord`**：开启 `autoToolSelectionEnabled` 并强化工具选择先验指引；
3. **`MemoryContextStrategyRecord`**：微调短期滑动窗口（如从默认 20 调整为紧凑的 10 或扩展至 15，优化 Token 占用与实体召回平衡）。

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.*;

/**
 * 离线受控变异提议器接口。
 */
public interface OfflineHarnessProposer {

    /**
     * 变异目标维度定义。
     */
    enum MutationDimension {
        SYSTEM_PROMPT_CONCISE,    // 角色提示词精简与边界固化（追求负 Token 增量）
        TOOL_GUIDANCE_ENHANCED,   // 工具选择策略参数强化
        MEMORY_WINDOW_OPTIMIZED   // 短期记忆窗口紧凑化调整
    }

    /**
     * 基于基线凭单生成单变量变异候选凭单。
     */
    HermesHarnessVoucher proposeMutation(
            HermesHarnessVoucher baselineVoucher,
            MutationDimension dimension,
            OfflineBenchmarkTestCase testCase
    );
}
```

#### 变异合法性硬门禁校验器 (`MutationSafetyValidator`)：
- **静态前缀缓存保护**：强制校验新 System Prompt 不包含任何动态占位符（`${...}`、`{{...}}`），确保 100% 命中 DeepSeek Context Caching；
- **字符上限控制**：System Prompt 字符增量 $\le 0$（严格防止膨胀）；
- **参数范围限制**：Memory 窗口限制在 $[5, 30]$ 区间内，严禁配置 0 或极值导致内存泄漏或 OOM。

---

### 3. Probe 隔离探针执行器与差异提取算法 (`OfflineProbeExecutor` & `EvidenceDiffExtractor`)

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;

import java.util.Objects;

/**
 * 隔离探针执行结果与物理差异凭单。
 */
public record ProbeExecutionDiff(
        String taskId,
        String baselineEvidenceId,
        String candidateEvidenceId,
        long baselineTokens,
        long candidateTokens,
        long deltaTokens,             // candidateTokens - baselineTokens（要求 <= 0）
        long baselineDurationMs,
        long candidateDurationMs,
        long deltaDurationMs,         // candidateDurationMs - baselineDurationMs（要求 <= 5ms）
        double baselineCostCny,
        double candidateCostCny,
        boolean toolSelectionMatched, // 候选者工具选择是否精准匹配基准期望
        boolean nonDegradingQuality   // 回效应答是否完备且未退化
) {
    public ProbeExecutionDiff {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(baselineEvidenceId, "baselineEvidenceId");
        Objects.requireNonNull(candidateEvidenceId, "candidateEvidenceId");
    }

    /**
     * 判断是否严格满足门禁硬约束：ΔTokens <= 0 且 ΔDuration <= 5ms。
     */
    public boolean satisfiesBudgetGate() {
        return deltaTokens <= 0 && deltaDurationMs <= 5;
    }
}
```

#### 差异提取算法（`EvidenceDiffExtractor`）：
```java
public class EvidenceDiffExtractor {
    public static ProbeExecutionDiff extract(
            OfflineBenchmarkTestCase testCase,
            HarnessEvidenceRecord baseEvidence,
            HarnessEvidenceRecord candEvidence,
            boolean nonDegradingQuality) {
        
        long deltaTokens = candEvidence.estimatedTokens() - baseEvidence.estimatedTokens();
        long deltaDuration = candEvidence.executionDurationMs() - baseEvidence.executionDurationMs();
        
        boolean toolMatch = true;
        if (testCase.expectedToolName() != null && !testCase.expectedToolName().isBlank()) {
            toolMatch = candEvidence.toolExecutions().stream()
                    .anyMatch(te -> te.toolName().equalsIgnoreCase(testCase.expectedToolName()) && te.success());
        }

        return new ProbeExecutionDiff(
                testCase.taskId(),
                baseEvidence.evidenceId(),
                candEvidence.evidenceId(),
                baseEvidence.estimatedTokens(),
                candEvidence.estimatedTokens(),
                deltaTokens,
                baseEvidence.executionDurationMs(),
                candEvidence.executionDurationMs(),
                deltaDuration,
                baseEvidence.estimatedCostCny(),
                candEvidence.estimatedCostCny(),
                toolMatch,
                nonDegradingQuality
        );
    }
}
```

---

### 4. 匿名盲审 Selection 比较器 (`DualBlindSelectionEvaluator`)

为消除单向提示带来的大模型“位置偏置（Position Bias）”，阶段三实现**双向交叉盲审评估器**：

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 双向匿名盲审裁决结果凭单。
 */
public record SelectionDecisionRecord(
        String taskId,
        String chosenCandidateId,      // 胜出候选标识（若平局或未胜出则为 BASELINE）
        boolean candidateAdopted,      // 候选者是否被采纳替换 Baseline
        String pass1ChosenAnonId,      // Pass 1（A vs B）胜出代号
        String pass2ChosenAnonId,      // Pass 2（B vs A）胜出代号
        boolean positionConsistency,   // 双向位置一致性验证是否通过
        String rationalSummary,        // 盲审裁判理由
        long evaluationDurationMs
) {
    public static final String BASELINE_LABEL = "BASELINE";
    public static final String CANDIDATE_LABEL = "CANDIDATE";
}
```

#### 核心盲审算法执行流程：
1. **防泄漏前置扫描（`assertZeroDataLeakage`）**：
   - 严格审查盲审提示词上下文，禁止出现 `ground_truth_answer`、`rubric_score`、`test_pass_rate` 等敏感评测词汇；
   - 裁判只可见：任务输入、双方匿名输出摘要、执行工具成功率、耗时与 Token 消耗。
2. **确定性种子代号映射（`assignAnonymizedIds`）**：
   - 基于 `SHA-256(taskId + "|selection")` 计算确定性伪随机布尔值，决定 Pass 1 中谁是 `A`、谁是 `B`；
3. **双向交叉评估（Position-Swap Dual Pass）**：
   - **Pass 1**：将 Baseline 与 Candidate 映射为 `(A, B)` 进行匿名对比；
   - **Pass 2**：对调位置为 `(B, A)` 再次进行独立匿名对比；
   - **一致性裁决**：
     - 若 Pass 1 判定 Candidate 对应的代号胜出，且 Pass 2 同样判定 Candidate 对应的对调代号胜出，则 `positionConsistency = true`，判定候选胜出；
     - 若两次评估结论冲突（例如大模型始终选 A，证明存在显著位置偏见）或裁判判定平局，则安全回退，**保持 Baseline 不变**（`candidateAdopted = false`）。

---

### 5. 离线凭单确定性序列化与回放验证器 (`OfflineEvidenceReplayer`)

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * 离线证据确定性回放验证器。
 *
 * <p>在不连接外部 LLM 和网络的环境下，以恒定时间和确定性算法
 * 验证历史沉淀的不可变证据链与签名指纹。</p>
 */
public class OfflineEvidenceReplayer {

    public record ReplayVerificationResult(
            boolean allVerified,
            int totalRecords,
            int validCount,
            List<String> failedEvidenceIds
    ) {}

    /**
     * 确定性回放并验证整组证据凭单的防篡改签名与代数一致性。
     */
    public static ReplayVerificationResult replayAndVerify(List<HarnessEvidenceRecord> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return new ReplayVerificationResult(true, 0, 0, List.of());
        }

        List<String> failures = new java.util.ArrayList<>();
        int valid = 0;

        for (HarnessEvidenceRecord record : evidenceList) {
            String recomputedDigest = record.computeDigest();
            if (record.verifyDigest(recomputedDigest)) {
                valid++;
            } else {
                failures.add(record.evidenceId());
            }
        }

        return new ReplayVerificationResult(
                failures.isEmpty(),
                evidenceList.size(),
                valid,
                List.copyOf(failures)
        );
    }
}
```

---

# 第四部分：AGENTS.md 规范 Research Ledger 结构化证据

严格按照《AGENTS.md》第 2.3 条 14 字段规范填报，无字段缺失或猜测：

```text
id=RS-STAGE3-001
sourceType=official-code
titleOrRepository=google-research/harness2
authorsOrMaintainer=Google Research (Harness² Authors)
venueAndYear=arXiv:2610.14224 (2026)
doiOrArxiv=arXiv:2610.14224
url=https://github.com/google-research/harness2
commitOrTag=1cfe254e8d25b15f37048c3730134686abe60335
license=Apache-2.0
filesOrSectionsRead=harness2/core/proposer.py (lines 1-450: Proposer, Reply, harvest, _cached, _write_audit)
verificationStatus=VERIFIED
relevantFinding=Proposer 采用基于调用签名的局部缓存机制与 harvest 工作区提取模式，支持严格的超时看门狗与线性退避重试，确保变异生成的审计留痕与幂等性。
projectApplicability=可直接采用其缓存指纹思想与会话工作区提取隔离机制；必须拒绝其直接通过 Python subprocess 调起宿主机 CLI 的高风险方式，在 Java 中改用强类型变异接口。
limitations=官方代码高度绑定本地 Python/Bun 执行器，不包含针对 Java/Spring 运行时的类型安全抽象。

id=RS-STAGE3-002
sourceType=official-code
titleOrRepository=google-research/harness2
authorsOrMaintainer=Google Research (Harness² Authors)
venueAndYear=arXiv:2610.14224 (2026)
doiOrArxiv=arXiv:2610.14224
url=https://github.com/google-research/harness2
commitOrTag=1cfe254e8d25b15f37048c3730134686abe60335
license=Apache-2.0
filesOrSectionsRead=harness2/core/selection.py (lines 1-506: assign_ids, selection_pool, _render_pool, _fallback, run_selection, reflect_adoption)
verificationStatus=VERIFIED
relevantFinding=Selection 选择器通过对候选者标签进行 SHA-1 种子伪随机洗牌（A..Z），隐藏真实配置来源；在评选前自动剔除未产出合法结果的候选；发生错误时安全回退至 Base 基线。
projectApplicability=直接指导 Hermes 阶段三匿名盲审选择器的设计；Hermes 需针对性增加 Position-Swap Dual Pass 机制以消除大模型原生偏好。
limitations=未显式处理双向位置偏置（单次决策可能受大模型首选偏见影响）。

id=RS-STAGE3-003
sourceType=official-code
titleOrRepository=google-research/harness2
authorsOrMaintainer=Google Research (Harness² Authors)
venueAndYear=arXiv:2610.14224 (2026)
doiOrArxiv=arXiv:2610.14224
url=https://github.com/google-research/harness2
commitOrTag=1cfe254e8d25b15f37048c3730134686abe60335
license=Apache-2.0
filesOrSectionsRead=harness2/core/recursion.py (lines 1-350: Stage, ModeResult, _accept_tree, _run_stage, _roll, _roll_many)
verificationStatus=VERIFIED
relevantFinding=任务级递归遵循严格预算约束（$R=4k, P=3k$）；在候选护具验证阶段进行字符上限与语法有效性检查，失败后提供 1 次自纠重试机会。
projectApplicability=指导阶段三 $k=1$ 最小闭环的调度边界，确保单任务 Rollout 次数不超过预算硬限。
limitations=调度逻辑中包含大量针对跨任务全局经验库（library.py）的索引更新，阶段三离线小样本验证应予以剪裁以聚焦单变量。

id=RS-STAGE3-004
sourceType=official-code
titleOrRepository=google-research/harness2
authorsOrMaintainer=Google Research (Harness² Authors)
venueAndYear=arXiv:2610.14224 (2026)
doiOrArxiv=arXiv:2610.14224
url=https://github.com/google-research/harness2
commitOrTag=1cfe254e8d25b15f37048c3730134686abe60335
license=Apache-2.0
filesOrSectionsRead=harness2/core/context.py (lines 400-547: assert_no_grader_verdicts, assert_no_leak_paths, mission, GRADER_VERDICT_MARKERS)
verificationStatus=VERIFIED
relevantFinding=通过在上下文生成边界设置双重敏感词雷区（扫描文件名与文本内容中的 pass_rate、deterministic_eval 等词），坚决杜绝评分标准与答案直接进入模型 Prompt。
projectApplicability=直接移植为 Hermes 阶段三 `assertZeroDataLeakage()` 门禁，确保盲审裁判与变异生成绝对无法获取 Ground Truth 或评分判定细则。
limitations=敏感词列表为静态预设，针对企业业务指标需根据实际场景做语义扩展。

id=RS-STAGE3-005
sourceType=production-implementation
titleOrRepository=tech.qiantong.qknow:qknow-hermes-core
authorsOrMaintainer=Hermes Architecture Core Team
venueAndYear=Hermes Core Implementation (2026)
doiOrArxiv=N/A
url=file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core
commitOrTag=26c913e5 (feat/hermes-harness-stage2)
license=Proprietary
filesOrSectionsRead=HarnessEvidenceRecord.java, HarnessEvidenceCollector.java, HermesHarnessVoucher.java, AgentOrchestrator.java (lines 470-520, 1470-1510)
verificationStatus=VERIFIED
relevantFinding=阶段二已成功实现不可变执行摘要证据凭单与非阻塞内存收集器，调度器已支持请求级凭单注册（registerRequestVoucher）与退出点自动证据锁定。
projectApplicability=构成了阶段三离线探针执行与物理差异比对的坚实基石，无需侵入性修改 AgentOrchestrator 生产主干路径。
limitations=缺少自动化离线基准测试套件与双向盲审仲裁器。
```

---

# 第五部分：验证命令与精确测试计数

### 1. 编译隔离环境与无污染构建命令
后端全量模块统一且**唯一使用 Java 21** 编译与运行，严格通过局部前缀传入 SDKMAN 环境，绝不污染系统全局 Java 17：

```bash
# 1. 验证 Proto 独立生成无竞态
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean compile -pl backend/qknow-hermes/qknow-hermes-proto -am

# 2. 运行阶段三离线闭环契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=HermesHarnessStage3OfflineLoopTest -pl backend/qknow-hermes/qknow-hermes-core
```

### 2. 阶段三验收契约测试用例集设计（精确 7 项测试）

在实施阶段，拟在 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/` 落地 `HermesHarnessStage3OfflineLoopTest`，精确包含 7 个单测方法：

1. `testBenchmarkSuiteIntegrity()`：验证 5 个代表性离线测试用例的不可变性、非空性与预期工具定义完整性；
2. `testProposerMutationSafety()`：验证 Proposer 变异器生成的候选 System Prompt 绝不包含动态占位符，且字符数满足非膨胀门禁；
3. `testProbeExecutionDiffExtraction()`：模拟探针执行，验证 `EvidenceDiffExtractor` 准确提取 $\Delta \text{Tokens}$、$\Delta \text{Duration}$ 与工具匹配标记；
4. `testBudgetGateEnforcement()`：构造正向（超预算）与负向（省预算）样本，验证 $\Delta \text{Tokens} \le 0$ 与 $\Delta \text{Duration} \le 5\text{ms}$ 的硬门禁拦截率 100%；
5. `testDualBlindSelectionPositionConsistency()`：验证双向盲审在 Pass 1 与 Pass 2 对调位置时的一致性校验逻辑，验证偏见时安全回退 Baseline；
6. `testZeroDataLeakageTripwire()`：向评选上下文注入敏感词（如 `passed_count`），验证雷区扫描机制 100% 触发异常拦截；
7. `testOfflineEvidenceDeterministicReplay()`：将采集的 5 份执行证据序列化并回放，通过 `verifyDigest()` 常量时间校验，验证通过率 100%。

---

### 建议与交付说明
请主智能体（parent agent）审阅本法医审计报告，并将其完整写入指定文件：
`/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-B.md`。
本报告已完成对 Harness² 源码的逐行解构，精确指出可吸收与必须拒绝的机制，并为阶段三制定了包含 5 大不可变组件契约、双向盲审与确定性回放验证的 Java 21 落地规范。
