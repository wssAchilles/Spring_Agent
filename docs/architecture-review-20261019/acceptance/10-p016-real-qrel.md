# P0-16 真实语料 30 条 qrel 检索评测报告

> 日期：2026-10-22　|　模式：B（Agent 生成 qrel + 独立复核 + 人工抽查）　|　后端：Java 21 / 8098，真实 PostgreSQL + pgvector + v4 embedding
> 数据文件：[qrel_real_corpus_30.json](../../../experiment/datasets/qrel_real_corpus_30.json)　|　脚本：[eval_real_corpus_qrel.py](../../../experiment/scripts/eval_real_corpus_qrel.py)　|　原始结果：[outputs/p016_real_qrel](../../../experiment/outputs/p016_real_qrel)

## 1. 语料与 qrel

- 真实 `vector_store` 共 4,955 条 / 1024 维（`text-embedding-v4`）；qrel 只覆盖 KB 8（2,317 条）与 KB 1（2,158 条）。
- 30 条 query：KB 8 15 条（大数据/移动应用开发/人工智能/分布式），KB 1 15 条（RAG、Agent、知识图谱、CI/CD、性能优化）。
- 每条 gold 同时记录 `gold_document_ids`（主指标）与 `gold_segment_ids`（辅指标）；source chunk ID、evidence quote、reviewer 状态一并冻结。
- `frozen_hash = b83dcb16472513947d3173bdb2ccd0d69d826f6dca8a19111381140138801137`；评测脚本启动时校验，qrel 运行后不可修改。

## 2. 标注与复核（Mode B）

| 步骤 | 实际执行 |
|---|---|
| query 生成 | 从真实 chunk 出发，由 DeepSeek 生成 15–40 字中文问题；生成阶段**不调用检索 API、不看检索结果** |
| 自动校验 | 长度、8-gram 原文重叠 ≤12 字、禁止目录/页码/章节/元数据类问题 |
| 独立复核 | 独立 reviewer prompt 对 30 条逐条判断“仅凭片段可答/无泄漏/非空泛”，**PASS 30/30** |
| 人工抽查 | 8 条抽查表已生成（见第 5 节），**状态：PENDING（等待确认）** |

> 说明：本轮没有人类逐条标注；qrel 属于“Agent 生成 + 独立 LLM 复核”的 provenance-derived 基准，不等同于 TREC 式人工裁决 qrel。人工抽查通过后才可作为正式验收证据。

## 3. 检索配置与指标

- 检索：真实 `POST /kmc/knowledgeBase/recallTest?debug=true`，`topK=10`，当前生产配置（v4 + hybrid + RRF），未调参。
- 指标：文档级 Recall@5/10、MRR@10、nDCG@10；segment 级 Recall@10、MRR；1000 次 bootstrap 95% CI。

| 指标 | 均值 | 95% CI | 样本 |
|---|---|---|---|
| Doc Recall@5 | **0.9667** | [0.8667, 1.0000] | 30 |
| Doc Recall@10 | **0.9667** | [0.8667, 1.0000] | 30 |
| Doc MRR@10 | **0.9278** | [0.8389, 1.0000] | 30 |
| Doc nDCG@10 | **0.9377** | [0.8544, 1.0000] | 30 |
| Segment Recall@5 | **0.6667** | [0.5000, 0.8333] | 30 |
| Segment Recall@10 | **0.7333** | [0.5667, 0.9000] | 30 |
| Segment MRR@10 | **0.5090** | [0.3529, 0.6629] | 30 |

- 30/30 query 执行成功，无 API 失败、无 NaN；结果非退化。
- 成本：仅 30 次查询 embedding，远低于 ¥0.05；未调用 DeepSeek 生成。

## 4. 失败与差异分析

### 4.1 文档级唯一漏召回：Q015

- Query：“该系统使用哪种数据库存储用户、群组和新闻等数据？”
- gold doc 25（`分布式.pdf`），实际 top-1 doc 27（`大数据.pdf`）。
- 判断：真实检索错误/文档级 gold 与近邻主题混淆；保留为失败案例，不修改 qrel。

### 4.2 segment 级差异（8 条）

segment Recall@10 仅 0.7333，低于文档级 0.9667。原因：
- 生产检索返回的是**父/子/上下文切片**，命中同一文档的不同 segment；
- segment gold 只标了 source chunk 本身的 ID，未把父切片全部标为 gold；
- 因此 **文档级指标更能代表“是否找到正确证据文档”**，segment 级应视为“精确切片命中率”的下界，而非检索失败。

### 4.3 其他

- 结果中出现负 segment ID（父子切片/内部 ID 形式），脚本按字符串保留；不影响文档级指标。
- 未对 topK、阈值、rerank 做任何调整；未根据结果修改 qrel。

## 5. 人工抽查表（等待你确认）

| # | Query | gold 文档/chunk | 依据原文 |
|---|---|---|---|
| 1 | 该系统在资源受限条件下解决了哪类稳定性问题？ | doc 27 / 1128e8ac | 在资源约束内最大化消除了 OOM 崩溃 |
| 2 | 哪个 Spark 配置开关用于压缩 event log 体积？ | doc 27 / 6adde8aa | `ark.sql.maxPlanStringLength=8192` 是压缩 event log 体积的关键开关 |
| 3 | Flutter 服务层为提升模拟器稳定性改进了哪些方面？ | doc 26 / 4ea7b388 | auth refresh convergence、polling interval control、caching strategies |
| 4 | 图书搜索模块的多层级匹配策略按什么优先级执行？ | doc 26 / 77f2d6c1 | 匹配按优先级从高到低依次执行：1. 精确匹配 |
| 5 | 该分布式推荐系统后端由哪些语言组件组成？ | doc 25 / 55546dd0 | Spring Boot、Go for messaging、C++ for graph processing |
| 6 | 为支持自定义 gap 参数，建议给哪个文件补充统一入口？ | doc 14 / 9ab596bf | 为 `bottom_sheet_spacing.dart` 补充统一入口，支持自定义 |
| 7 | 文档第 1 章复盘安卓应用内更新链路稳定性时，分析了哪两种现象？ | doc 13 / e5c16eee | APK 安装失败和后台下载中断 |
| 8 | Day 12 工作日志从占位态升级为什么状态，并实现了哪三项标准化输出？ | doc 12 / 59fc33bb | 向“工程化记录态”升级，实现“结构化台账 + 可视化流程 + 风险追踪” |

请确认：**“上述 8 条 query 均能由对应依据原文回答，且未看到检索结果后反推 gold。”** 回复“抽查通过”后，P0-16 即可闭环；若发现某条不成立，我只改该条标注并重新冻结后重跑。

## 6. 限制与结论

- 30 条为小样本；CI 较宽，不应把差异包装成统计显著性。
- qrel 为 known-item/provenance-derived 自动生成，存在“query 与 source 同源”的乐观偏差；文档级 Recall/MRR 宜作为**真实链路可复现性证据**，不等同于人工裁决的检索质量基准。
- **结论**：真实链路已产出可复现、非退化的检索指标（Doc Recall@10=0.9667，MRR@10=0.9278）；P0-16 的“真实运行证据”已具备，**最终状态取决于第 5 节人工抽查**。
