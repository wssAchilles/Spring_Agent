# Hermes Harness Stage 11B-2A 真实受控经验获取任务书

- 状态：`REVISION 1 - PRE-LIVE CONTRACT FROZEN`；11B-2A 实施与 live 仍须满足本书前置条件，11B-2B 继续 `BLOCKED`。
- 基线：`feat/hermes-harness-stage11b` / `b4552c48`；仅使用 [68 来源审计](68-harness11b2a-audit.md) 和 [68 受控夹具](68-harness11b2a-fixtures.md)，不修改 `main`。
- 正文 SHA-256：`326963485106ca97ee3149df6eb6bf04dde50a079f1266a93797430492e8a5bd`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、目标、来源等级与可证伪假设

本阶段唯一假设：在固定的 10 题 `CONTROLLED_FIXTURE` Domain 中，单算子候选 A 或 B 至少有一个能在 bootstrap 的两次重复中稳定改善固定二元 rubric，且唯一预选胜者在 holdout 上不退化、全部质量与安全检查通过。任何一项不成立即不产生 `ADOPTED`；不把该结果外推为生产收益、跨域迁移或统计显著性。Stage 9 的 7 条 `UNSCOPED_REPLAY` 记录仍为历史观察，不作为本实验正向经验或失败因果规则。

来源等级固定为 `CONTROLLED_FIXTURE`，不是 `REAL_PRODUCTION`。夹具 Domain 暂定 `ExperienceDomain(110201, 110201, "controlled_harness11b2a")`，只能写入隔离的受控经验库；这两个数是临时夹具标识，不是 `kb_bot`/业务 KB 映射。实验前必须从冻结的 `fixture.sql` 在独立 `h11b2a_fixture_*` PostgreSQL 库重建 KB、任务、工具和历史，使用 `verify-retrieval.sql` 得到实际检索结果。只读查询生产库、复用 Stage 9 注入正文/硬编码 chunk ID、凭题目 gold ID 填充上下文，均不满足来源门禁。

本次检索是**受控 PostgreSQL 全文检索**，不是 `recallTest`、pgvector 或生产混合检索。未来 runner 应按 task ID 读取 `retrieval_hit` 实际返回的正文组成 `RAGContext.preRetrievedContent`，并在独立 provenance 中保存 query、split、document ID、segment ID、rank 和正文 SHA-256；`RAGContext` 本身没有 segment ID 字段。无命中、多命中、跨 split、正文 hash 不符或 Agent 证据无法证明 `ragUsed>0` 时，该 probe 不可采纳。工具必须从冻结 `tool`/`tool_result` 表装配本地只读 callback，按准确 JSON 参数返回固定结果并记录实际调用；HISTORY 必须从 `history_message` 表按轮次装配并记录 source ID，不得在题目之外手工注入。

## 二、任务、rubric 与隔离

冻结资源及其 SHA-256：`fixture.sql` = `4d30d11b3fd73e8a647a6fb51ea6ec90dbd52bb5b59eae7fb4959b4546c263f4`；`verify-retrieval.sql` = `1b307ea0c51c1ee7ff4e5576fed0e608dc0477a482d14f5e4d99e90f4074dc77`。任务、rubric、文档、segment、工具和历史的分类快照 hash 见 68 夹具报告；任何资源字节变化均需重审并重新冻结本书，不能边跑边改。

| Split | PROMPT_ONLY | RAG | TOOL | HISTORY | COMPOSITE |
|---|---|---|---|---|---|
| BOOTSTRAP | B01 日历合法性 | B02 网关重试 | B03 交易查询 | B04 网格上下文 | B05 Mesh 指标 |
| HOLDOUT | H01 日历合法性 | H02 Webhook 验签 | H03 交易查询 | H04 缓存回滚 | H05 队列切换 |

每题的完整 `question`、`answerAllOf`、`forbid`、`goldSegmentId`、`requiredToolResultId` 和 `requiredHistorySources` 以冻结 SQL 行为准。答案判定先作 Unicode NFKC、大小写折叠和连续空白归一，再要求所有 `answerAllOf` 子串存在、所有 `forbid` 子串不存在；这些是**预注册的保守二元指标**，不是开放问题的语义正确性证明。还必须同时检查：PROMPT_ONLY 不提供 RAG/工具；RAG 的 gold segment 是实际检索 top-1 且 `ragUsed>0`；TOOL 的指定 callback 以冻结参数成功执行；HISTORY 的四个 source ID 顺序一致、最终 `content` 非空；COMPOSITE 同时满足 RAG 与工具证据。所有类别均要求没有模型错误、guard block、cycle、未授权工具调用或越界输出。任何证据缺失视为不通过，不用模型自评补足。rubric 不进入系统提示、候选凭单或 Agent 可见的检索正文。

为避免把字符串命中包装成学术质量结论，报告只能称为“固定 rubric 通过率”。候选若仍有任何题未通过，即 `failureObserved=true`，不得写 `ADOPTED`；此处不推断失败归因为 Agent 或夹具。Bootstrap 只能用于候选筛选；holdout 在胜者锁定后才揭封，不能据 holdout 结果改 rubric、改候选或反选另一候选。

## 三、候选、排程与采纳判定

基线凭单以 `HermesHarnessAssembler.createDefaultProductionVoucher(110201)` 的非提示词组件为起点，角色固定为“你是严谨的研究助手。”，核心原则固定为“优先依据受控证据回答；\n证据不足时明确说明，不编造事实；\n输出结构清晰、可追溯。”（此处 `\n` 表示一个实际 LF），安全指令沿用装配器现值，版本固定 `11b2a-baseline-v1`。实现前需冻结基线及两个候选的完整 canonical 字节和 SHA-256 指纹；若源码默认组件变化，必须先重审任务书而不是静默重建。A 仅用现有 `AlgebraicHarnessMutator.Operator.CONCISE_SYSTEM_PROMPT`；B 仅用 `ADAPTIVE_MEMORY_WINDOW`（默认短期窗口 20 改为 19）。每个候选的 `PpcCandidate.operators` 恰有一个元素，必须与实际凭单 diff 一致且指纹互异。C/`TOOL_GUIDANCE_SYNTHESIS` 本阶段**不参与**，不得事后补跑或进入 holdout。生产默认 SINGLE、kernel OFF 不变；受控 runner 如需 kernel，只能请求级显式装配。

预注册排程按每轮 B01..B05 的固定顺序，每题运行 baseline、A、B；重复 2 轮，共 `5×3×2=30` 个 bootstrap probes。每个 probe 的独立 `requestId`、候选指纹和轮次必须存证。只有全部 30 个有效 probe 完成后才筛选候选：每题每轮质量为 0/1、安全全通过；候选相对同题同轮 baseline 不能有任何 1→0，且至少**同一个 task ID**在两轮均出现 0→1。候选自身还须在 bootstrap 10 个 probe 中全部质量通过，才能作为候选经验的无失败来源。若两者均不合格，停止且在完整有效评估前提下报告 `NO_ADOPTED_EXPERIENCE`。

若 A/B 均合格，按稳定改善 task ID 数量降序、bootstrap 总实测人民币费用升序、总 tokens 升序、最后 A 优先于 B 决定唯一胜者；上述次序固定，不看 holdout。胜者指纹与选择理由写入只追加 manifest 后，holdout 按每轮 H01..H05 的固定顺序运行 baseline、**唯一胜者**，重复 2 轮，共 `5×2×2=20` probes。另一候选不进入 holdout，也不因未测 holdout 被记作失败。采纳要求胜者在每个 holdout task/轮次无 1→0，且自身 10 个 holdout probes 全部质量与安全通过；两轮方向一致。总计恰好 50 个有效 probe，最多一条受控 `ADOPTED`；holdout 不满足则 `NO_ADOPTED_EXPERIENCE`，不得回头选择另一候选。

## 四、真实调用、预算与凭据

只允许官方当前 `deepseek-flash`；不得回退旧模型名或调用 `deepseek-v4-pro`。live 前重新读取 DeepSeek 官方价格/参数文档，确认 `thinking.type=disabled` 可用并显式设置，单次 `max_output_tokens<=2000`；若当前客户端不能准确发送该参数，先修兼容性并重新验证，不把 reasoning-only 空答案算作 HISTORY 通过。模型凭据只能由用户通过 `DEEPSEEK_API_KEY` 或经核验的安全 resolver 注入，不得写入 SQL、脚本、文档、日志或 Git；本轮 shell 未提供该 key，未进行连通性验证。未来先做**一次**显式模型/usage 连通性检查，计入总模型调用、token 和费用，失败立即停止，不靠旧模型行回退。

独立 11B-2A 预算，不复用或放宽 Stage 9 `PpcRun.Budget(20,60,...)`：`maxProbes=50`（含失败尝试）、`maxModelCalls=100`（含连通性检查、工具循环和所有尝试）、`maxTotalTokens=200000`、`maxOutputTokensPerCall=2000`、`maxCostCny=10.00`。默认**不自动重试**，避免 50-probe 排程被隐性重试挤占。每次模型调用前以序列化请求 UTF-8 字节数加 4096 作为保守输入 token 预留，以高峰、未缓存输入 ¥2/百万和输出 ¥8/百万预留上界；任一预留触及硬上限则调用前停机。调用后用官方实时价格、峰谷时间和实际 cache-hit/uncached-input/output（含 reasoning）usage 按“元/百万 tokens 除以 10^6”计费。usage 缺失或不能区分计费 token 时费用为 `UNKNOWN`，立即停止，不将未知写成 ¥0；若官方价格变化或估算上界失效也立即停止。任何越限记 `BUDGET_EXHAUSTED`，保全已发生费用与不完整排程，绝不写 `ADOPTED`。

## 五、兼容证据类型与经验写入边界

现有 `ExperienceEntry` 没有 `provenanceLevel`/Operator，`AttributionStatus` 没有 `VERIFIED`，现有 `HarnessEvidenceRecord.ExecutionMode` 也没有 `CONTROLLED_FIXTURE`。实施时须向后兼容地新增该 execution mode，并用独立不可变 `ControlledProvenanceRecord`（或等价强类型记录）绑定 `entryId`、`sourceRunId`、`provenanceLevel=CONTROLLED_FIXTURE`、`evidenceVerification=VERIFIED`、唯一现有 `Operator`、候选 fingerprint、夹具/任务/rubric hash、检索 document/segment/body hash、工具实际入参/结果摘要、历史 source ID、原始证据 digest 与计量。该记录必须与 run manifest 同时校验并写入隔离 store；`EvidenceReference` 指向受控证据 digest 且 `executionMode="CONTROLLED_FIXTURE"`。不能把原始受控调用保留成 `REAL_PRODUCTION` 标签，也不能仅靠 11B-1 的字段一致性 `OperatorProvenance` 冒充来源认证。若兼容性测试失败，停止在 pre-live，不写经验。

仅当全部 50 个有效 probes、完整 usage/cost、唯一胜者及 bootstrap/holdout 门禁均通过，才可在**隔离受控 Domain** 写一条 `ExperienceEntry`：`disposition=ADOPTED`、`failureObserved=false`、`attributionStatus=NOT_APPLICABLE`（归因字段不表示证据核验）、`confidence=1.0`（仅指本固定任务集 20/20 候选 probes 通过，不是概率/泛化置信度）、`sourceRunId`/fingerprint/evidenceRef 与受控 provenance 一致。`cue`/`lesson` 只记录算子和受控适用范围，不含 qrel、原始回答或业务文本。非胜者观察可记 `OBSERVED`，已完整测得且不合格候选可记 `REJECTED`；如 `failureObserved=true`，归因为 `UNRESOLVED`，不得写 `INVALID_FIXTURE` 或全局负规则。写入必须有失败原子回滚或可验证补偿，不能留下无 provenance 的 `ADOPTED`。

状态严格区分：前置来源、凭据、类型、检索、接线或默认测试失败为 `BLOCKED`，此时 `ADOPTED=0` 表示**未完成有效评估**；有效评估完成但无合格候选为 `NO_ADOPTED_EXPERIENCE`；预算触限为 `BUDGET_EXHAUSTED`；全门禁通过且受控条目写入/重读验证后才是 `ADOPTED`。任何未知费用、缺失 probe、空最终答案或证据缺口均不能包装为质量失败后的 `NO_ADOPTED_EXPERIENCE`。

## 六、实施与验证停机门禁

本书冻结**不授权本轮开始实施或 live**。后续实施前须在 `feat/hermes-harness-stage11b` 上核验本书双 hash、夹具两文件 hash 与独立库 SQL 结果，再按用户后续授权创建实现分支；不得在本轮创建。默认测试在 canonical 全新本地 APFS 检出中使用 Java 21.0.5/Maven 3.9.16、单 Maven 进程、无手动 proto 预构建，单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`，2179+ tests、0 fail/0 error、默认无外网/付费 API。历史构建故障根因仍 `UNPROVEN`；若 proto/target/clean 异常，按 65 保全并停机，不把失败归因给新机制。

正式 runner 必须先通过隔离测试证明：受控 SQL 检索正文和 ID 逐项关联、split 隔离、实际工具调用与精确 JSON 入参、HISTORY source ID、真实 `AgentOrchestrator` 消费、受控 execution mode、预算每次调用前后计量、状态区分和原子写入。仅在 `-Plive` 显式启用且安全凭据已验证时可调用真实模型；输出放在新增的 gitignored `experiment/outputs/harness11b2a/<runId>/`，每次只追加 manifest/checksums、脱敏最终回答，不覆盖已跟踪证据。默认测试不能读取该目录。任何 preflight 缺口、模型/usage 异常、来源 ID 与实际 SQL 不符、预算触限、构建失败或夹具 hash 漂移均立即停止，不继续候选。

11B-2A 完成后只报告受控 Domain、实际调用数/token/人民币费用、baseline/A/B 与唯一 holdout 胜者、`ADOPTED` 数量和严格状态；即使受控 `ADOPTED` 成立，也不能宣称 `REAL_PRODUCTION` 或解封 11B-2B。11B-2B 的迁移任务集、预算、任务书和授权必须另立。

---
本书 hash 只保证契约字节一致；本轮未执行 live、未验证凭据、未写任何经验。Stage 9 HISTORY/COMPOSITE 仍是 `OBSERVED + UNRESOLVED`，不是已确认夹具缺陷。
