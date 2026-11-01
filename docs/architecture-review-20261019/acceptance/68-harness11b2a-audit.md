# Hermes Harness Stage 11B-2A 来源等级预审

- 结论：`BLOCKED`。当前既不能证明 `REAL_PRODUCTION`，也尚未建立可核验的 `CONTROLLED_FIXTURE`。按 11B-2A 阶段 0 约束，**未开始 live、未消耗模型 token/费用、未创建 11B-2A 分支、未写 ADOPTED 经验**。
- 检查基线：`feat/hermes-harness-stage11b` / `6435ec78`；只读核查 PostgreSQL 元数据、现有源码与 DeepSeek 官方价格页。未读取或记录密钥明文及业务文档内容。

## 来源等级判定

| 等级 | 当前证据 | 判定 |
|---|---|---|
| `REAL_PRODUCTION` | `kb_bot.id=9` 存在，但只读查询未发现对应 `kb_agent_config` 的知识库/工具绑定；Stage 9 导入的 7 条记录均处于 `UNSCOPED_REPLAY`，不能推断 `knowledgeBaseId` 或 `taskCategory`。 | 不成立；不得将 Stage 9 记录改写为生产 Domain。 |
| `CONTROLLED_FIXTURE` | 本地 PostgreSQL/Redis 在监听，库中有知识库及切片，但没有已冻结、由本阶段创建并能证明来源的受控 KB/工具任务集。现有 Stage 9 `PpcOrchestrator.fixtures()` 将 RAG 正文直接写入 `preRetrievedContent`，chunk ID 在 fixture 中硬编码；这不是一次可核验的真实检索。 | 目标等级可选，但**尚未建立**，不能据此开始 live 或写 ADOPTED。 |

`AgentOrchestrator` 读取 `ChatRequest.ragContexts` 中的预检索正文；真实检索发生在控制面 `KbAgentConfigServiceImpl` 的 `coordinateRetrieval`/`recallTest` 之前。当前 `RAGContext` 只有 knowledge ID、name、预检索正文，不带 document/segment ID；若实施受控实验，必须从实际检索返回的 `RetrieveResult`/校准切片单独保存来源 ID 并与发给 Agent 的正文逐项关联，不能沿用 Stage 9 注入的 `chunkIds`。

## 凭据与计量预检

- 当前 shell 中 `DEEPSEEK_API_KEY` **未设置**。只读数据库检查发现 DeepSeek 凭据记录非空，但未解密、未验证可用性，也未调用模型；这不等于可执行的 live 授权凭据。现有 Stage 9 live 测试只读取 `DEEPSEEK_API_KEY`，不能直接复用。
- 数据库 `ai_model` 中未见显式 `deepseek-flash` 记录；既有 DeepSeek 行仍为旧模型名。新实验必须显式锁定 `deepseek-flash`，不能靠旧名回退或改用 `deepseek-v4-pro`。
- DeepSeek [官方价格页](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) 本轮实际读取：`deepseek-flash` 每百万 tokens，空闲时缓存命中输入 ¥0.02、未命中输入 ¥1、输出 ¥4；高峰时分别 ¥0.04、¥2、¥8。北京时间工作日 09:00-12:00、14:00-18:00 为高峰，法定节假日除外。页面声明价格可能变化；live 前须再次核验，并在 usage 不完整时将费用标记 `UNKNOWN` 而非零。

## 冻结前必须修正的契约

1. 现有 `ExperienceEntry.AttributionStatus` 为 `NOT_APPLICABLE`、`UNRESOLVED`、`FIXTURE_CONFIRMED`、`AGENT_CONFIRMED`，没有任务提示中的 `VERIFIED`。不能把“采纳证据已核验”混入“失败归因”枚举；69 任务书需单独定义证据核验状态，或明确 ADOPTED 沿用 `NOT_APPLICABLE` 并由不可变 manifest 证明采纳。
2. `ExperienceEntry` 没有 `provenanceLevel` 或唯一 `Operator` 字段；`HarnessEvidenceRecord.ExecutionMode` 也没有 `CONTROLLED_FIXTURE`。若输出受控经验，必须定义向后兼容的类型化证据/来源关系，不能将其标成 `REAL_PRODUCTION`。11B-1 的 `OperatorProvenance` 只做字段一致性核对，不是原始 run 的来源认证。
3. Stage 9 的 `PpcRun.Budget` 限制最多 20 probes/60 calls，`PpcOrchestrator` 固定 `harness9-live`、5 个 fixture 和候选。11B-2A 的 50/100 上限必须用独立预算与 Probe 实现，不能放宽 Stage 9 预算或复用其判定冒充新实验。
4. 提示中的 A/B/C 三个候选与“5 个 bootstrap × 3 configs × 2 次 = 30”只覆盖 baseline+A+B。若 A、B 都过 bootstrap，再把两者都带入 holdout 会使总 probes 达 60，超过 50。69 必须预先规定只选一个候选进 holdout、C 的处理方式及确定性选择次序；不能看完结果后临时挑选。

## 解锁条件

1. 在隔离 Domain 建立固定 10 题（5 bootstrap、5 holdout）与受控 KB/工具记录；保存实际检索 document/segment ID、工具调用和历史消息来源，确认 `CONTROLLED_FIXTURE` 的只读/可清理边界。现有业务 KB 不能未经核验直接充当受控夹具。
2. 通过安全环境注入或现有 `HermesCredentialResolver` 路径验证 `deepseek-flash` 可用凭据，**不得把密钥写入文档、脚本或日志**。
3. 先修正上述类型与预算契约，完成 [69 任务书](69-harness11b2a-taskbook.md) 的任务、rubric、切分、判定和双 SHA-256 冻结，再开始任何 live 调用。

在这些条件之前，不创建 11B-2A 实现分支，不运行 `-Plive`，不修改生产配置/数据库/Redis，也不写入任何 ADOPTED/REJECTED 经验。11B-2B 继续 `BLOCKED`。
