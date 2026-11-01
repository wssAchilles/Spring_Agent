# Hermes Harness Stage 11B-2A 受控夹具与检索来源记录

- 状态：`CONTROLLED_FIXTURE` 夹具已建立并通过本地 PostgreSQL 16.15 检索校验；真实 Agent/DeepSeek probe **尚未运行**。
- 分支：`feat/hermes-harness-stage11b`；审计基线提交 `b4552c48`。`main`、生产 `ai_agent` 数据库和 Redis 未修改。
- 当前实验结果：0 次模型调用、¥0、`ADOPTED=0`，原因是前置实验未开始；状态仍为 `BLOCKED`，不是 `NO_ADOPTED_EXPERIENCE`。

## 夹具边界

唯一数据源是 [fixture.sql](../../../backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness11b2a-controlled/fixture.sql)，校验查询是 [verify-retrieval.sql](../../../backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness11b2a-controlled/verify-retrieval.sql)。SQL 在创建任何对象前要求数据库名匹配 `h11b2a_fixture_*`；本轮仅在 `h11b2a_fixture_verify_20261101` 临时库执行，未使用业务 KB、生产 schema 或 Redis。

受控 Domain 的 `botId=110201`、`knowledgeBaseId=110201`、`taskCategory=controlled_harness11b2a` 仅供未来**隔离经验库**使用，不是生产映射。库含 10 题，BOOTSTRAP/HOLDOUT 各 5 题且各覆盖 PROMPT_ONLY、RAG、TOOL、HISTORY、COMPOSITE 一次；4 份文档、8 个 segment（每个相关 segment 有一个同域干扰段）、3 个工具 schema、4 组固定工具输入/输出、2 组四轮 HISTORY。问题、rubric、检索词和工具参数均在 SQL 中固定。COMPOSITE 的 `clusterId`/`queueId` 明确给出，不再复用 Stage 9 的缺参题目。

检索引擎为临时 PostgreSQL 的 `simple` 全文索引。`h11b2a.retrieval_hit` 视图按 task 的查询词、受控 KB ID 和 split 搜索 segment，**不**按 rubric 中的 gold ID 取正文；gold ID 只在事后校验。下表是实际 SQL 返回，不是题目注入：

| task | query | document ID | segment ID | returned body SHA-256 |
|---|---|---|---|---|
| B02 | `gateway retry` | `doc-b-payment` | `seg-b-payment-01` | `c3c6a3b91b0e0be4d9a90c4436aaf956e2db49ec2ede9345f2cf735bacd00d97` |
| B05 | `mesh ebpf` | `doc-b-mesh` | `seg-b-mesh-01` | `c5546070f1ad4e54d4cf4475ef78966bf7573d52719eabcea4b1c2d30262a9e9` |
| H02 | `webhook signature` | `doc-h-webhook` | `seg-h-webhook-01` | `21bdf3c7f224859f95a4b0cd79e65ea5fd795a86a82dbffdbde6b34469cac7b2` |
| H05 | `queue failover` | `doc-h-queue` | `seg-h-queue-01` | `94a8de0153b489cd3e72185b1176269b27e2fcf8ddc01273b5db7e88f904a22e` |

工具绑定为 B03/`tool-b-transaction`、B05/`tool-b-mesh`、H03/`tool-h-transaction`、H05/`tool-h-queue`，输入分别固定为 `TX-FIX-B-001`、`CLUSTER-FIX-B`、`TX-FIX-H-001`、`QUEUE-FIX-H`；每条输入的 required 字段、schema 额外字段和 split 均经 SQL 核对。HISTORY 来源分别为 `hist-b-01..04` 与 `hist-h-01..04`，轮次为 user/assistant/user/assistant。验证脚本要求四个检索任务各恰有一条命中且与独立 gold 标签一致；任何缺失、跨 split 或额外命中均不得放行。

## 冻结校验和

文件字节 SHA-256：

| 文件 | SHA-256 |
|---|---|
| `fixture.sql` | `4d30d11b3fd73e8a647a6fb51ea6ec90dbd52bb5b59eae7fb4959b4546c263f4` |
| `verify-retrieval.sql` | `1b307ea0c51c1ee7ff4e5576fed0e608dc0477a482d14f5e4d99e90f4074dc77` |

PostgreSQL 16.15 对有序 JSONB 行快照计算的分类 SHA-256（校验 SQL 中有完整算法）：

| 分类 | SHA-256 |
|---|---|
| tasks | `94f79eb0d943d637373299675a3185c759c96ba032872d58972fef32b174a1b4` |
| rubrics | `9d4dffcf685acda497064e2d4547582b49a8649e1dd941b5d4911860503a67cb` |
| documents | `8ea599fff26e8fe24581c0f72cb647e96526ea7684034ce5f896eb7d94fe80af` |
| segments | `fb5feed799ab9c54f5cd7189353aba54ce08a19d0bf35f5cedca10549e3a1562` |
| tools | `2b1a57c3a5356c66a7a57757388d8788c48292d75a20a7a36d50576daac5e438` |
| tool_results | `1c51fc76f8aa4d628a61b57cf96490ab2314083627823c341f46628e28f2b202` |
| history | `f0f142e803a64ccac0db9182d8cb3a34f25051c85c896dc5ef6151cbee12236c` |

复核命令（在项目根目录；使用**新建的同前缀临时库**，不可对 `ai_agent` 运行）：

```bash
createdb -T template0 h11b2a_fixture_verify_20261101
psql -w -d h11b2a_fixture_verify_20261101 -f backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness11b2a-controlled/fixture.sql
psql -w -A -F '|' -d h11b2a_fixture_verify_20261101 -f backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness11b2a-controlled/verify-retrieval.sql
```

本轮第二次导入和校验均退出 0；返回 4 条检索、4 组工具绑定、2 组历史来源及上述 7 类 hash。临时库复核结束后删除，可由冻结 SQL 重建。分类行快照 hash 对 PostgreSQL JSONB 文本序列化版本敏感；跨版本以 SQL 文件字节 hash 为主。

## 未跨越的门禁

本证据仅证明**受控夹具内发生过真实数据库检索**。它不是项目 `recallTest`/pgvector 的生产路径证据，也未证明 `AgentOrchestrator` 已消费这些结果、RAG attribution 已标出使用、工具已被真实 Agent 调用或 DeepSeek 凭据可用。未来 runner 必须将查询返回的正文填入 `RAGContext`，同时独立保留返回的 document/segment ID 和 body hash；不能从 rubric 的 gold ID 或 Stage 9 的 `preRetrievedContent` 构造来源。上述接线、隔离存储、凭据和 69 双哈希通过前，11B-2A live 与 11B-2B 均 `BLOCKED`。
