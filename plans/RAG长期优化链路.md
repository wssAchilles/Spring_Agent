# Knowledge Hub RAG / Agent 长期优化链路

> **版本**：v1  
> **日期**：2026-09-10  
> **依据**：`AGENTS.md` Research-to-Implementation Gate  
> **状态**：长期路线图（非单次实施契约）  
> **已交付链路**：A1/H1 ColBERT fail-closed → H2 SIMPLE 轻检索 → H3 检索链 LLM 门控  

---

## 0. 使用方式（长期执行纪律）

本文件是 **长期优化链路**，不是一次性「全部改完」的许可。每个候选 **必须单独过门禁**：

```text
只读核对真实路径
  → 锁定唯一可证伪假设 Hn
  → 定向文献 3–6 条 Research Ledger
  → 适用性 + 候选比较
  → decision-complete 契约（baseline/candidate/指标/失败码/停止）
  → 用户批准
  → 最小实现 + 消融评测
  → 合入 main
  → （另批）改生产默认 / A/B / push / 上线
```

**铁律**

| 规则 | 说明 |
|---|---|
| 一假设一轮 | 禁止一揽子改 topK+路由+CRAG+切块 |
| 不改 qrel 掩盖失败 | 失败要分清环境/实现/假设 |
| 证据分级 | UNIT &lt; MECHANISM &lt; LIVE_ANN &lt; PROD，低级证据不得直接改线上默认 |
| 默认开关 | 评测用系统属性；生产默认变更单独授权 |
| 已否决项 | 记入「拒绝清单」，禁止换名重引入 |

---

## 1. 当前基线（已交付）

### 1.1 已合入 main 的算法变更

| ID | 内容 | 证据 | 生产默认 |
|---|---|---|---|
| **H1/A1** | ColBERT 无真实 embedding 时 fail-closed 跳过粗排 | 机制消融 R@10 0.90→1.00；live ANN 质量持平 + 延迟 win | `skip-when-no-embedding: true` |
| **H2** | SIMPLE 短查询 keyword 轻检索 topK=5，零 LLM | 短切片 Hit@10 0→0.70 | `simple.light-retrieval: true` |
| **H3** | 实体抽取默认关；CRAG sample 10% | 单测 5/5；LLM 调用预期降 ≥50% | entity false + crag sample |

**证据目录**：`backend/tests/evidence/{a1-colbert-fail-closed,h2-simple-light-retrieval,h3-llm-gating}/`

### 1.2 当前生产相关默认（摘要）

```yaml
hermes.rag.colbert.skip-when-no-embedding: true
qknow.rag.simple.light-retrieval: true
qknow.rag.simple.light-top-k: 5
qknow.rag.query-entity.enabled: false
qknow.rag.crag.gate-mode: sample
qknow.rag.crag.sample-rate: 0.10
```

### 1.3 仍未闭环的系统债（算法外，可并行）

| 项 | 说明 | 类型 |
|---|---|---|
| `recallTest` 丢弃 `RagResult.getContext()` | 生产 Agent 裸拼 sources，预算/去重/父子扩展未进真实路径 | **接线缺陷**（可单独修，不进算法 Gate） |
| 多轮 history 未进 `recallTest` | 指代消解失效 | 依赖 API 形状，可先接线 |
| 检索链仍可能 2–3 次同步 LLM | rewrite 仍在；H3 只砍了实体+CRAG | 见 H4 |
| 评估 golden 仅 10 条 + 无独立 holdout | 结论强度受限 | 见 H0 评估基建 |
| CI 无 live 检索门禁 | 回归靠人工 | 见 H0 |
| embedding 模型已切 `text-embedding-v4` 1024d | 查询侧与库需一致 | 运维项 |

---

## 2. 长期链路总览

```text
[已完成]
  H1 ColBERT fail-closed
  H2 SIMPLE 轻检索
  H3 实体关 + CRAG 抽样

[阶段 0 · 地基]  ──►  H0 评估门禁与 holdout
                       │
[阶段 1 · 接线]  ──►  H4a 生产接通 RagResult.getContext()
                       H4b 多轮 history 进检索
                       │
[阶段 2 · 检索质量] ─► H5 关键词中文路径 / Tantivy
                       H6 切块参数 A/B（child 长度、overlap）
                       H7 混合融合与过滤语义（RRF 后过滤）
                       │
[阶段 3 · 成本与延迟] ► H8 rewrite 减负 / 查询向量只算一次
                       H9 gRPC 取消传播（Hermes）
                       │
[阶段 4 · 图与记忆] ─► H10 GraphRAG 启用验证或关闭
                       H11 长期记忆读路径
                       │
[阶段 5 · 真晚交互] ─► H12 ColBERTv2 / Jina 真模型（仅 H1 证据后）
```

依赖关系：**H0 优先**；H4a 可与 H0 并行；H12 必须在 H1 机制证据与 H0 指标稳定之后。

---

## 3. 候选队列（按建议执行序）

### H0 — 评估基建与 Holdout（P0，先于一切调参）

| 字段 | 内容 |
|---|---|
| **假设** | 当前仅 10 条 golden、无独立 holdout，不足以支撑「默认变更」级别的结论；补齐 **≥40 条分层短/中/长查询 + Selection/Holdout 划分 + live Recall/MRR 门禁** 后，后续 Hn 的 promotion 才可自动判定。 |
| **类型** | 基建 / 指标（仍走门禁文档，因会冻结 fixture） |
| **最小做法** | 扩展 `rag-golden` 或新建 `rag-eval` 正式集；实现 `RetrievalMetrics` live 路径；CI 可选跑 smoke 子集 |
| **通过** | Holdout n≥20；同一 commit 两次跑指标波动 &lt;2pp |
| **禁止** | 为过线改 qrel |
| **依赖** | 无 |

---

### H4a — 生产接通 `RagResult.getContext()`（P0，接线）

| 字段 | 内容 |
|---|---|
| **问题** | `KbAgentConfigServiceImpl` / `recallTest` 只取 sources 裸拼，**丢弃** `RagContextBuilder` 的预算、去重、父子扩展 |
| **类型** | 普通缺陷修复倾向；若同时改预算默认值则升为算法 Gate |
| **最小做法** | API/缓存返回或传递 `context`；Agent 注入该串；sources 仅引用 |
| **指标** | 注入 token 长度分布；超预算率；答案引用完整度 |
| **禁止** | 顺手改 max-bytes 默认、改切块 |
| **依赖** | 建议 H0 有基础指标 |

---

### H4b — 多轮 history 进检索（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 带指代的第二轮问题在无 history 压缩时 Recall@10 显著低于「改写后单轮」基线。 |
| **做法** | `recallTest` 增加 history；复用已有 `compressQuery`（注意 LLM 成本，可与 H3 门控思路一致：仅多轮时启用） |
| **禁止** | 每轮无条件 LLM 压缩 |
| **依赖** | H4a 接线 |

---

### H5 — 中文关键词路径（P1）

| 字段 | 内容 |
|---|---|
| **假设** | `plainto_tsquery('simple', 整句)` 对无空格中文几乎不命中，靠 ILIKE 兜底；用 **jieba token OR / websearch_to_tsquery / Tantivy** 可提升 short-medium 查询 Hit@10。 |
| **现状** | `KeywordRetriever` + 已实现未接入的 `TantivyClient` |
| **做法** | A/B：现 ILIKE vs jieba token；Tantivy 作二期 |
| **通过** | 中文子集 Hit@10 +10pp 且 p95 不升超过 20% |
| **依赖** | H0；与 H2 轻检索共用 keyword，注意回归 short slice |

---

### H6 — 切块策略 A/B（P2）

| 字段 | 内容 |
|---|---|
| **假设** | child=`min(maxTokens,128)` 过碎，中文嵌入语义不足；提到 256–384 可提升 context_recall。 |
| **做法** | 只改 **新文档** 或影子索引做 A/B；禁止原地改历史 chunk 不重嵌 |
| **成本** | 需 embedding 额度与重建时间 |
| **依赖** | H0；明确 embedding 模型=v4 1024d |

---

### H7 — 融合后过滤语义（P2）

| 字段 | 内容 |
|---|---|
| **假设** | `filterIrrelevant` 把 RRF 分数当「向量分数」取中位数×0.75，过滤行为不可解释且可能伤召回。 |
| **做法** | 过滤移到各路原始分，或删除只保留 RRF+精排 |
| **通过** | Holdout Recall@10 不劣；过滤误杀率下降 |
| **依赖** | H0 |

---

### H8 — 检索链 rewrite / 查询向量减负（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 控制面 `rewriteQuery` + 管线内 `buildQueryEnhancement` 可能双开；查询 embedding 可能算两次；合计仍是主要延迟源。 |
| **做法** | 入口算一次 query embedding 传入 VectorRetriever；rewrite 配置单点；与 H3 同思路门控 HyDE |
| **指标** | p95、LLM+embed 调用计数 |
| **依赖** | H3 已交付；H0 |

---

### H9 — Hermes gRPC 取消传播（P1，运行时）

| 字段 | 内容 |
|---|---|
| **问题** | 客户端断连后 ReAct/LLM 继续；无 deadline |
| **做法** | ClientCall cancel + server onCancel + `withDeadlineAfter` |
| **指标** | 断连后模型调用次数、token 浪费 |
| **类型** | 可作工程项；若改 ReAct 步数策略则升算法 Gate |
| **依赖** | 无硬依赖，可与 H4 并行 |

---

### H10 — GraphRAG：启用验证或正式关闭（P2）

| 字段 | 内容 |
|---|---|
| **假设** | `graph.enabled=false` 时 Neo4j 为摆设；开启后 PPR 是否在本库带来 Hit@10 增量未知。 |
| **做法** | 固定 seed 与子图规模；A/B；无增量则文档写明「默认关闭，非产品能力」 |
| **禁止** | 无 golden 支撑就宣传图谱检索 |
| **依赖** | H0 |

---

### H11 — 长期记忆读路径（P2）

| 字段 | 内容 |
|---|---|
| **问题** | `SleepTimeMemoryAgent` 写入，`executeAgent` 从不 `recallByScope` |
| **做法** | 短期为空时注入 top-k 长期摘要；限制长度 |
| **指标** | 跨会话指代任务成功率 |
| **依赖** | H4b；独立评测集 |

---

### H12 — 真 ColBERT / 多语 late-interaction（P3）

| 字段 | 内容 |
|---|---|
| **前提** | H1 已证明「伪向量粗排有害」；H0 有稳定 Recall@K |
| **做法** | Jina-ColBERT-v2 或 ColBERTv2 checkpoint + 文档 token 矩阵索引；**禁止**用任意 embedding API token 冒充 |
| **成本** | 索引重建、内存、可能 GPU/ONNX |
| **依赖** | H0 + H1 证据 + 独立 Gate |

---

## 4. 建议排期（示例）

| 迭代 | 内容 | 出口条件 |
|---|---|---|
| **S1（1 周）** | H0 评估集 + live 指标脚本 | Holdout 可复现跑通 |
| **S2（3–5 天）** | H4a 接线 context | Agent 注入受预算约束的 context |
| **S3（1 周）** | H8 成本减负 + H3 回归 | LLM 调用与 p95 报告 |
| **S4（1–2 周）** | H5 中文关键词 A/B | 通过或否决入拒绝清单 |
| **S5** | H4b 多轮 + H9 取消传播 | 专项指标 |
| **S6+** | H6/H7/H10/H11/H12 | 各自独立 Gate |

每迭代结束：更新本文件「完成状态」表 + evidence 目录 + 可选 push。

---

## 5. 统一指标字典（冻结术语）

| 指标 | 定义 |
|---|---|
| **Hit@K / Evidence Hit@K** | topK 内是否出现 expected documentName（或多源 any） |
| **Recall@K** | 命中 expected 源数 / expected 总数（切片级可退化为 Hit） |
| **MRR@10** | 首个相关文档排名倒数均值 |
| **NDCG@10** | 排序质量 |
| **LLM calls / query** | 检索链同步 chat 调用次数（不含最终答案生成） |
| **Embed calls / query** | 查询向量化次数 |
| **p50/p95** | 检索耗时 |
| **HASH_FALLBACK_IN_*** | 臂内禁止出现的失败哨兵 |
| **证据级别** | UNIT_MECHANISM / MECHANISM_ABLATION / LIVE_ANN / LIVE_KEYWORD / PROD |

---

## 6. 拒绝清单（禁止换名重引入）

| 已否决 | 理由 |
|---|---|
| 无 embedding 时用任意 embedding API 的 token 向量冒充 ColBERT | 非 late-interaction |
| 用 `text-embedding-v1` 做重算 | 用户额度用尽；已用 v4 |
| 为过线改 golden/qrel | 泄漏 |
| SIMPLE 一刀切空召回作为组织库默认 | H2 已否决 |
| 无 holdout 时改生产默认并宣称 SOTA | 证据不足 |
| 引入 PLAID/新向量库而无 H1/H0 证据 | 范围膨胀 |

---

## 7. 每个 Hn 的模板（复制到 `plans/YYYY-MM-DD-candidate-hN-*.md`）

```markdown
# Candidate HN：<标题>
状态 / 日期 / 依据 AGENTS.md

## A. 当前代码与失败机制（唯一假设 HN）
## B. Research Ledger（3–6 源，全字段）
## C. 可迁移与不可迁移
## D. 候选方案比较（含 baseline 与拒绝理由）
## E. 推荐最小算法
## F. 契约（数据/指标/通过/失败码/最小文件/复现命令）
## G. 风险、停止、授权边界
## 附录 A 审核勾选
## 附录 B 实现结果（T 后填）
```

---

## 8. 完成状态总表（随迭代更新）

| ID | 标题 | 状态 | 证据级别 | 生产默认 | 备注 |
|---|---|---|---|---|---|
| H1 | ColBERT fail-closed | **Delivered** | MECHANISM + LIVE_ANN | true | f6c0d31 等 |
| H2 | SIMPLE 轻检索 | **Delivered** | LIVE_KEYWORD | true | 125f788… |
| H3 | 实体关 + CRAG 抽样 | **Delivered** | UNIT_MECHANISM | 已改默认 | f4be4b5… |
| H0 | 评估基建 | **Delivered** | UNIT | — | holdout jsonl + LiveRetrievalMetrics |
| H4a | context 接线 | **Delivered** | UNIT | 有 ragContext 时优先 | 38a5d68/aad75df |
| H4b | 多轮 history | **Delivered** | UNIT | 已接线；压缩需开 transform | 219439d |
| H5 | 中文关键词 | **Delivered** | UNIT + LIVE_KEYWORD | websearch+bigram | fca26c9；短切片仍 0.70 |
| H6 | 切块 A/B | **Subset A/B Done** | LIVE embed | child-tokens 保持 128 | 子集 Hit@10: 128=0.875 vs 256=0.50 |
| H7 | 融合过滤 | **Delivered** | UNIT | post-fusion-filter 默认 false | 0d375d6/1a81541 |
| H8 | rewrite/embed 减负 | **Delivered** | UNIT | query-transform 默认关 | 38256ad/9a6478d |
| H9 | gRPC 取消 | **Delivered** | UNIT | deadline 120s + onDispose cancel | 0d375d6 |
| H10 | GraphRAG | **A/B Done — Keep Off** | LIVE | enabled=**false** | 12 holdout: vector Hit@10=1.00 vs graph+kw=0.42；证据 h10-graph/ |
| H11 | 长期记忆读 | **Delivered** | UNIT | recall-on-empty=true topK=3 | 582fa2c |
| H12 | 真 ColBERT | Deferred | — | H1 fail-closed 已挡伪向量 | 真 checkpoint/索引另批 |

---

## 9. 运维与回滚

- 所有算法开关集中在 `qknow.rag.*` / `hermes.rag.*`，改 yml 或 `@Value` 默认即可回滚。  
- 证据 JSON 一律进 `backend/tests/evidence/<candidate>/`，与计划附录 B 一一对应。  
- 大规模重嵌 / 索引重建前：备份 `vector_store`（**禁止**再对扩展做 CASCADE 而不备份）。  
- push / promotion / 线上灰度 **每次单独授权**。

---

## 10. 下一步（建议你直接批准的动作）

1. **批准 H0**：先立评估门禁（只增数据与指标，不改算法）。  
2. **批准 H4a**：接通生产 `getContext()`（接线，风险低）。  
3. 二者完成后，再按 S3+ 推进 H8/H5 等。

回复示例：「批准 H0」「批准 H4a」「按 S1–S2 连续做」。
