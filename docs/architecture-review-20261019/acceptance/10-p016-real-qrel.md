# P0-16 真实语料 30 条 qrel 检索评测报告

> 日期：2026-10-22　|　模式：B（Agent 生成 qrel + 独立复核 + 人工抽查）　|　后端：Java 21 / 8098，真实 PostgreSQL + pgvector + v4 embedding
> 数据：[qrel_real_corpus_30.json](../../../experiment/datasets/qrel_real_corpus_30.json)　|　脚本：[eval_real_corpus_qrel.py](../../../experiment/scripts/eval_real_corpus_qrel.py)　|　结果：[outputs/p016_real_qrel](../../../experiment/outputs/p016_real_qrel)

## 1. 语料与 qrel

- 真实 `vector_store` 4,955 条 / 1024 维（`text-embedding-v4`）；qrel 覆盖 KB 8（2,317 条）与 KB 1（2,158 条）。
- 30 条 query：KB 8 15 条（大数据/移动应用开发/人工智能/分布式），KB 1 15 条（RAG、Agent、知识图谱、CI/CD、性能优化）。
- 每条 gold 同时含 `gold_document_ids`（主指标）与 `gold_segment_ids`（辅指标）、source chunk、evidence quote。
- `frozen_hash = 99410c5a6a42e5b225ff33901f7f6268424dc4f1a5a84d8a14f4142e54b1228c`；评测脚本启动时校验。

## 2. 标注与复核（Mode B，含一次返修）

| 步骤 | 实际执行 |
|---|---|
| query 生成 | 从真实 chunk 出发，由 DeepSeek 生成 15–40 字中文问题；生成阶段不调用检索 API、不看检索结果 |
| 独立复核 v1 | 独立 reviewer 复核 30 条：PASS 25，FAIL 5（Q003/Q017/Q022/Q028/Q030），原因均为**连续照搬原文 ≥8 字** |
| 返修 v2 | 对全部 query 做“去空白后 8 字重叠”严格审计，共发现 11 条重叠（含 reviewer 未列出的 6 条），全部重写；v2 严格审计 **0 条 ≥8 字重叠** |
| 独立复核 v2 | PASS 29/30；Q025「文档采用什么记录策略？」被判空泛 FAIL |
| 返修 v3 | 重写 Q025 为「Day 12 的日志记录如何区分已确认内容与待补充内容？」；修正 6 条 category 元数据；严格审计 0 重叠；本地复核 PASS 30/30 |
| 人工抽查 | 8 条抽查表见第 5 节，**状态：PENDING** |

> 结论口径：本 qrel 是“Agent 生成 + 独立复核 + 返修”的 provenance-derived 基准，不等同于人类裁决 qrel；人工抽查通过后才可标为正式验收证据。

## 3. 检索配置与指标（qrel v1.2）

- 检索：真实 `POST /kmc/knowledgeBase/recallTest?debug=true`，`topK=10`，生产配置（v4 + hybrid + RRF），未调参。
- 指标：文档级 Recall@5/10、MRR@10、nDCG@10；segment 级 Recall@10、MRR；1000 次 bootstrap 95% CI。

| 指标 | 均值 | 95% CI | 样本 |
|---|---|---|---|
| Doc Recall@5 | **0.9667** | [0.8667, 1.0000] | 30 |
| Doc Recall@10 | **0.9667** | [0.8667, 1.0000] | 30 |
| Doc MRR@10 | **0.9056** | [0.8056, 0.9778] | 30 |
| Doc nDCG@10 | **0.9210** | [0.8333, 0.9833] | 30 |
| Segment Recall@5 | **0.6333** | [0.4333, 0.8000] | 30 |
| Segment Recall@10 | **0.6333** | [0.4333, 0.8000] | 30 |
| Segment MRR@10 | **0.3506** | [0.2206, 0.4917] | 30 |

- 30/30 query 执行成功，无 API 失败、无 NaN；结果非退化。
- 成本：仅 30 次查询 embedding，< ¥0.05；未调用 DeepSeek 生成。

## 4. 失败与差异分析

### 4.1 文档级唯一漏召回：Q015

- Query：“该系统使用哪种数据库存储用户、群组和新闻等数据？”
- gold doc 25（`分布式.pdf`），实际 top-1 doc 27（`大数据.pdf`）。
- 保留为失败案例，不修改 qrel。

### 4.2 segment 级差异

segment Recall@10 0.6333，低于文档级 0.9667。原因是生产检索返回父/子/上下文切片，命中同一文档的不同 segment；segment gold 只标 source chunk 本身。因此**文档级指标代表“是否找到正确证据文档”，segment 级是精确切片命中率的下界**。

## 5. 人工抽查表（等待确认）

| # | Query | gold 文档/chunk | 依据原文 |
|---|---|---|---|
| 1 | 该系统在资源受限条件下解决了哪类稳定性问题？ | doc 27 / 1128e8ac | 在资源约束内最大化消除了 OOM 崩溃 |
| 2 | 哪个配置项通过限制执行计划字符串长度来避免日志文件膨胀？ | doc 27 / 6adde8aa | `ark.sql.maxPlanStringLength=8192` 是压缩 event log 体积的关键开关 |
| 3 | Flutter 服务层为提升模拟器稳定性改进了哪些方面？ | doc 26 / 4ea7b388 | auth refresh convergence、polling interval control、caching strategies |
| 4 | 图书搜索模块的多层级匹配策略按什么优先级执行？ | doc 26 / 77f2d6c1 | 匹配按优先级从高到低执行：1. 精确匹配 |
| 5 | 该分布式推荐系统后端由哪些语言组件组成？ | doc 25 / 55546dd0 | Spring Boot、Go for messaging、C++ for graph processing |
| 6 | 为了支持可配置的间隙参数，建议在哪个文件补统一入口？ | doc 14 / 9ab596bf | 为 `bottom_sheet_spacing.dart` 补充统一入口，支持自定义 |
| 7 | 第 1 章分析移动端应用更新稳定性时，重点排查了哪两种现象？ | doc 13 / e5c16eee | APK 安装失败和后台下载中断 |
| 8 | Day 12 的记录能力从占位升级到了什么状态，并实现了哪三项输出？ | doc 12 / 59fc33bb | 向“工程化记录态”升级，实现“结构化台账 + 可视化流程 + 风险追踪” |

请确认：**上述 8 条 query 均能由对应依据原文回答，且未看检索结果后反推 gold。** 回复“抽查通过”后，P0-16 闭环；若某条不成立，只改该条标注并重新冻结后重跑。

## 6. 限制与结论

- 30 条小样本；CI 较宽，不把差异包装成统计显著性。
- qrel 为 known-item/provenance-derived 自动生成，存在“query 与 source 同源”的乐观偏差；文档级指标用于**真实链路可复现性证据**，不等同于人工裁决基准。
- **结论**：真实链路已产出可复现、非退化的指标（Doc Recall@10=0.9667，MRR@10=0.9056）；独立复核 v2 为 29/30，Q025 修复后本地复核 PASS 30/30；P0-16 的真实运行证据已具备，剩余 gate 为人工抽查确认。
