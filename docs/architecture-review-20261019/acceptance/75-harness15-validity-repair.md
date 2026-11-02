# Stage 15: 受控 PPC 实验效度修复

## 结论与证据边界

Stage 13 原始结论 `NO_ADOPTED_EXPERIENCE` 保持不变；它仅表示原 10 题、旧 rubric、旧 A/B 与 10/10 门槛下没有候选被采纳。Stage 14 已指出候选效果推断无效。本阶段只做离线契约修复，**没有**重新运行 Stage 13、调用 DeepSeek、进入 holdout、写入 `ADOPTED` 或修改生产路径。当前决定：**不直接重跑**。新结构的可判定性和请求作用点已通过 fake-model 测试，但真实模型的同题两轮稳定 baseline 失败尚无证据。

原始第二批运行 `crppc1-5b398395-a8cd-4106-a42b-5a678d410e2e` 的 `run.json` SHA-256 为 `49ce0c3a903e295d986bfcebf4b694190067c1fca06c086a4a5b1ad1bbd12499`，`checksums.sha256` 为 `bb3f26defd91830c4fb221500f18fe562b163d98637887dcf4e19c80e5873830`。派生 `stage13-quality-matrix.json` SHA-256 为 `4f3310cf725e111a875847aa255b2831c3ccd3daf2b7`；已与原始 30 probe 的 `taskId/repetition/candidateId/qualityPass` 逐项比对。派生文件使旧矩阵在干净检出中可复算，但不等于用新 rubric 重新评分：旧回答仅存 300 字前缀和完整回答的 hash，无法恢复完整答案。

| 题/轮 | baseline | 旧 A | 旧 B |
|---|:---:|:---:|:---:|
| B01/1 | F | P | P |
| B01/2 | P | P | P |
| B02/1 | P | P | P |
| B02/2 | P | P | P |
| B03/1 | F | F | P |
| B03/2 | P | F | F |
| B04/1、2 | P/P | P/P | P/P |
| B05/1、2 | P/P | P/P | P/P |
| **10 次/列** | **8/10** | **8/10** | **9/10** |

baseline 第一轮失败集合 `{B01,B03}`，第二轮为空，交集为空。旧采纳条件要求**同题两轮** baseline 失败且候选通过，所以这批数据无论候选自身分数如何都不可能满足稳定改善。此结论只针对已经观察到的这 30 个 probe，不是对未来模型输出的定理。

## 缺陷与修复

1. 旧 B03 `forbid=["已发凭证"]` 对整段文本做裸子串匹配；“是否已发凭证”“未发凭证”也含该字串。六次工具调用均使用正确交易 ID 且成功，四个 `F` 的已存前缀显示否定/疑问表达，故不能把旧 `F` 直接归因于交易理解错误。新 `ControlledAnswerRubric` 使用严格 JSON `taskId` 和 `facts`，以 `receiptIssued:false` 判定状态；可选 `sourceQuote` 只作原文引用、不参与事实判定。`receiptIssued:true` 明确失败；非 JSON 或额外断言字段不被接受。
2. 旧 A 只折叠 `corePrinciples` 空白。A-v2 `evidenceBoundPrompt` 加入逐字段证据核对、区分疑问/否定/引用与肯定事实的语义指令；不包含 gold 答案。旧 A 的指纹与结果保持冻结，A-v2 是新候选，不追认旧 8/10。
3. 旧 B 将短期窗口 20 改为 19，但旧 HISTORY 仅 4 条。B-v2 复用同一个有界算子，新的 B04/H04 各有 20 条按 source ID 固定的 HISTORY；第 1 条为过期提案，第 20 条为最终更正。fake-model 请求捕获证明 baseline/A 能看到过期提案，而 B 看不到且仍能看到最终更正。**这只证明输入变化，不证明真实模型答案改善。**
4. 旧 B01 仅存 300 字前缀，无法独立复算完整回答为何失败。新 `Assessment` 保存完整的脱敏答案、答案 SHA-256、rubric SHA-256、判定码；在同一版本 rubric 上可独立重算与验 hash。若检测到 API key、邮箱或手机号样式，输出被遮盖并标为 `REDACTED_OUTPUT`，不可通过质量门禁；该有限脱敏规则只适用于合成受控内容，未来真实数据须另审。

新题目资源为 `src/test/resources/harness15-validity/tasks.json`，SHA-256 `ac42d03c5c8629f3fdf22133f13082a36d0890d5f5a0f50ae064d0030d4e6513`。10 题按 5 bootstrap/5 holdout 分开，各覆盖 PROMPT_ONLY/RAG/TOOL/HISTORY/COMPOSITE 一次；每题记录一个明确的可判定失败机制与标量事实预期。B01 的格式/日历混淆和 B04 的过期决议干扰是**待证伪的 baseline 失败预测**，不是已经观察到的稳定真实模型失败。RAG/工具数据目前仅引用旧受控任务 ID；本阶段没有声称新版 SQL 检索 provenance 或 live runner 已接通。

## 采纳与验收边界

采纳门槛**未降低**：bootstrap 仍需 30 个有效 probe；候选自身 10/10、没有同题同轮 1→0，且同一 bootstrap task 在两轮均 baseline 0→候选 1；先选唯一胜者，再进行 20 个 holdout probe，胜者自身 10/10 且无退化；只有完整 50 probe 与安全、来源、计量证据均成立才允许最多一条受控 `ADOPTED`。若下次 baseline 两轮失败集合仍无交集，应按预注册规则停机，重新设计任务，不事后改门槛。

离线定向测试 `ControlledValidityContractTest`：6/6 通过，覆盖否定表达、完整答案 JSON 落盘后复算/篡改、10 题结构、旧 30-probe 派生矩阵和 AgentOrchestrator fake-model 的 A/B 请求变化。另用明确的“遇到第一条方案就采用”的确定性测试 oracle，在 B04 上得到 baseline 两轮 `F/F`、B-v2 两轮 `P/P`；它只证明夹具能触发稳定改善门槛，不是 DeepSeek 的行为或真实效果。单步全量 `clean test` 为 2202 tests / 0 failures / 0 errors / 10 skipped。新 rubric 尚未替换 Stage 13 runner；因此 Stage 15 是**效度契约与离线作用点修复**，不是新 live 实验已就绪。下一次是否值得重跑，必须先以新任务书/预算/runId 完成独立来源夹具、版本化 runner 和真实模型 baseline 稳定性门禁；未达到时保留本次受控负结果，不设计 Candidate C。
