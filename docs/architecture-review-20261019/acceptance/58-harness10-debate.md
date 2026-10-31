> **SUPERSEDED / DO NOT USE FOR IMPLEMENTATION**：本文 Stage 9 HISTORY/COMPOSITE 的 `INVALID_FIXTURE` 与“非 Agent 缺陷”表述仅为早期假设，未获反事实验证，不构成已确认归因。本文中的业务 Domain 映射也未核验；以 `59-harness10-taskbook.md` Revision 2 为准：失败记录为 `OBSERVED` + `failureObserved=true` + `attributionStatus=UNRESOLVED`，Stage 9 回放统一进入 `UNSCOPED_REPLAY` 且不参与正向检索。

# 【多智能体交叉辩论与终局裁决书】阶段十：Per-Domain 经验库与跨任务经验复用

> **【辩论与存证元数据】**
> - **辩论发起方与主裁决员**：主智能体 (Parent Agent: `e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`)
> - **理论推导正方**：智能体 A：经验库理论审计员 (`cd6b77c4-d965-4b69-8c6a-9a1e0eaf4800`) — [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/cd6b77c4-d965-4b69-8c6a-9a1e0eaf4800/.system_generated/logs/transcript.jsonl)
> - **工程实现反方**：智能体 B：Java 经验库工程审计员 (`8f9bee90-de38-499f-aae9-4e1a61bf3d83`) — [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/8f9bee90-de38-499f-aae9-4e1a61bf3d83/.system_generated/logs/transcript.jsonl)
> - **实验合规监察方**：智能体 C：实验与泄漏审计员 (`785fa860-1119-4846-a8c9-a2af2bbbad86`) — [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/785fa860-1119-4846-a8c9-a2af2bbbad86/.system_generated/logs/transcript.jsonl)
> - **裁决时间**：2026-10-31
> - **辩论主题**：Stage 10 Per-Domain Experience Library 架构与治理方案的理论完备性、工程落地点与零泄漏硬隔离。

---

## 一、辩论焦点一：经验条目数据模型与命名规范

### 1.1 各方论点陈述
- **智能体 A（理论审计员）**：
  主张严格对标 ESWA/TKDE 数学形式化，将经验条目建模为不可变 8 元组 $\mathbf{e} = \langle d, c, l, \epsilon, \gamma, \tau, \phi, \delta \rangle$，包含领域标识符 $d$、检索线索 $c$（含千问 1024 维超球面向量）、因果教训 $l$、物理证据引用 $\epsilon$、动态置信度 $\gamma \in [0.0, 1.0]$、来源流水号 $\tau$、Harness 指纹 $\phi$ 与处置状态 $\delta$。类名建议为 `DomainExperienceEntry`。
- **智能体 B（工程审计员）**：
  主张契约实用性与 Java 21 工业级高内聚，设计了 8 个核心类。类名建议使用更精炼的 `ExperienceEntry`，通过嵌套 `ExperienceDomain`、`ExperienceDisposition` 枚举，并增加版本号 `version` 与软淘汰标识 `retired`/`retirementReason`。
- **智能体 C（实验审计员）**：
  要求条目必须具备强类型处置状态 `EntryDisposition`，必须显式区分 `ADOPTED`、`REJECTED`、`INVALID_FIXTURE` 与 `OBSERVED`，且要求在写入时严格断言 `harnessFingerprint` 为 64 位 SHA-256 十六进制字符串。

### 1.2 主裁决裁定
**【统一裁决】**：采纳智能体 B 的命名结构与智能体 A 的形式化 8 元组内核，融合为 Java 21 不可变 Record：
- 包路径：`tech.qiantong.qknow.hermes.agent.harness.experience`
- 类名：`ExperienceEntry`
- 结构字段：
  ```java
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
      int version,
      boolean retired,
      String retirementReason,
      Instant createdAt
  )
  ```
- 严格保证 $\gamma \in [0.0, 1.0]$，`harnessFingerprint` 为 64 字符十六进制，`version` 单调自增，字段全量不可变。

---

## 二、辩论焦点二：淘汰机制（物理硬删除 vs 软标记墓碑与冷备）

### 1.1 各方论点陈述
- **智能体 B（开源代码法医视角）**：
  指出 Google Harness² 官方开源代码 `library.py` 的 `forget(task_ids)` 与 `retain(task_ids)` 采用了物理硬删除 `shutil.rmtree(entry_dir)`。提问：是否为了追求与上游 Harness² 行为一致而采用文件删除？
- **智能体 A（理论推导视角）**：
  强烈反对物理硬删除。引用定理 3 证明：物理硬删除会导致历史 PPC 运行凭单（Voucher）的依赖哈希链断裂，产生悬空指针，直接摧毁顶刊论文法医级实验的可复现性与 Merkle 审计链不变量。
- **智能体 C（合规监察视角）**：
  严正指出本项目《AGENTS.md》第七章第 5 条具有最高效力——“严禁发生不可逆的物理硬删除，凡涉及淘汰必须强制实现软删除隔离与三级冷备”。

### 1.2 主裁决裁定
**【统一裁决】**：**彻底否决上游 Harness² 的物理硬删除实现，全票采纳智能体 A 与 C 的软淘汰原则**！
- 在 `ExperienceWriter.retire(domain, entryId, reason)` 中，绝对不调用 `Files.delete()` 删除经验条目目录；
- 通过原子更新（`ATOMIC_MOVE`）将条目替换为 `retired = true`, `retirementReason = reason` 的新版本；
- 在 `EXPERIENCE.md` 首部显式追加 `> [!WARNING] RETIRED` 注记；
- 在 `INDEX.md` 中将该条目归入 `## Retired Entries` 独立段落；
- 检索检索器（`ExperienceRecall`）在面向 Proposer 时默认过滤所有 `retired == true` 的条目，但磁盘文件与凭据哈希永久留存供法医审计。

---

## 三、辩论焦点三：Stage 9 真实产物导入与 Disposition 映射规则

### 1.1 各方论点陈述
- **智能体 C（实验审计员）**：
  针对 Stage 9 的 15 次探针执行与 5 个用例实证数据，提出严格映射规则：
  - `baseline` 通过的 3 个任务 (`kb-qa-01`, `tool-route-02`, `param-guard-03`) $\implies$ `OBSERVED`；
  - `candidate-a`（过度精简导致补全 Token 反弹 17%）与 `candidate-b`（收紧步数未改善质量） $\implies$ `REJECTED`；
  - `ctx-history-04`（长思考链耗尽 1024 补全 Token）与 `comp-rag-tool-05`（缺 `clusterId` 参数未调用工具） $\implies$ `INVALID_FIXTURE`；
  - 严禁生成任何 `ADOPTED` 条目，因为 Stage 9 最终采纳为 0（`REJECTED_AFTER_REAL_PPC`）。
- **智能体 A（理论审计员）**：
  完全支持。指出将 `ctx-history-04` 和 `comp-rag-tool-05` 标记为 `INVALID_FIXTURE` 符合因果归因原则：失败是由外部评测环境（Token 预算上限、入参缺失）引起的，而非 Agent 内在认知缺陷。若错误标记为能力缺陷，会诱导模型做出错误的漂移变异。
- **智能体 B（工程审计员）**：
  同意，并在导入器契约中增加刚性断言：若导入数据中出现 `ADOPTED` 状态，或将未通过任务标为 `SUCCESS`，立即抛出 `IllegalStateException` 终止构建。

### 1.2 主裁决裁定
**【统一裁决】**：全票采纳智能体 C 的 Stage 9 映射矩阵，将其作为 Stage 10 导入测试与初始经验资产的唯一刚性契约。

---

## 四、辩论焦点四：`passedCases=5` 语义误导纠正方案

### 1.1 各方论点陈述
- **智能体 C（实验审计员）**：
  历史摘要中写 `passedCases=5` 容易被误解为 5 个任务质量全部通过，而实际上真实探针是 15 次，质量断言通过仅为 3 次。必须纠正这一误导，但不能修改已冻结的 Stage 9 历史 JSON 资产。
- **智能体 B（工程审计员）**：
  建议在 Stage 10 的 `ExperienceRunSummaryRecord` 中废弃单一的 `passedCases`，拆解为 `totalProbesExecuted=15`、`qualityPassedCases=3`、`invalidFixtureCases=2`、`evaluatedCandidates=3`、`adoptedCandidates=0`。
- **智能体 A（理论审计员）**：
  指出遗留接口向后兼容问题：如果有老测试或老代码调用 `passedCases()`，该方法应返回 `qualityPassedCases`（即 3），并标记 `@Deprecated`，严禁返回 5。

### 1.2 主裁决裁定
**【统一裁决】**：
- 严格保持 `experiment/outputs/harness9-live/` 历史只读与哈希冻结；
- 在 Stage 10 统一采用显式多维结构 `ExperienceRunSummaryRecord`；
- 遗留适配方法强制返回真实质量通过数 3，彻底消除“全绿通过”幻觉。

---

## 五、辩论焦点五：零 Qrel / 零 Judge 判词泄漏门禁与泛化保障

### 1.1 各方论点陈述
- **智能体 A（理论审计员）**：
  根据泛化误差界定理（定理 4），如果经验条目中充斥着具体的测试用例实体字面量（如 `TX-20261029-8891`、`25:99:99`），会导致假说空间 VC 维爆炸，产生严重的测试集过拟合；经验条目必须抽象为因果元知识。
- **智能体 C（实验审计员）**：
  必须在代码层设置硬拦截器 `ExperienceLeakageGuard.assertNoQrelOrGraderLeakage()`，对 `GRADER_VERDICT_MARKERS` 与 `KNOWN_QREL_MARKERS` 执行逐字符检查，发现违规立即抛出异常拒绝写盘。
- **智能体 B（工程审计员）**：
  确认该检查将作为 `ExperienceWriter` 每次写盘前的第一前置拦截步骤，并对 `INDEX.md`、`ENTRY.json`、`EXPERIENCE.md` 全量文本生效。

### 1.2 主裁决裁定
**【统一裁决】**：全票采纳智能体 A 的理论约束与智能体 C 的工程拦截实现，作为 Stage 10 经验写入的第一道刚性安全门禁。

---

## 六、辩论焦点六：经验库单向可见性与生产零变异门禁（No Auto-Mutation Gate）

### 1.1 各方论点陈述
- **智能体 C（实验审计员）**：
  经验库必须设立物理气隙（Physical Air-Gap），严禁任何向当前在线生产凭单（`HermesHarnessVoucher`）的反向动态热加载、定时器轮询或事件广播。
- **智能体 B（工程审计员）**：
  经验库门面 `ExperienceLibrary` 仅提供 `recallForProposer(domain, cue)` 供离线提议器在探索阶段使用；生产在线执行链路（`AgentOrchestrator.chat`）绝对不引用该接口，杜绝运行时 SystemPrompt 污染。
- **智能体 A（理论审计员）**：
  从控制论稳定性角度指出，闭环系统若存在未经形式化验证的自演化回路，极易发生正反馈共振导致系统发散崩溃。离线 PPC 门禁是系统唯一的演化准入通道。

### 1.2 主裁决裁定
**【统一裁决】**：经验库被严格限定为离线只读资产（面向 Proposer），严禁与生产运行时建立任何自动刷新或自修改通道。

---

## 七、终局裁决汇总表

| 序号 | 治理与架构维度 | 裁决方案 | 约束效力 |
| :--- | :--- | :--- | :--- |
| **1** | **数据模型** | `ExperienceEntry` 不可变 Record，包含 8 元组及软淘汰标识与版本号 | 强制 |
| **2** | **物理隔离** | `domainSlug` 路径物理隔离，目录树严格单领域自治 | 强制 |
| **3** | **淘汰策略** | 严禁物理硬删除，仅允许版本追加与 `isSoftRetired` 软标记 | 强制 |
| **4** | **Stage 9 映射** | `ADOPTED` 为空，2 个候选 `REJECTED`，2 个失效 `INVALID_FIXTURE` | 强制 |
| **5** | **指标纠偏** | 多维指标显式分离，彻底废弃 `passedCases=5` 歧义表达 | 强制 |
| **6** | **防泄漏门禁** | 写入前强制执行 `assertNoQrelOrGraderLeakage`，违者一票否决 | 强制 |
| **7** | **运行隔离** | 仅离线 Proposer 可查，生产在线运行时零感知、零变异 | 强制 |
| **8** | **测试保障** | 纯离线测试，零外部网络，前置构建 `qknow-hermes-proto` | 强制 |

---
*本辩论裁决书作为 Stage 10 任务书（59-harness10-taskbook.md）的直接依据，全量归档生效。*
