> **SUPERSEDED / DO NOT USE FOR IMPLEMENTATION**：本文 Stage 9 HISTORY/COMPOSITE 的 `INVALID_FIXTURE` 与“非 Agent 缺陷”表述仅为早期假设，未获反事实验证，不构成已确认归因。本文中的业务 Domain 映射也未核验；以 `59-harness10-taskbook.md` Revision 2 为准：失败记录为 `OBSERVED` + `failureObserved=true` + `attributionStatus=UNRESOLVED`，Stage 9 回放统一进入 `UNSCOPED_REPLAY` 且不参与正向检索。

# Hermes Harness² 阶段十（Stage 10）：归档语义修正与只读源码法医审计报告

- **文档编号**：`58-harness10-audit.md`
- **所属阶段**：Stage 10 — 阶段 0（归档与语义修正）与阶段 1（只读源码法医审计）
- **审计基线分支**：`feat/hermes-harness-stage9`（HEAD：`3eb94e45`，工作区 `clean`）
- **核心关注点**：Stage 9 真实负结果归档、`passedCases` 语义纠正、Per-Domain 经验库理论与工程边界

---

## 一、阶段 0：Stage 9 结果归档与语义误导修正

### 1.1 Stage 9 真实实证结果客观归档（不修改历史证据）
Stage 9 的真实运行结果已在 `experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/` 下完成原子写盘并通过 17/17 SHA-256 校验和核验。Stage 10 **严格保留 Stage 9 原始物理证据，不修改任何已冻结的历史 JSON 文件**。

真实运行事实如下：
- **执行模式**：`REAL_PPC`（真实调用 DeepSeek `deepseek-flash` 官方端点，无测试替身）；
- **执行总量**：15 probes，26 次底层模型调用，35,500 tokens，实际总费用 **¥0.06718974**（未超出 ¥10.00 硬上限）；
- **最终结论**：**`REJECTED_AFTER_REAL_PPC`**（0 个候选被采纳，保留 `baseline`）；
- **质量真实通过数**：**3/5**（并非全部通过）。
  - `kb-qa-01` (`RAG`)：**质量通过**（真实调用 `knowledgeBase_MiddleWare_Spec`，包含 3 次重试与 SHA-256 幂等键）；
  - `tool-route-02` (`TOOL`)：**质量通过**（真实调用 `banking_transaction_query`，准确返回扣款成功与账户 `ACC-9901`）；
  - `param-guard-03` (`PROMPT_ONLY`)：**质量通过**（无外部工具，准确识别时间格式数值越界 `25:99:99`）；
  - `ctx-history-04` (`HISTORY`)：**质量失败**（1,024 输出 Token 被 DeepSeek 长思维链占满，最终回答为空）；
  - `comp-rag-tool-05` (`COMPOSITE`)：**质量失败**（模型指出工具 `arch_metrics_calculator` 缺少 `clusterId` 入参而拒绝盲目调用，未触发指定工具）。

### 1.2 `passedCases` 语义误导纠正方案
- **历史语义误区**：在 Stage 9 的 `ppc_summary.json` 中，存在字段统计容易被误解为“5 个任务质量全部通过”。但根据前述法医证据，实际质量通过率仅为 **3/5**。
- **Stage 10 修正方案**：
  1. **历史数据不动**：保持 Stage 9 已生成的 `ppc_summary.json` 物理文件不变；
  2. **Stage 10 显式重命名与扩充字段**：在 Stage 10 代码与契约中，明确将实验汇总指标细化为互斥且完备的 4 个统计字段：
     - `probesExecuted = 15`（实际物理执行探针总数）
     - `qualityPassedCases = 3`（离线质量门槛真正通过的任务数）
     - `invalidFixtureCases = 2`（因测试夹具或参数契约设计不完善导致的失效用例数）
     - `rejectedCandidates = 2`（因未超越基线而被拒绝的候选数：`candidate-a`, `candidate-b`）
  3. **失效夹具定性**：`ctx-history-04` 与 `comp-rag-tool-05` 被严格定性为 **`INVALID_FIXTURE`**，**严禁将其作为有效正样本导入经验库**。

---

## 二、阶段 1：只读源码法医审计（七大核心必答题）

结合对 `PpcOrchestrator`、`PpcComposer`、`HarnessEvidenceRecord`、`AlgebraicHarnessMutator`、上游 Harness² `library.py` 以及当前 Bot/KB 结构的法医审计，对 Stage 10 经验库的核心机制给出逐项解答：

### 问题 1：一个 domain 如何定义？
- **源码与架构依据**：在 Hermes 智能体框架中，智能体的行为表现高度耦合于其应用定位（`botId`）、挂载的领域知识库（`knowledgeBaseId`）以及执行的任务形态（`taskCategory`）。
- **形式化定义**：一个领域 $d$ 由三元组严格唯一确定：
  $$d = \langle \text{botId},\; \text{knowledgeBaseId},\; \text{taskCategory} \rangle$$
  - `botId`：长整型（如 `9L`，标识智能体专属应用类型）；
  - `knowledgeBaseId`：字符串（如 `"_MiddleWare_Spec"`、`"arch-standard"`、`"NONE"`，标识挂载的知识资产）；
  - `taskCategory`：强类型枚举（如 `KNOWLEDGE_QA`、`MULTI_TOOL_SELECTION`、`TOOL_PARAM_GUARD`、`CONTEXT_HISTORY_RECALL`、`COMPOSITE_REASONING`）。
- **目录 Slug 映射**：遵循 Harness² `library.py#L50-L55` 的 `domain_dir` 规范，将领域三元组编码为无斜杠、无空格的安全目录名：
  $$\text{slug}(d) = \texttt{"bot-"} + \text{botId} + \texttt{"__kb-"} + \text{sanitize}(\text{knowledgeBaseId}) + \texttt{"__cat-"} + \text{taskCategory}$$
  例如：`bot-9__kb-MiddleWare_Spec__cat-KNOWLEDGE_QA`。

### 问题 2：哪些 Stage 9 结果可以进入经验库？
- **准入准则**：仅有真实发生的物理观测与受控对比结论方可入库，且必须打上客观的处置状态标签（`ExperienceDisposition`）：
  1. **`baseline` 的三项通过记录 $\implies$ `OBSERVED`（可信基线事实）**：
     - `kb-qa-01`：在中间件规范问答域，详细系统提示词能稳定触发知识库工具检索并遵循 3 次退避与幂等键规范；
     - `tool-route-02`：在金融查询域，基线配置能准确从多工具中路由 `banking_transaction_query` 并核实账户状态；
     - `param-guard-03`：在参数校验域，基线配置能依据公理直接识别非法时间并拒止。
  2. **`candidate-a` 的负向实证 $\implies$ `REJECTED`（负向经验）**：
     - 记录其核心教训：过度精简系统提示词（`CONCISE_SYSTEM_PROMPT`）未能降低总 Token，反而诱发了模型在输出阶段的代偿性展开（`completionTokens` 从 4510 增至 5276），总成本增加 13.2%，且在 5 个任务上均未取得质量优势。
  3. **`candidate-b` 的负向实证 $\implies$ `REJECTED`（负向经验）**：
     - 记录其核心教训：将最大循环步数从 3 收紧至 2（`GUARDRAIL_TIGHTENING`）虽然使 Token 略微下降 6.7%，但在复杂多工具或带前置条件的任务中无法提升质量，且未能挽救失效夹具，未达帕累托采纳门槛。

### 问题 3：哪些必须标 `INVALID_FIXTURE`？
- **必须标记为 `INVALID_FIXTURE` 的用例**：
  1. **`ctx-history-04`（`CONTEXT_HISTORY_RECALL`）**：
     - **失效根因**：单次模型调用硬限 `maxOutputTokens = 1024`，而 `deepseek-flash` 开启深度推理时思维链（`reasoningTokens`）消耗了全部 1024 Token，导致最终面向用户的回答字段被强行截断为空（`finalOutputSummary == ""`）。这是**输出 Token 预算与长思维链模型特性的冲突导致的夹具失效**，并非 Harness 凭单策略劣化。
  2. **`comp-rag-tool-05`（`COMPOSITE_REASONING`）**：
     - **失效根因**：用户问题要求调用 `arch_metrics_calculator` 查询集群指标，但测试输入未在 `question` 或 `inputParams` 中提供工具必需的 `clusterId` 参数；模型严谨指出了“缺少 clusterId 前置条件，无法盲目执行工具”。这是**测试夹具参数契约不闭合导致的夹具失效**。
- **隔离规则**：标记为 `INVALID_FIXTURE` 的条目**严禁作为经验建议推荐给 Proposer**，仅作为测试套件工程改进的审计档案留存。

### 问题 4：经验如何被 Proposer 读取，而不污染 SystemPrompt？
- **物理单向流动架构**：
  - **离线阶段（Offline Evolution）**：Proposer（如 `AlgebraicHarnessMutator`）在生成候选变异体前，调用 `ExperienceLibrary.recallForProposer(domain, cue)`。Proposer 将经验作为变异搜索树的剪枝条件（例如：检索到该领域曾发生“精简提示词导致 Token 反弹”，则 Proposer 自动跳过 `CONCISE_SYSTEM_PROMPT` 算子，转向尝试调整循环步数或注入工具特定前置约束）。
  - **在线阶段（Online Serving / Runtime）**：`AgentOrchestrator` 执行时，系统提示词严格来源于不可变的 `HermesHarnessVoucher.invariantSystemPrompt()`。**运行时绝对不调用经验库，绝对不动态拼接任何经验文本到 SystemPrompt 中**，确保生产环境提示词的确定性与密码学防注入安全。

### 问题 5：如何避免跨 domain 污染？
- **隔离机制（Isolation by Construction）**：
  - 经验库采用**物理目录与领域命名空间强绑定**设计：每个领域独立的目录结构 `<libraryRoot>/<domainSlug>/`。
  - 检索方法 `recallForProposer(ExperienceDomain domain, String cue)` 在执行时，根路径直接锁定到目标 `domain.slug()` 目录；**底层代码不存在任何跨目录遍历或跨领域全局检索逻辑**。
  - 金融交易域的工具调用教训绝不可能流入知识库问答域，微服务架构评估域的经验绝不会干扰日程时间校验域。

### 问题 6：经验如何淘汰？
- **不可变软淘汰机制（Soft Retirement）**：
  - 严禁对磁盘上的经验条目执行 `rm` 或 `delete` 操作（硬删除会破坏实验审计留痕与三级冷备可追溯性）；
  - 当某条经验满足以下任一淘汰条件时，调用 `ExperienceWriter.retire(domain, entryId, reason)`：
    1. **置信度衰减**：经验条目的置信度 $\gamma$ 衰减至阈值以下（$\gamma < 0.3$）；
    2. **实证反驳/冲突**：后续更新的 PPC 真实实验产物与历史经验直接矛盾；
    3. **版本过期**：底层模型大版本升级导致旧提示词经验失效。
  - 淘汰处理方式：在条目元数据中更新 `retired = true`、记录 `retiredAt` 与 `retireReason`，并调用 `rebuildIndex()` 将其移入 `INDEX.md` 的 `## Retired Entries` 审计区块，检索器在默认情况下自动过滤所有 `retired == true` 的条目。

### 问题 7：如何防止 qrel/judge 泄漏？
- **零泄漏前置断言与信息防火墙**：
  - 参考上游 Harness² `context.py#assert_no_grader_verdicts`，在 `ExperienceWriter` 写入经验条目前强制执行 `assertNoQrelOrGraderLeakage(ExperienceEntry entry)`：
    1. **禁止 qrel 黄金答案词**：条目的 `cue` 和 `lesson` 严禁包含测试集的专有字面量（如流水号 `"TX-20261029-8891"`、非法时间 `"25:99:99"`、切片 ID `"chunk-pay-idem-01"`）；
    2. **禁止评分器判词**：严禁出现 `"qualityPass"`、`"deterministic_eval"`、`"gold_rubric"`、`"qrel"` 等判词标记；
    3. **抽象化提炼**：入库教训必须是对行为模式的高阶抽象（如“在时间参数校验中，当输入包含越界数值时，提示词应明确指示直接拒止而非尝试工具容错”）。命中任何黑名单或违规关键词立即抛出 `IllegalStateException` 拒绝写入。
