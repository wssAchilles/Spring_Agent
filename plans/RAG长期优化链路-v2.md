# Knowledge Hub 长期优化链路 v2

> **版本**：v2  
> **日期**：2026-09-10  
> **前置**：v1（H0–H12）已闭环并 push 至 `cef6ef3`  
> **依据**：`AGENTS.md` Research-to-Implementation Gate  
> **性质**：长期路线图；每个候选仍须**单独过门禁**

---

## 0. 与 v1 的关系

### 0.1 v1 结论（不可重复劳动）

| 结论 | 含义 |
|---|---|
| Holdout **vector Hit@10 = 1.00** | 当前 12 条 holdout 对检索算法几乎无区分度 |
| H1 ColBERT 伪向量 fail-closed | 无真 token embedding 时禁止 hash 粗排 |
| H6：child **128 优于 256**（子集） | 不盲目加大切块 |
| H10 GraphRAG：图不增益 | 默认关 |
| H12 colbertv2 MaxSim = ANN | 不替换 ANN；本地模型可删 |

### 0.2 v2 核心判断

**主要瓶颈已不在「再换一种检索算法」**，而在：

1. **评估与证据**：golden/holdout 过小、无线上真实 query 回放  
2. **生产工程**：可观测、CI、安全、压测  
3. **Agent/生成侧**：答案质量、工具、流式与成本  
4. **数据运维**：索引健康、备份、embedding 模型一致性  

v2 仍用门禁编号 **P / Q / R…**，避免与 Hn 混淆。

---

## 1. 执行纪律（同 v1）

```text
只读核对真实路径
  → 锁定唯一假设
  → 文献 3–6 条 Ledger
  → 最小候选 + 比较
  → decision-complete 契约
  → 用户批准
  → 最小实现 + 证据（边跑边 commit）
  → 合入 main
  → 另批：改默认 / A/B / push / 线上
```

**铁律**：不改 qrel 掩盖失败；证据分级（UNIT &lt; MECHANISM &lt; LIVE &lt; PROD）；大文件不进 git。

---

## 2. 阶段总览

```text
[阶段 E · 评估地基]     Q0 真实 query 日志集
                       Q1 holdout 扩容与分层
                       Q2 生成侧指标（faithfulness/引用）
[阶段 P · 生产工程]     P0 Actuator + 指标
                       P1 CI 门禁（compile+关键单测+密钥扫描）
                       P2 安全 hardening（SSRF/API Key/SSE）
                       P3 备份与恢复演练
[阶段 A · Agent 侧]    R0 工具结果裁剪与超时
                       R1 答案引用完整性（ragContext 已接线后）
                       R2 成本/延迟仪表
[阶段 D · 数据运维]     S0 embedding 模型/维度漂移检测
                       S1 索引健康与重建脚本
                       S2 冷启动：空库/短库行为
[阶段 N · 原生加速]     N0 JNI 基线与基准（已有 Rust 工具）
                       N1 MaxSim/向量批量核（Rust）强化
                       N2 Tantivy 中文 BM25 服务化
                       N3 C++/ONNX 可选：本地 rerank/encoder
                       （Swift：见 §N 说明，默认不做）
```

依赖：**Q0/Q1 优先**（否则 PROD 证据不可信）；P0/P1 可与 Q 并行；R、S 在 Q 有最小 holdout 后再开算法类改动；**N 阶段**可与 P 并行，但收益必须用 Q1 holdout + p95 证明，禁止「为了快而快」。

### §N 多语言加速：为什么是 Rust / C++，而不是 Swift

| 语言 | 在本仓库的角色 | 建议 |
|---|---|---|
| **Rust** | 已有 `backend/tools/{jieba,vecsim,colbert}-jni` + `tantivy-server` | **主路径**：JNI 边界清晰、无 GC、易做 SIMD 批量 MaxSim / trgm / BM25 |
| **C++** | 未主导 | **可选**：若必须接 ONNX Runtime / faiss 官方 C++ API 再引入 |
| **Swift** | 无 | **默认不做**：服务端是 Java/Spring，Swift 对 Linux 容器与 JNI 无优势；若将来做 macOS 原生客户端再评估 |
| **Python** | `rerank_server.py` 已有 | 保持 sidecar；热路径不走 Python |

**思想边界（AGENTS.md）**：原生层只加速**已验证的热路径**（MaxSim、批量点积、分词、trgm），**不**用新语言重写业务编排；每个 N 候选仍是独立假设 + 基准契约。

---

## 3. 候选队列

### Q0 — 真实 Query 回放集（P0）

| 字段 | 内容 |
|---|---|
| **假设** | 当前 golden/holdout 为合成/课程文档题，与线上短问、错别字、指代分布不同；无真实 query 时算法 A/B 结论外推弱。 |
| **做法** | 从 `kmc_knowledge_recall_log` / 会话表导出脱敏 query（或用户授权的样本）；冻结为 `rag-real-queries-v1.jsonl`（只增不改）。 |
| **通过** | ≥50 条、分 short/medium/long；抽样人工可读。 |
| **禁止** | 导出后按结果改 query 文本。 |

---

### Q1 — Holdout 扩容（P0）

| 字段 | 内容 |
|---|---|
| **假设** | n=12 时 vector Hit@10=1.00，无法区分改进；扩到 **n≥40** 且加入「难负例文档」后才能分层。 |
| **做法** | 在 v1 `rag-eval-holdout-v1` 上追加（不改旧 qrel）；标签：short / medium / cross-doc / 指代。 |
| **通过** | 扩容后 baseline Hit@10 **&lt; 0.95** 或存在至少 5 条失败案例可追踪。 |
| **依赖** | Q0 可选；纯人工也可。 |

---

### Q2 — 生成侧评估（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 检索 Hit 打满后，用户感知质量取决于 **是否引用对文档、是否幻觉**；需 answer-level 指标。 |
| **做法** | 对 holdout 问题固定模型跑答案；记录：引用 documentName 命中率、空答率、可选 LLM judge（抽样）。 |
| **通过** | 指标脚本可复现；与 ragContext 注入前后对比（H4a 已接线）。 |
| **禁止** | 无 holdout 就宣称「答案质量提升」。 |

---

### P0 — 可观测性（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 无 p95/错误率/池水位，无法证明线上无回归。 |
| **做法** | `spring-boot-starter-actuator` + micrometer-prometheus；Timer：`rag.retrieve`、`agent.chat`、`hermes.grpc`。 |
| **通过** | `/actuator/prometheus` 含上述指标；本地起服务可抓取。 |
| **风险** | 暴露端口需鉴权/仅内网。 |

---

### P1 — CI 门禁（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 无 CI 时 46+ 提交链路依赖人工记忆，易回归。 |
| **做法** | GitHub Actions：JDK17 `mvn -pl tests -am test`（或至少 compile + 关键单测）；gitleaks；前端可选 build。 |
| **通过** | PR 上红/绿可见；密钥扫描无高危。 |

---

### P2 — 安全 Hardening（P0，可与 Q 并行）

来自首轮审查、**尚未进 v1 算法链** 的项：

| ID | 问题 | 最小动作 |
|---|---|---|
| P2a | SQL 种子/历史凭证 | 轮换 + 占位符 + 忽略规则（历史需 filter-repo 另批） |
| P2b | `HttpRequestToolFunction` SSRF | 禁私网/链路本地/metadata IP |
| P2c | Security ASYNC permitAll 等 | 收紧 ASYNC 与 `/syncData/**` |
| P2d | 前端 `v-html` XSS | DOMPurify 或 `html:false` |
| P2e | API Key 明文返回 | 恢复脱敏 |

**门禁**：每项单独 PR + 单测；**不**与算法候选混提。

---

### P3 — 备份与恢复（P1）

| 字段 | 内容 |
|---|---|
| **做法** | `scripts/backup.sh`：pg_dump + 可选 Neo4j dump + `vector_store` 导出；文档化恢复步骤。 |
| **通过** | 在测试库 restore 一次成功。 |

---

### R0 — 工具调用治理（P2）

| 字段 | 内容 |
|---|---|
| **假设** | 工具结果无长度上限/超时，会拖垮上下文与延迟。 |
| **做法** | 统一 maxChars、timeout、失败重试；与 H9 取消联动。 |
| **通过** | 单测 + 延迟报告。 |

---

### R1 — 引用完整性（P2）

| 字段 | 内容 |
|---|---|
| **假设** | H4a 后 Agent 用 ragContext，但**前端/引用 UI**是否展示 documentName 未验证。 |
| **做法** | 端到端：有 ragContext 时 sourceRefs 与 context 内来源一致率。 |
| **通过** | 一致率 ≥ 约定阈值或明确缺陷修复。 |

---

### R2 — 成本/延迟仪表（P2）

| 字段 | 内容 |
|---|---|
| **做法** | 基于 P0 指标：LLM 调用次数、token、p95；对照 H3/H8 改造前后。 |
| **通过** | 报告写入 `backend/tests/evidence/r2-cost/`。 |

---

### S0 — Embedding 漂移检测（P1）

| 字段 | 内容 |
|---|---|
| **假设** | 库内已为 `text-embedding-v4` 1024d；KB 配置若仍指向旧模型/1536d 会混写或静默失败。 |
| **做法** | 启动或定时任务：校验 `kmc_knowledge_base.embedding_model` 与 `vector_store` 维度；告警。 |
| **通过** | 故意错配时告警可测。 |

---

### S1 — 索引健康与重建（P1）

| 字段 | 内容 |
|---|---|
| **做法** | `scripts/reindex-vector-store.sh`（幂等、按 KB、断点续跑）；HNSW 状态查询。 |
| **通过** | 小库演练：删 embedding → 脚本恢复 count。 |
| **禁止** | 无备份时 DROP EXTENSION CASCADE（v1 事故教训）。 |

---

### S2 — 冷启动行为（P2）

| 字段 | 内容 |
|---|---|
| **假设** | 空 `vector_store` / 无分片 KB 上 SIMPLE 轻检索、ANN 会怎样未定义。 |
| **做法** | 集成测试：空库不 500、返回空 sources、日志可诊断。 |
| **通过** | 固定用例绿。 |

---

### N0 — JNI 基线与统一基准（P1，原生轨入口）

| 字段 | 内容 |
|---|---|
| **假设** | 已有 jieba/vecsim/colbert JNI，但是否在生产路径真正 load、比 Java 快多少 **无统一数字**；「加 Rust」前必须先有基线。 |
| **做法** | 一次性基准：同输入下 Java fallback vs JNI（分词 QPS、1024d 批量点积、MaxSim 5k 段）；`backend/tests/evidence/n0-jni-bench/`；可选 JMH。 |
| **通过** | 报告含 p50/p95 与相对加速比；JNI 不可用时 fallback 仍正确（已有 RagFallbackMonitor）。 |
| **禁止** | 未测就宣称「Rust 提升 10×」。 |

---

### N1 — MaxSim / 向量批量核强化（P2）

| 字段 | 内容 |
|---|---|
| **假设** | 在 **真 token 向量**（非 hash）上，Rust SIMD MaxSim 比 Java 双层循环显著更快，且不改排序语义。 |
| **做法** | 强化 `vecsim-jni` / `colbert-jni`：批量 + 多线程 + 对齐内存；契约：与 Java 实现 **分数误差 &lt; 1e-4**，排序一致。 |
| **通过** | N0 基准上再测；质量 A/B 与 Java 相同 Hit@10。 |
| **依赖** | H1 fail-closed；有真 embedding 时才有意义（H12 用过本地 MaxSim）。 |
| **禁止** | 用 hash 伪向量当「Rust 加速成功」证据。 |

---

### N2 — Tantivy 中文 BM25 服务化（P2）

| 字段 | 内容 |
|---|---|
| **假设** | 现 keyword 靠 pg_trgm + ILIKE；`tantivy-server` 已有但主路径未接。Rust BM25 对中文/长库可降低 p95 并提升难词 Hit@10。 |
| **做法** | 索引同步（增量）；KeywordRetriever 增加 Tantivy 后端开关；与 pg 路径 A/B（Q1 holdout）。 |
| **通过** | Hit@10 不劣且 p95 下降，或 Hit@10 上升且延迟可接受。 |
| **依赖** | Q1 扩容 holdout；同步一致性方案。 |

---

### N3 — C++/ONNX 可选轨（P3）

| 字段 | 内容 |
|---|---|
| **假设** | 仅当需要 **本地 cross-encoder / token encoder** 且 Java 生态不足时，才引入 C++ ONNX Runtime。 |
| **做法** | 先证明：现有 Python rerank sidecar 或已交付指标不够；再 PoC 延迟/内存。 |
| **通过** | PoC 报告；否则 **拒绝引入 C++**。 |
| **禁止** | 为技术栈完整度而引入。 |

---

### N-Swift — 明确拒绝清单

| 项 | 决定 |
|---|---|
| 服务端 Swift 重写 / Swift JNI | **拒绝**（无 Linux/JNI 收益，违背最小算法） |
| 若仅 macOS 客户端 | 另立客户端路线图，不进本仓库 RAG 热路径 |

---

## 4. 建议排期

| 迭代 | 内容 | 出口 |
|---|---|---|
| **V2-S1（1 周）** | Q0+Q1 扩容 + S0 漂移检测 | holdout n≥40；错配可测 |
| **V2-S2（3–5 天）** | P0 指标 + P1 CI 最小 | actuator + Actions 绿 |
| **V2-S3（1 周）** | P2 安全五项分批 | 每项有测试 |
| **V2-S4** | P3 备份 + S1 重建 | 演练记录 |
| **V2-S5** | Q2 生成评估 + R1 引用 | evidence 目录 |
| **V2-S6+** | R0/R2/S2 | 按需 |

---

## 5. 指标字典（v2 增补）

| 指标 | 定义 |
|---|---|
| **真实 Hit@10** | 在 Q0/Q1 扩容 holdout 上的 Evidence Hit@10 |
| **引用一致率** | 答案/前端展示 documentName ∈ 检索 sources 的比例 |
| **空答率** | 无 sources 仍生成答案的比例 |
| **rag.retrieve.p95** | P0 Timer |
| **embed 维度漂移** | KB 配置维度 ≠ 库内向量维度的 KB 数 |

---

## 6. 拒绝清单（v2 增补）

| 已否决/禁止 | 理由 |
|---|---|
| 在 n=12 holdout 上宣称检索 SOTA | 无区分度 |
| 为过线改 qrel | v1 铁律 |
| 再次对 Postgres 扩展 CASCADE 不备份 | v1 事故 |
| ColBERT/Graph 无证据默认打开 | v1 实验结论 |
| 大模型 checkpoint 提交进 git | 磁盘与仓库污染 |

---

## 7. 候选模板（复制到 `plans/YYYY-MM-DD-candidate-qN-*.md` 等）

同 v1：A 唯一假设 → B Ledger → C 适用性 → D 比较 → E 最小算法 → F 契约 → G 风险授权 → 附录勾选与结果。

---

## 8. 完成状态总表

| ID | 标题 | 状态 |
|---|---|---|
| Q0 | 真实 query 回放集 | Planned |
| Q1 | Holdout 扩容 | Planned |
| Q2 | 生成侧评估 | Planned |
| P0 | Actuator 指标 | Planned |
| P1 | CI 门禁 | Planned |
| P2 | 安全 hardening | Planned |
| P3 | 备份恢复 | Planned |
| R0 | 工具治理 | Planned |
| R1 | 引用完整性 | Planned |
| R2 | 成本仪表 | Planned |
| S0 | Embedding 漂移检测 | Planned |
| S1 | 索引健康重建 | Planned |
| S2 | 冷启动行为 | Planned |
| N0 | JNI 基线基准 | Planned |
| N1 | Rust MaxSim/批量核 | Planned |
| N2 | Tantivy 中文 BM25 | Planned |
| N3 | C++/ONNX 可选 | Planned（默认可拒） |
| N-Swift | 服务端 Swift | **Rejected** |

---

## 9. 回滚与运维

- 算法开关仍集中在 `qknow.rag.*` / `hermes.rag.*`。  
- 证据：`backend/tests/evidence/<id>/`；大文件 gitignore。  
- push / 上线 **每次单独授权**。

---

## 10. 建议你批准的第一批

1. **Q1 Holdout 扩容**（检索类改动的前置）  
2. **P2 安全 hardening**（不依赖算法，风险直接）  
3. **P0+P1 指标与 CI**（工程底座）

回复示例：「批准 Q1」「批准 P2」「Q1+P0+P1 连续做」。
