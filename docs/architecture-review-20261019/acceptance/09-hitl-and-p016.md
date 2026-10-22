# HITL / 工具调用端到端冒烟与 P0-16 评测结论

> 日期：2026-10-22　|　后端：Java 21、独立端口 8098；真实 PostgreSQL/Redis/Neo4j；GitHub MCP stdio（`@modelcontextprotocol/server-github@0.6.2`，本地 node_modules 预热后成功注册 9 个工具）

## 1. HITL / 工具调用端到端冒烟：通过（并修复 2 个真实缺陷）

### 1.1 实验路径

1. 复用 Bot 9（`智能因果协同工作流`），临时把 `tool_1` 配置为：
   `mcp.github.search_repositories` + `requireApproval=true`，输入映射 `query`。
2. `POST /kb/flow/executeFlow`（`botId=9`）→ 流程在工具节点返回 `SUSPENDED`。
3. 校验 Redis：`hermes:approval:pending:9:<runtimeId>:tool_1` 存在；`GET /hermes/approval/pending` 返回待审批单。
4. `POST /hermes/approval/approve`（`flowId=9, requestId=<runtimeId>, nodeId=tool_1`）。
5. 审批代理自动 `resumeFlowWithApproval` → 真实重放工具节点 → GitHub `search_repositories` 被调用 → 运行实例 `SUCCESS`。

### 1.2 关键结果

| Runtime | 场景 | 结果 |
|---|---|---|
| 135 | 修复前审批恢复 | 审批通过后运行实例直接 `SUCCESS`，但 `tool_1` 仍是 `SUSPENDED`，**工具从未执行**（HITL 恢复跳过工具的重放缺陷） |
| 136 | 修复后但 flow style 快照仍为旧配置 | 恢复后重放了工具层，但读到旧工具名 `causal_counterfactual_verifier` → 正确失败（证明恢复确实重放，而不是跳过） |
| 137 | 修复后 + style/config 对齐 | 恢复后第 5 层重放 `tool_1`，工具节点 `status=1`，输出 GitHub `search_repositories` 原始结果，运行实例 `status=1 (SUCCESS)` |

137 的工具节点输出（截断）：

```json
{"tool":"mcp.github.search_repositories",
 "input":{"query":"model context protocol 官方仓库有哪些，企业知识库接入工具要注意什么"},
 "rawResult":"{\"content\":[{\"type\":\"text\",\"text\":\"{\\n  \\\"total_count\\\": 0, ...}\"}]}",
 "status":"SUCCESS"}
```

> GitHub 搜索对中文长查询返回 `total_count=0` 属工具正常返回；关键证据是调用真实发生、参数正确传入、结果回到流程。

### 1.3 冒烟暴露并已修复的缺陷（commit `49f33dd9`）

1. **HITL 恢复跳过工具执行**：`DagCheckpointManager.wakeSuspended` 把任意挂起节点直接标记 `SUCCESS`，恢复时 `allCompleted=true` 从下一层开始，工具永远不会执行。
   - 修复：工具节点挂起时输出 `replayOnWake=true`；唤醒时将该节点移出“已完成”集合，恢复时重放该层；审批节点保持“审批通过即完成”的既有语义。
2. **审批节点重复恢复会再次挂起**：恢复重放审批节点时，`ApprovalNodeExecutor` 未检查既有决策。
   - 修复：执行前读取 Redis `hermes:approval:decision:*`，若为 `APPROVED` 直接返回成功，实现幂等。
3. **`dag_checkpoints` 历史列类型不兼容**：本地旧表 `created_at/updated_at` 为 `bigint`，而代码写 `CURRENT_TIMESTAMP`，导致检查点保存失败、审批无法恢复。
   - 修复：启动时自动迁移为 `TIMESTAMP`（兼容旧库），本环境已迁移成功。

### 1.4 实验环境说明

- 为让 flow 真正跑到工具节点，临时给 `knowledge_1` 绑定 KB 8（否则知识库节点按设计 fail-close）。
- 实验后 Bot 9 的 `tool_1` / `knowledge_1` 配置与 style 快照已**全部还原**，Redis 测试审批键已清理，临时 8098 进程已停止；用户本地 8099 dev watcher 未改动。

## 2. P0-16 小规模 Recall/MRR 评测：BLOCKED（qrel 与真实语料不匹配）

### 2.1 尝试与证据

仓库现有评测资产：

| 资产 | 实际情况 |
|---|---|
| `experiment/datasets/qrel_ground_truth.json` | 30 个任务、字段为 `gold_entities`（实体级），不是真实 `vector_store` 的 chunk/doc ID |
| `experiment/runs/baseline_g0/payloads/TASK_*` | 仅 10 个任务有 payload；其中 8 个无法从推理文本中提取出用户问题；仅 TASK_001/009 有可用问题 |
| 真实 `vector_store` 语料 | 4,955 条 / 1024 维（`text-embedding-v4`）；对 `SagaCoordinator / GraphRAG / RadixPrefixTree / ColbertNative` 等 gold 实体做全文匹配，命中数为 **0** |
| `backend/tests/evidence/eval_results_300*` | `evaluationMode=offline_deterministic_retrieval`，gold 为 `src-conf-*` 合成 segment ID，**不是真实库** |

结论：现有 qrel 是面向“合成图/微服务实验语料”的实体级标注，而真实库是实习日志、课程报告、政策文档等；两者不存在可对齐的 chunk/doc 标识。若强行用现有 qrel 对真实 API 计算 Recall/MRR，只会得到接近 0 的数值，既不能证明检索质量，也不能作为 P0-16 的通过证据，属于“用错标注制造指标”。

### 2.2 为什么不能直接新建 qrel

按 `AGENTS.md` 第一章，**新增/调整 qrel 或 fixture 会进入算法结果影响边界，必须走 Research-to-Implementation Gate**，不能为了让本次评测通过而临时造标注。因此本轮停止在“可复现的阻塞结论”，不伪造指标。

### 2.3 解除阻塞所需的最小方案（待授权）

1. **授权构建真实语料 qrel**（建议 30 条，KB 8 为主、KB 1 补充）：
   - 查询来自真实文档可回答的问题；
   - gold 使用 `vector_store.metadata->>'kmc_segment_id'` / `kmc_document_id`，不用实体名；
   - 标注过程盲测：先固定 query，再检索候选，人工/规则只确认“是否回答了问题”，避免按 top-k 结果反推 gold。
2. 定义指标与预算：`Recall@5`、`Recall@10`、`MRR@10`、`nDCG@10`，固定 topK 与检索模式（v4 + hybrid + RRF），记录延迟与 embedding token；单轮成本 < ¥1。
3. 通过 Gate 后再执行，并将结果写入 `acceptance/`；在此之前 P0-16 状态保持 **BLOCKED（缺真实 qrel）**，不以合成数据替代真实运行证据。

## 3. 当前状态

- HITL/工具调用：**通过**（真实 MCP 工具执行 + 审批恢复），缺陷已修复并新增 2 条回归测试；全量 **1907 tests / 0 fail / 8 skipped**。
- P0-16：真实 qrel 已按 Mode B 构建并跑出真实指标，见 **[10-p016-real-qrel.md](10-p016-real-qrel.md)**；人工抽查 8 条待确认，确认后闭环。
- 相关提交：`49f33dd9`（HITL 恢复修复）、`92084772`（v4 维度基线）、`ecb0a841`（启动修复）。
