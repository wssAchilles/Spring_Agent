# Hermes Harness Stage 15 实验效度修复任务书

- 状态：`OFFLINE CONTRACT ONLY`；不授权 live、Candidate C、生产接入或 11B-2B。
- 来源：Stage 13 原始运行 `crppc1-5b398395-a8cd-4106-a42b-5a678d410e2e` 与 Stage 14 `74-harness14-failure-analysis.md`；原运行结论保持 `NO_ADOPTED_EXPERIENCE`。
- 正文 SHA-256：`4b9a603fa08c4e21c702ca67bd017fec609ff1e5f7747c8cc1f80c66baeef29a`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、唯一可证伪假设与范围

唯一假设：将输出改为类型化事实 rubric，并使 A 的语义指令与 B 的 20→19 HISTORY 裁剪实际到达 Agent 模型请求，可消除旧 B03 子串误伤和旧 B 输入不变这两个**实验设计缺陷**。若否定表达仍被误判，或 fake-model 首个请求中 A/B 与 baseline 无实质差异，则假设不成立。本假设不声称真实模型已稳定改善，也不声称 A/B 已可采纳。

仅允许独立的 `ppc/validity` 契约、合成任务资源和确定性测试。不得修改 Stage 13 冻结 `fixture.sql`、`verify-retrieval.sql`、`ControlledPpcRunner`、其 A/B 指纹、原始证据或旧 rubric；不得调用模型 API、访问生产 DB、改 AgentOrchestrator 生产接线、写经验或改变旧结论。RAG/工具任务只保留旧受控 ID 作为离线语义参照；未来 live 必须另立完整来源夹具。

## 二、新任务与 rubric

任务清单冻结在 `backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness15-validity/tasks.json`，SHA-256 为 `ac42d03c5c8629f3fdf22133f13082a36d0890d5f5a0f50ae064d0030d4e6513`。所有题目的 `expectedFacts` 仅用于评分，不能进入 Agent 的系统提示、用户问题、RAG、工具返回或 HISTORY；模型只接收 `question` 与共同 `answerContract`。本资源不复用旧 SQL 的 rubric hash，也不冒充已验证的新 PostgreSQL fixture。

| 题 | 类别 | 可判定的失败机制 | 候选作用点 |
|---|---|---|---|
| B01/H01 | PROMPT_ONLY | ISO 形状与真实日历有效性混淆 | A 的逐事实核对 |
| B02/H02 | RAG | 重试/验签条件遗漏 | A 的逐事实核对；需未来检索来源门禁 |
| B03/H03 | TOOL | 疑问/否定误判已发、PENDING 误报成功 | A 的布尔事实核对；需未来工具调用门禁 |
| B04/H04 | HISTORY | 过期第 1 条决议干扰最终第 20 条更正 | B 的 20→19 实际裁剪 |
| B05/H05 | COMPOSITE | 检索规则和工具数值未同时使用 | A 的逐事实核对；需未来双来源门禁 |

新答案为严格 JSON：顶层 `taskId` 与 `facts` 必需，`facts` 只能包含任务预注册的标量字段且类型/值逐项精确匹配；可选 `sourceQuote` 是引用，不是事实断言。其它顶层字段、缺失/多余事实、非 JSON、类型错误均不通过。B03 用工具给出的布尔 `receiptIssued=false`，不再扫描“已发凭证”字串；`sourceQuote` 即使含“是否已发凭证/未发凭证/不代表已发凭证”也不会被错判为肯定，`receiptIssued=true` 则失败。RAG、工具、HISTORY、COMPOSITE 的既有来源/安全门禁应与答案 rubric 作逻辑与；答案通过不能替代实际检索、调用和 source 顺序证据。

每个 probe 必须保存**完整**脱敏答案而非 300 字前缀、答案 SHA-256、任务/rubric SHA-256、结构化判定码和来源证据。用相同冻结 rubric 从保存的完整答案独立重算，与原判定和 hash 比较；不一致、缺失答案或发生脱敏均不可采纳。当前脱敏仅识别 `sk-` 样式 key、邮箱和大陆手机号，受控合成数据范围外不得据此声称隐私保护完备。旧 Stage 13 完整答案不可恢复，故旧 30 probe 只复算旧 `qualityPass` 矩阵，不用新版 rubric 追认分数。

## 三、候选与采纳门槛

旧 A/B 保持冻结。A-v2 以 `ControlledValidityCandidates.evidenceBoundPrompt` 仅改变 `corePrinciples`：新增逐字段核对、布尔值依据显式证据、疑问/否定/引用不作肯定事实的语义规则；不注入预期答案。B-v2 复用现有 `ADAPTIVE_MEMORY_WINDOW` 的 20→19 变换，但 B04/H04 的 HISTORY 固定为 20 条，第一条过期、最后一条最终决议。测试必须检查实际 Agent 请求：A 的 system prompt 改变且历史不变；B 的 system prompt 不变、最旧历史消失、最终决议保留。仅比较凭单指纹不够。

正式实验门槛保持原版：5 bootstrap 题 × baseline/A-v2/B-v2 × 2 = 30 有效 probe；候选自身 10/10、无同题同轮 1→0、至少一个同一题两轮均 0→1。若 A/B 均合格，只选唯一胜者；5 holdout 题 × baseline/胜者 × 2 = 20 有效 probe，胜者自身 10/10、无退化。全部 50 个有效 probe、安全与来源/计量门禁完成后才可写最多一条隔离受控 `ADOPTED`。任何 baseline 两轮失败集合交集为空的批次都不能产生稳定改善；应停机并另立新任务，不得事后降低 10/10 或改为单轮改善。

## 四、离线验证与进入下一轮的条件

本轮只验证：10 题与 5/5 split、五类任务各覆盖一次、每题失败机制；B03 否定/疑问引用不误伤且肯定布尔值失败；完整答案 JSON 落盘后与双 hash 可重算、篡改失败；fake model 通过真实 AgentOrchestrator 捕获 A/B 的首个请求差异。派生 `harness15-validity/stage13-quality-matrix.json`（SHA-256 `4f3310cf725e111a875847aa255b2831c3ccd3daf2b7`）仅保留旧 30 probe 的质量布尔值；必须对照原始 `run.json` hash，不可冒充完整答案。使用局部 Java 21：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=ControlledValidityContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

默认全量 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` 亦须通过；测试不读取 gitignored live evidence、无外网、无付费调用。原 Stage 13 30 probe 可按 `run.json` 的 `taskId/repetition/candidateId/qualityPass` 重建旧矩阵，但完整答案不存在，不能声称新 rubric 已在旧结果上验证。

本轮**不授权重跑**。下一次实验须新任务书、新预算、新 runId、新的受控来源夹具与版本化 runner，并在候选评估前以预注册程序证明指定 bootstrap 题的 baseline 两轮失败；若该前提不成立，应改题目而不是改门槛。fake-model 的稳定失败只能证明测试夹具与门禁可达，不能替代真实模型观测。Candidate C 等 A/B 作用点与新 rubric 的真实稳定性被证实后再决定。
