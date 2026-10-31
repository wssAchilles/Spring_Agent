# Hermes Harness² 阶段十（Stage 10）：Per-Domain Experience Library 与跨任务经验复用冻结任务书

- **文档编号**：`59-harness10-taskbook.md`
- **任务书状态**：`FROZEN`（修订后重新冻结，Revision 2）
- **基线分支与提交**：`feat/hermes-harness-stage9` (`HEAD: 3eb94e45`)
- **目标工作分支**：`feat/hermes-harness-stage10`（从 `feat/hermes-harness-stage9` 切出，严禁修改 `main`）
- **Java 编译与运行环境**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
- **生成模型与向量基线**：DeepSeek 官方活跃模型 `deepseek-flash`；阿里千问 `text-embedding-v4`（1024 维超球面单位向量空间 $\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$，$\|\mathbf{v}\|_2 = 1$）
- **生产默认配置**：`SINGLE` 模式；`KernelBridgeMode.OFF`；`hermes.orchestration.enabled=false`（经验库严格作为离线提议器只读资产，生产在线执行零动态注入、零自动变异）
- **物理派发三智能体审计存证**：
  - **Agent A（经验库理论审计员）**：[`cd6b77c4-d965-4b69-8c6a-9a1e0eaf4800`](conversation://cd6b77c4-d965-4b69-8c6a-9a1e0eaf4800) | [`58-harness10-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-agent-A.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/cd6b77c4-d965-4b69-8c6a-9a1e0eaf4800/.system_generated/logs/transcript.jsonl)
  - **Agent B（Java 经验库工程审计员）**：[`8f9bee90-de38-499f-aae9-4e1a61bf3d83`](conversation://8f9bee90-de38-499f-aae9-4e1a61bf3d83) | [`58-harness10-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-agent-B.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/8f9bee90-de38-499f-aae9-4e1a61bf3d83/.system_generated/logs/transcript.jsonl)
  - **Agent C（实验与泄漏审计员）**：[`785fa860-1119-4846-a8c9-a2af2bbbad86`](conversation://785fa860-1119-4846-a8c9-a2af2bbbad86) | [`58-harness10-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-agent-C.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/785fa860-1119-4846-a8c9-a2af2bbbad86/.system_generated/logs/transcript.jsonl)
- **前置审计、调研与辩论文档**：
  [`58-harness10-audit.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-audit.md)、
  [`58-harness10-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-research.ledger.json)、
  [`58-harness10-research.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-research.md)、
  [`58-harness10-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-debate.md)
- **正文哈希（`body_sha256`，从 `^## 一、` 起算至文件末尾）**：`ad2ac171e566ff9810dd522fa896ee8fcc3154e18efdd529f09e67c3fffc6bb2`
- **全文件哈希（`full_file_sha256`）**：见伴生文件 [`59-harness10-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/59-harness10-taskbook.md.sha256)

---

## 一、阶段定位、使命与硬性边界（Mission & Non-Goals）

### 1.1 阶段使命与学术定位
在 Stage 9 中，系统以真实 ¥0.06718974 完成了 15 次全量真实探针调用，诚实证实了负结果（`REJECTED_AFTER_REAL_PPC`，未盲目采纳恶化的候选）。其中 HISTORY 与 COMPOSITE 仅有失败观测，尚无排他归因。昂贵的真实模型试错应先沉淀为分级证据，再由后续反事实验证决定哪些结论可迁移复用。

**Stage 10 的核心使命**是：
在 `tech.qiantong.qknow.hermes.agent.harness.experience` 包下构建**完全对标顶刊论文（ESWA, IEEE TKDE, ACM TOIS）水准、基于商空间按构造物理隔离（Partition by Construction）的 Per-Domain 经验库与跨任务证据复用引擎**。将 Stage 9 真实负结果与后续 PPC 产物转化为不可变、强类型、零泄漏且标明归因状态的经验资产；只有经验证的结论才能在同领域离线提议时提供正向引导。

### 1.2 硬性排除边界（Non-Goals & Invariants）
为确保架构纯粹、合规可审计与生产安全，Stage 10 显式锁定以下铁律边界：
1. **绝不修改 `main` 分支**：所有实施代码必须在独立分支 `feat/hermes-harness-stage10` 上推进；
2. **生产在线零感知与零污染（No SystemPrompt Pollution）**：经验条目严禁在运行期动态拼接或直接注入到生产 `AgentOrchestrator` 的 SystemPrompt 中。经验库仅作为只读上下文，单向暴露给离线 Proposer（如 `AlgebraicHarnessMutator`）使用；
3. **零运行期自动变异（No Auto-Mutation Gate）**：严禁经验库建立任何监听器（`WatchService`）、定时器或异步事件向生产活跃凭单（`HermesHarnessVoucher`）反向写回；
4. **绝不执行物理硬删除（Append-Only & Soft-Retirement）**：坚决废弃上游开源 Harness² 的 `shutil.rmtree` 物理硬删除机制，严格遵循不可变软标记（`isSoftRetired`）与三级冷备存证，保障顶刊级密码学哈希审计链不中断；
5. **绝不重跑 Stage 9 付费 API**：Stage 10 为纯离线架构实现与测试，默认 `mvn test` 严禁调用真实外网 API，100% 依托 Stage 9 冻结的 15 份真实探针 JSON 资产进行离线回放导入验证；
6. **严禁把未验证归因固化为事实**：Stage 9 的 `ctx-history-04` 与 `comp-rag-tool-05` 仅证明质量失败及可观察原因，不足以排除 Agent、夹具、工具参数或能力装配问题。Stage 10 必须记录 `failureObserved=true`、`attributionStatus=UNRESOLVED`、`disposition=OBSERVED`；在完成反事实实验前，不得标定为 `INVALID_FIXTURE`、不得断言“绝非 Agent 缺陷”，也不得作为已验证的正向经验供 Proposer 使用。只有反事实验证确认夹具/工具参数/能力装配归因后，才允许升级为 `INVALID_FIXTURE`。

---

## 二、Per-Domain 形式化隔离与存储层级规范

### 2.1 领域的代数定义与 Slug 映射
业务领域被严格定义为三元组商空间；另有一个与业务领域不相交的保留回放域 `UNSCOPED_REPLAY`，只承载缺少可信业务映射的历史证据：
$$\mathcal{D}_{business} = \langle \text{botId}, \text{knowledgeBaseId}, \text{taskCategory} \rangle \in \mathbb{N}^{+} \times \mathbb{N}^{+} \times \Sigma^*, \quad \mathcal{D}_{replay} \cap \mathcal{D}_{business} = \varnothing$$

在 Java 21 中定义为不可变 Record：
```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

public record ExperienceDomain(
    long botId,
    long knowledgeBaseId,
    String taskCategory
) {
    public ExperienceDomain {
        boolean unscopedReplay = botId == 0 && knowledgeBaseId == 0
            && "__unscoped_replay__".equals(taskCategory);
        if (!unscopedReplay && (botId <= 0 || knowledgeBaseId <= 0)) {
            throw new IllegalArgumentException("Business domain identifiers must be positive");
        }
        if (taskCategory == null || taskCategory.isBlank()) {
            throw new IllegalArgumentException("Task category must not be blank");
        }
    }

    public static ExperienceDomain unscopedReplay() {
        return new ExperienceDomain(0, 0, "__unscoped_replay__");
    }

    public boolean isUnscopedReplay() {
        return botId == 0 && knowledgeBaseId == 0
            && "__unscoped_replay__".equals(taskCategory);
    }

    /**
     * 生成规范化物理存储目录 Slug
     * 规则：全部转小写，非字母数字下划线替换为单下划线
     * 格式示例：bot_101__kb_202__cat_finance
     */
    public String toSlug() {
        if (isUnscopedReplay()) return "unscoped_replay";
        String cleanCategory = taskCategory.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        return "bot_" + botId + "__kb_" + knowledgeBaseId + "__cat_" + cleanCategory;
    }
}
```

### 2.2 物理目录树布局规范（对标 Harness² `library.py`）
经验库物理存储根目录约定为项目根目录下的 `experiment/experience-library/`（在测试环境中支持通过构造器注入 `@TempDir` 隔离目录）。每个业务领域拥有物理隔离的独立子树；保留回放域使用固定、不可与正数业务 ID 冲突的 `unscoped_replay` slug：

```text
experiment/experience-library/
├── <businessDomainSlug>/
│   ├── INDEX.md
│   └── entries/<entryId>/
│       ├── ENTRY.json
│       ├── EXPERIENCE.md
│       └── versions/<version>/...
└── unscoped_replay/            # 保留隔离域，永不进入业务正向检索
    ├── INDEX.md
    └── entries/<entryId>/...
```

- **`domainSlug` 路径隔离铁律**：检索与写入严格限定在目标 `domainSlug` 目录内，任何方法严禁扫描或跨目录读取其他领域的文件，从代数构造上彻底消除异构领域伪语义负迁移（定理 1 与定理 2）。
- **回放域隔离铁律**：`ExperienceDomain.unscopedReplay()` 固定映射到 `unscoped_replay/`；业务 ID 必须为正数，因此该 slug 与业务目录构造性互斥。Stage 9 历史导入只能写入此保留域，`isEligibleForPositiveProposer` 对该域恒为 `false`。
- **`INDEX.md` 格式契约**：包含 `# Experience Index: <domainSlug>` 标题、更新时间戳、活跃条目清单（含 ID、处置状态、置信度、Cue 摘要）以及 `## Retired Entries` 软淘汰条目隔离清单。

---

## 三、不可变经验条目规范与原子落盘协议

### 3.1 核心不可变 Record 协议蓝图
经验条目严格映射形式化 8 元组 $\mathbf{e} = \langle d, c, l, \epsilon, \gamma, \tau, \phi, \delta \rangle$：

```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.time.Instant;

/**
 * 阶段十核心架构：不可变经验条目 Record
 */
public record ExperienceEntry(
    String entryId,
    ExperienceDomain domain,
    String cue,
    String lesson,
    String evidenceSummary,
    EvidenceReference evidenceRef,
    double confidence,
    String sourceRunId,
    String harnessFingerprint,
    ExperienceDisposition disposition,
    boolean failureObserved,
    AttributionStatus attributionStatus,
    int version,
    boolean retired,
    String retirementReason,
    Instant createdAt
) {
    public ExperienceEntry {
        if (entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("entryId must not be blank");
        }
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
        if (cue == null || cue.length() < 5) {
            throw new IllegalArgumentException("cue must have at least 5 characters");
        }
        if (lesson == null || lesson.length() < 10) {
            throw new IllegalArgumentException("lesson must have at least 10 characters");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be strictly bounded in [0.0, 1.0]");
        }
        if (harnessFingerprint == null || harnessFingerprint.length() != 64) {
            throw new IllegalArgumentException("harnessFingerprint must be a 64-char SHA-256 hex string");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("disposition must not be null");
        }
        if (attributionStatus == null) {
            throw new IllegalArgumentException("attributionStatus must not be null");
        }
        if (disposition == ExperienceDisposition.INVALID_FIXTURE
            && attributionStatus != AttributionStatus.FIXTURE_CONFIRMED) {
            throw new IllegalArgumentException("INVALID_FIXTURE requires confirmed attribution");
        }
    }

    public enum AttributionStatus {
        NOT_APPLICABLE,
        UNRESOLVED,
        FIXTURE_CONFIRMED,
        AGENT_CONFIRMED
    }

    /**
     * 判断是否已落入软淘汰判定边界
     */
    public boolean isSoftRetired(double retireThreshold) {
        return retired
            || confidence < retireThreshold
            || disposition == ExperienceDisposition.INVALID_FIXTURE
            || disposition == ExperienceDisposition.REJECTED;
    }

    /** 未完成反事实归因的失败只能作为观察记录，不能成为正向提议依据。 */
    public boolean isEligibleForPositiveProposer(double retireThreshold) {
        return !isSoftRetired(retireThreshold)
            && !failureObserved
            && !domain.isUnscopedReplay();
    }
}
```

伴生凭证引用与处置状态枚举：
```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

public record EvidenceReference(
    String evidenceId,
    String requestId,
    String evidenceSha256,
    String executionMode
) {}

public enum ExperienceDisposition {
    ADOPTED,         // 经真实 PPC 验证显著改进并被采纳的候选策略（Stage 9 此集合严格为空）
    OBSERVED,        // 已观察到的基线或失败事实；归因未决时不得作为正向经验
    REJECTED,        // 经实证验证劣于基线或未达采纳标准的候选（记录被拒机理，防止重复变异）
    INVALID_FIXTURE  // 仅限反事实确认的夹具/参数/能力装配问题
}
```

### 3.2 细粒度并发与原子落盘协议
为确保并发写入安全与系统崩溃零损坏，`FileSystemExperienceStore` 必须遵循：
1. **细粒度按领域加锁**：维护 `ConcurrentHashMap<String, ReentrantLock>`，写入时仅锁定当前操作的 `domainSlug`，实现异构领域完全无锁并发；
2. **同分区临时文件写入**：写入 `ENTRY.json` 与 `EXPERIENCE.md` 时，必须先在目标条目目录下创建带有随机 UUID 后缀的临时文件（如 `ENTRY.json.tmp_8a1f...`）；
3. **`ATOMIC_MOVE` 原语原子重命名**：使用 `Files.move(tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)` 完成替换，杜绝断电或进程中断导致的半写残损文件。
4. **覆盖前保全旧版本**：更新 `ENTRY.json` 或 `EXPERIENCE.md` 前，先将上一版本的两个文件完整写入不可变 `versions/<version>/`，并校验成功；存档失败则不得替换当前文件。`ATOMIC_MOVE` 只保证单文件原子性，不宣称两个当前文件及 `INDEX.md` 的跨文件事务原子性。

---

## 四、因果抽象元知识提取与零泄漏硬门禁

### 4.1 因果抽象与防止过拟合准则
根据定理 4（泛化误差界定理），进入经验库的 `cue` 与 `lesson` 必须是**可追溯的抽象经验或已验证的因果元知识**，并明确区分观察与归因；未决失败不得写成已验证的原因，亦不得退化为针对特定测试用例的具象文本记忆。

- **禁止项（具象表层记忆）**：
  - 严禁出现测试流水号（如 `TX-20261029-8891`）、具体越界时间（如 `25:99:99`）、测试账户号（如 `ACC-9901`）、特定金额（如 `5800.00`）或分块 ID（如 `chunk-pay-idem-01`）；
- **鼓励项（证据分级的抽象经验）**：
  - 提炼系统提示词精简策略的边际效益（例如：“过度精简约束提示词会导致长思维链推理冗余发散，反而使补全 Token 上升 17%”）；
  - 将长上下文多轮对话的输出预算列为待验证假设（本轮仅观察到 1024 补全配额耗尽与空答复同时出现）；
  - 将复合 RAG 与工具调用的阶段解耦列为待验证假设（本轮仅观察到 RAG 命中但计算工具未执行）。

### 4.2 零 Qrel 与零 Judge 判词硬拦截器（`ExperienceLeakageGuard`）
在 `DefaultExperienceLibrary.recordExperience` 执行任何落盘前，必须强制调用零泄漏硬拦截器。一旦发现敏感词，立即抛出 `ExperienceLeakageException` 拒绝写入；该异常作为 `ExperienceLeakageGuard.java` 中的包内辅助类型，不新增第 13 个 Java 文件：

```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Locale;

public final class ExperienceLeakageGuard {
    private static final List<String> GRADER_VERDICT_MARKERS = List.of(
        "deterministic_eval_summary", "deterministic_eval_result",
        "judge_scope_and_deterministic_status", "qualitypass",
        "\"passed_count\"", "\"pass_rate\"", "\"test_pass_rate\"",
        "scoring_rubric", "gold_rubric", "qrels.tsv", "ground_truth_answer",
        "expectedkeywords"
    );

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

---

## 五、离线 Proposer 检索接口与运行时零变异物理气隙

### 5.1 检索门面与安全结果封装
经验库对外的统一门面为 `tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary`。检索返回不可变结果 Record：

```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;

public record ExperienceRecall(
    ExperienceDomain domain,
    String queryCue,
    List<ExperienceEntry> matchedEntries,
    int totalAvailableInDomain,
    int filteredRetiredCount
) {
    public boolean hasRelevantExperience() {
        return matchedEntries != null && !matchedEntries.isEmpty();
    }
}
```

门面接口定义：
```java
public interface ExperienceLibrary {
    /**
     * 仅供离线 Proposer 调用：检索同领域未淘汰、置信度达标且未观察到失败的正向经验
     */
    ExperienceRecall recallForProposer(ExperienceDomain domain, String queryCue, int maxResults);

    /**
     * 记录新经验（写盘前执行零泄漏断言、原子移动与索引重建）
     */
    ExperienceEntry recordExperience(ExperienceEntry entry);

    /**
     * 软淘汰条目（版本升级、状态置换为 retired、打上注记，绝不物理删除）
     */
    boolean retire(ExperienceDomain domain, String entryId, String retirementReason);
}
```

### 5.2 物理气隙（Air-Gap Invariant）硬约束
1. **单向引用依赖**：
   `ExperienceLibrary` 只能被离线提议器（如测试套件、`AlgebraicHarnessMutator`）依赖；
   生产执行核心类 `tech.qiantong.qknow.hermes.agent.AgentOrchestrator` 与其下全部运行时组件，**严禁引入 `ExperienceLibrary` 或其子包的任何类**；
2. **凭单签名封签（Cryptographic Sealing）**：
   生产运行唯一的策略输入是不可变 Record `HermesHarnessVoucher`，且带密码学签名 `voucherFingerprint`。系统中严禁提供任何从经验库热加载生成新凭单的运行期后门。

---

## 六、置信度动态演化动力学与不可变软淘汰机制

### 6.1 非对称置信度更新动力学
置信度 $\gamma$ 的演化严格遵循非对称因果更新动力学（定义 2 与引理 1）：
- **强化算子（Upvote）**：当后续 PPC 任务复用该经验并证实正向收益时：
  $$\gamma_{t+1} = \min\left(1.0, \, \gamma_t + 0.15 \cdot \kappa_t \cdot (1 - \gamma_t) \cdot \sigma(\Delta s_t)\right)$$
- **削弱算子（Downvote）**：当后续任务出现因果归因的严重回退时：
  $$\gamma_{t+1} = \max\left(0.0, \, \gamma_t - 0.35 \cdot \kappa_t \cdot \gamma_t \cdot (1 + |\Delta s_t|)\right)$$
- **单调收敛性**：严重失败的惩罚速率（$\eta_{\text{down}}=0.35$）两倍于正向累积速率（$\eta_{\text{up}}=0.15$），确保有毒策略被快速隔离。

### 6.2 软淘汰（Soft-Retirement）标准工作流
当条目满足以下任一条件时，触发软淘汰并移出检索工作集：
$$\text{IsRetire}(e) \iff (\gamma(e) < 0.20) \lor (\text{disposition} = \text{INVALID\_FIXTURE}) \lor (\text{disposition} = \text{REJECTED})$$

`failureObserved=true` 且 `attributionStatus=UNRESOLVED` 的条目不会因观察到失败而自动升级为 `INVALID_FIXTURE`；它们可保留为审计记录，但必须被排除出 Proposer 的正向建议集合。

**执行步骤**：
1. 从磁盘读取原条目 `ENTRY.json`；
2. 构建 `retired = true`, `retirementReason = reason`, `version = version + 1` 的新不可变 `ExperienceEntry`；
3. 将包含 `> [!WARNING] RETIRED` 注记的新 `EXPERIENCE.md` 与新 `ENTRY.json` 原子替换写入；
4. 增量刷新 `INDEX.md`，将该条目挪入 `## Retired Entries` 段落；
5. **绝对保留物理资产**：当前文件、`versions/<version>/` 中的旧版 JSON/MD 及关联凭单均保留；不得把 `REPLACE_EXISTING` 当作历史版本保全。

---

## 七、Stage 9 真实负结果资产回放导入与多维指标纠偏

### 7.1 Stage 9 实证资产映射规约
Stage 10 提供离线回放导入组件 `tech.qiantong.qknow.hermes.agent.harness.experience.Stage9ExperienceImporter`，负责读取 `experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/` 的冻结数据，并严格按如下矩阵导入经验库：

| 资产实体 | 目标 Domain | 映射处置状态 | 证据分级 Lesson 摘要（严格脱敏测试实体） |
| :--- | :--- | :--- | :--- |
| **Baseline 稳定通过任务** | `UNSCOPED_REPLAY` | `OBSERVED` | 基线在 RAG、工具路由与纯提示词三类任务质量通过；baseline 五题共 11,864 tokens，每题平均 2,372.8 tokens。保留作审计，不进入业务 Domain 正向检索。 |
| **Candidate-A 变异产物** (`CONCISE_SYSTEM_PROMPT`) | `UNSCOPED_REPLAY` | `REJECTED` | 本轮提示词精简候选的总 Tokens 与费用高于 baseline，未达采纳标准；具体机制尚未完成排他归因。仅限回放隔离区。 |
| **Candidate-B 变异产物** (`GUARDRAIL_TIGHTENING`) | `UNSCOPED_REPLAY` | `REJECTED` | 本轮循环步数收紧候选虽略降成本与 Tokens，质量门禁仍未通过，不能采纳；具体机制尚未完成排他归因。仅限回放隔离区。 |
| **`ctx-history-04` 探针证据** (HISTORY) | `UNSCOPED_REPLAY` | `OBSERVED` | `failureObserved=true`、`attributionStatus=UNRESOLVED`：观察到思考模式下 `completionTokens=reasoningTokens=1,024` 且最终答复为空；当前证据不能排除输出预算、夹具设计或 Agent 能力因素，需后续反事实实验。仅限回放隔离区。 |
| **`comp-rag-tool-05` 探针证据** (COMPOSITE) | `UNSCOPED_REPLAY` | `OBSERVED` | `failureObserved=true`、`attributionStatus=UNRESOLVED`：观察到未执行 `arch_metrics_calculator`，模型指出缺少 `clusterId`；当前证据不能排除提示词、工具参数契约或能力装配因素，需后续反事实实验。仅限回放隔离区。 |

- **刚性准入断言**：导入逻辑中必须显式断言 `importedAdoptedCount == 0`，若发现任何状态被错误赋予 `ADOPTED`，立即抛出异常终止。
- **回放隔离域**：冻结 JSON 仅含 `botId=9`，缺少可信 `knowledgeBaseId` 与 `taskCategory`。Stage 9 资产导入统一写入 `UNSCOPED_REPLAY` 保留域，保留证据中的 `botId=9` 作为 provenance，不构造或猜测业务 Domain。该保留 slug 与正数业务 ID 不冲突，且此隔离域所有条目（包括 baseline 通过项）均不得进入业务 Domain 的 Proposer 正向检索；真实业务域导入必须等可信映射另行提供。
- **归因升级门禁**：Stage 9 回放导入不得自行把两项未决失败升级为 `INVALID_FIXTURE`。后续须预先冻结并执行能区分夹具/工具参数/能力装配与 Agent 缺陷的反事实实验，保留结果引用后，方可将 `attributionStatus` 升为 `FIXTURE_CONFIRMED` 并改变 `disposition`；原 Stage 9 观测记录保持可追溯。
- **证据优先级**：本修订任务书以 Stage 9 冻结报告和原始探针为事实依据；58 系列预研/辩论文档中的早期夹具归因已标为 SUPERSEDED，仅为未验证假设，不得覆盖本节导入状态。

### 7.2 多维指标结构与质量通过语义
Stage 10 新建独立的 `ExperienceRunSummaryRecord`，明确区分质量通过和归因未决。现有 `OfflineProbeRunner.FullPpcRunSummary.passedCases` 属于 Stage 4/5 离线统计，不修改其类型或语义，也不冒称新 Record 与其二进制/API 兼容：

```java
package tech.qiantong.qknow.hermes.agent.harness.experience;

public record ExperienceRunSummaryRecord(
    String runId,
    int totalProbesExecuted,       // 真实探针执行总数：15
    int totalModelCallsExecuted,    // 真实模型调用总数：26
    long totalTokensConsumed,       // 消耗 Token 总数：35,500
    double totalCostCnyConsumed,    // 真实费用总额：0.06718974
    int uniqueCasesEvaluated,       // 评估的基准用例总数：5
    int qualityPassedCases,         // 真实质量断言通过用例数：3
    int unresolvedAttributionCases, // 观察到失败但尚未完成反事实归因的用例数：2
    int evaluatedCandidates,        // 参与评估的候选数：3 (baseline, a, b)
    int rejectedCandidates,         // 被真实 PPC 拒绝的候选数：2
    int adoptedCandidates,          // 最终被采纳的候选数：0
    String finalVerdict             // 最终判定："REJECTED_AFTER_REAL_PPC"
) {}
```

---

## 八、核心主干类实现蓝图与离线契约验证矩阵

### 8.1 核心主干 8 个功能模块、12 个 Java 文件落点设计 (`src/main/java`)
在 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/experience/` 下新增 8 个功能模块、12 个 Java 文件；模块与文件逐项对应如下：

1. **领域标识模块**：`ExperienceDomain.java`；
2. **处置状态模块**：`ExperienceDisposition.java`；
3. **证据引用模块**：`EvidenceReference.java`；
4. **不可变条目模块**：`ExperienceEntry.java`（含嵌套 `AttributionStatus`）；
5. **泄漏拦截模块**：`ExperienceLeakageGuard.java`；
6. **检索结果模块**：`ExperienceRecall.java`；
7. **持久化模块**：`ExperienceStore.java`、`FileSystemExperienceStore.java`；
8. **门面与回放统计模块**：`ExperienceLibrary.java`、`DefaultExperienceLibrary.java`、`Stage9ExperienceImporter.java`、`ExperienceRunSummaryRecord.java`。

以上 12 个 Java 文件的真实路径均为：
`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/experience/<文件名>`。
测试文件单列，不计入上述 12 个 Java 文件：`ExperienceLibraryContractTest.java`、`HarnessExperienceStage10Test.java`，路径为对应模块的 `src/test/java/tech/qiantong/qknow/hermes/agent/harness/experience/`。

### 8.2 契约单元测试矩阵 (`src/test/java`)
在 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/experience/` 下编写两套完整的契约测试：

1. **`ExperienceLibraryContractTest.java`**（契约与安全性测试）：
   - 验证 `domainSlug` 路径隔离合规性与非字母字符过滤；
   - 验证原子写入与文件内容一致性（`ENTRY.json`、`EXPERIENCE.md` 与 `INDEX.md`）；
   - 验证敏感词拦截：注入 `qualityPass` 或 `TX-20261029-8891` 时，100% 触发 `ExperienceLeakageException` 拒绝落盘；
   - 验证软淘汰（`retire`）：确认更新为 `retired = true`，Markdown 首部增加 WARNING 注记，物理文件与目录绝对不被删除；
   - 验证检索过滤：确认 Proposer 正向检索默认排除已软淘汰、`failureObserved=true` 和 `UNSCOPED_REPLAY` 域的条目；隔离回放条目仍可作为审计记录读取。
2. **`HarnessExperienceStage10Test.java`**（集成回放与治理测试）：
   - 读取 Stage 9 真实回放数据，验证状态机映射：`baseline` 3 题 $\implies$ `OBSERVED`，`candidate-a/b` $\implies$ `REJECTED`，HISTORY/COMPOSITE 2 题 $\implies$ `OBSERVED` + `failureObserved=true` + `attributionStatus=UNRESOLVED`；所有条目进入 `UNSCOPED_REPLAY`，`INVALID_FIXTURE` 计数为 0，且 Proposer 正向检索结果为空；
   - 断言 `ADOPTED` 条目数为 0；
   - 验证多维指标结构：断言 `qualityPassedCases=3`、`unresolvedAttributionCases=2`，不修改旧 `FullPpcRunSummary.passedCases`；
   - 验证未决失败不会进入正向 Proposer 建议集合，并确认经验库写入与检索完全不触发 `HermesKernelBridge` 或生产凭单变异。

### 8.3 门禁准入执行指令
由于项目根 POM 依赖 Protobuf 自动生成代码，在执行全量测试前必须先编译 proto 模块：

```bash
# 步骤 1：先单独编译 proto 模块生成依赖代码
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/qknow-hermes/qknow-hermes-proto/pom.xml clean compile

# 步骤 2：执行 Stage 10 经验库定向契约测试（目标耗时 < 3 秒，100% 离线绿色通过）
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/pom.xml test \
  -Dtest=ExperienceLibraryContractTest,HarnessExperienceStage10Test

# 步骤 3：全仓默认回归测试（确保 100% 零外网，2160+ 测试全部绿色通过）
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/pom.xml test
```

---
*本任务书经三方沙盒智能体法医级交叉审计、数学推导与主裁决确认，哈希锁定后作为 Stage 10 唯一实施契约。*
