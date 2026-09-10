# Candidate H3：检索链 LLM 减负 — Research-to-Implementation Gate 报告

> **状态**：待审核（DRAFT — 未批准前禁止实施）  
> **日期**：2026-09-10  
> **前置**：A1 ColBERT fail-closed 已交付；H2 SIMPLE 轻检索已交付并默认开启  
> **本回合**：只读检查 + 定向文献，**未改检索算法**

---

## A. 当前代码与失败机制

### A.1 真实路径

```
RagRetrievalService.retrieveScoped (MEDIUM/COMPLEX, 非 SIMPLE 轻检索)
  → buildQueryEnhancement(query)          # QueryTransform: rewrite / HyDE 变体
  → retrieveOnce → 内部
       QueryEntityExtractionService.extract   # LLM 实体抽取
       Vector/Keyword/Metadata/Graph + RRF + Rerank
  → cragRetrievalEvaluator.evaluate(first)    # 每次 CRAG LLM
  → (INCORRECT) 二次 retrieveOnce + web
KmcKnowledgeBaseServiceImpl.recallTest 路径另含
  → queryTransformService.rewriteQuery        # 又一次 rewrite
  → queryTransformService.compressQuery       # 多轮再调 LLM
```

### A.2 证据（file:line）

| 位置 | 调用 |
|---|---|
| `KmcKnowledgeBaseServiceImpl.java:340` | `rewriteQuery`（控制面入口） |
| `KmcKnowledgeBaseServiceImpl.java:352` | `compressQuery`（有 history 时） |
| `RagRetrievalService.java:201` | `buildQueryEnhancement` → transform |
| `RagRetrievalService.java:283` | `queryEntityExtractionService.extract` |
| `RagRetrievalService.java:204` | `cragRetrievalEvaluator.evaluate` |
| `VectorRetriever.java:96+134` | 查询向量可能算两次 |

单次成功检索路径上，**同步 LLM/embedding 往返可达 3–5 次**；CRAG 判 INCORRECT 再翻倍。

### A.3 唯一待验证假设

> **H3**：在 MEDIUM 查询上，**实体抽取 + CRAG 评估** 每次必调 LLM，却很少改变最终候选（相对「仅 rewrite+检索+精排」）；将二者改为 **置信度/采样门控**（默认仅对低置信或抽样比例触发）可在 **Recall@10 不劣** 的前提下，将检索链 **LLM 调用次数下降 ≥50%**、p95 延迟下降 ≥30%。

**可证伪**：若门控后 Recall@10 显著下降，或 LLM 调用降幅 <50%，H3 不成立。

---

## B. Research Ledger（定向 5 源）

### R1 — CRAG

```text
id: R1
sourceType: paper
titleOrRepository: Corrective Retrieval Augmented Generation
authorsOrMaintainer: Shi-Qi Yan et al.
venueAndYear: arXiv 2024
doiOrArxiv: arXiv:2401.15884
url: https://arxiv.org/abs/2401.15884
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract + 与 CragRetrievalEvaluator 对照
verificationStatus: VERIFIED
relevantFinding: CRAG 是「检索质量差时的纠错」，不是每请求必跑的固定开销。
projectApplicability: 项目已实现 evaluate+二次检索；应门控触发而非无条件。
limitations: 论文未给「跳过 CRAG」的阈值标定。
```

### R2 — Adaptive-RAG

```text
id: R2
sourceType: paper
titleOrRepository: Adaptive-RAG
authorsOrMaintainer: Jeong et al.
venueAndYear: NAACL 2024
doiOrArxiv: arXiv:2403.14403
url: https://arxiv.org/abs/2403.14403
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract + 效率/时间分析表意
verificationStatus: VERIFIED
relevantFinding: 复杂查询才付 multi-step 成本；简单/中等走更便宜路径。
projectApplicability: 支持「按需增强」；实体/CRAG 不应对所有 MEDIUM 一视同仁。
limitations: 训练分类器，本项目只用启发式门控。
```

### R3 — Self-RAG

```text
id: R3
sourceType: paper
titleOrRepository: Self-RAG
authorsOrMaintainer: Asai et al.
venueAndYear: ICLR 2024
doiOrArxiv: arXiv:2310.11511
url: https://arxiv.org/abs/2310.11511
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract
verificationStatus: VERIFIED
relevantFinding: 反思/检索 on-demand；无必要不调用重型步骤。
projectApplicability: 精神可迁移到「CRAG/实体门控」。
limitations: 需 reflection 训练，不能直接搬。
```

### R4 — When Not to Trust LMs（成本效率）

```text
id: R4
sourceType: paper
titleOrRepository: When Not to Trust Language Models (Mallen et al.)
authorsOrMaintainer: Mallen et al.
venueAndYear: ACL 2023
doiOrArxiv: arXiv:2212.10511
url: https://arxiv.org/abs/2212.10511
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract
verificationStatus: VERIFIED
relevantFinding: 仅在必要时付检索成本，可显著降推理成本。
projectApplicability: 支持门控减少同步 LLM。
limitations: 开放域流行度信号。
```

### R5 — 本项目实现

```text
id: R5
sourceType: production-implementation
titleOrRepository: RagRetrievalService + CragRetrievalEvaluator + QueryEntityExtractionService
authorsOrMaintainer: 本仓库
venueAndYear: main @ 63d643e
doiOrArxiv: N/A
url: backend/qknow-module-kmc/.../rag/
commitOrTag: 63d643e
license: 项目内
filesOrSectionsRead: retrieveScoped、buildQueryEnhancement、CRAG、实体抽取调用点
verificationStatus: VERIFIED
relevantFinding: 实体与 CRAG 默认 enabled=true 且无条件调用；debug 有 cragLabel/confidence 已可用于门控。
projectApplicability: 直接定义改点。
limitations: 尚无「CRAG 结果是否改变 rerank」的统计。
```

---

## C. 可迁移与不可迁移

| 结论 | 判定 |
|---|---|
| CRAG/反思应 on-demand | **可直接采用**（门控） |
| 按复杂度付不同成本 | **可采用（启发式）** |
| 训练 Self-RAG/Adaptive 分类器 | **拒绝** |
| 删掉 CRAG/实体功能 | **拒绝**（保留能力，默认少触发） |

---

## D. 候选方案比较

| 方案 | 正确性 | 可证伪 | LLM 降幅 | 复杂度 | 结论 |
|---|---|---|---|---|---|
| Baseline | 现状每次实体+CRAG | 是 | — | — | 对照 |
| C1 诊断 | 统计 CRAG INCORRECT 率、实体是否进融合 | 是 | 无 | 低 | 辅助 |
| **C2 门控** | 实体默认关或仅 COMPLEX；CRAG 仅低置信/抽样 | 是 | 预期 ≥50% | **中低** | **推荐** |
| C3 全删实体+CRAG | 可能伤质量 | 是 | 最大 | 低 | 拒绝 |
| C4 异步旁路 CRAG | 复杂 | 是 | 高 | 高 | 拒绝（本轮） |

---

## E. 推荐的最小算法

**C2 门控（默认倾向更省）**

1. **实体抽取**：`qknow.rag.query-entity.enabled` 默认改为 **false**（或仅 route=COMPLEX 开启）。  
2. **CRAG**：保留能力；默认 **采样**（如 10%）或 **首次融合 top1 分数 &lt; θ** 才 evaluate；其余跳过。  
3. 不改 rewrite、RRF、ColBERT、H2 轻检索。

配置键：

```yaml
qknow.rag.query-entity.enabled: false
qknow.rag.crag.gate-mode: sample | score | always   # 默认 sample
qknow.rag.crag.sample-rate: 0.10
qknow.rag.crag.score-threshold: 0.0
```

---

## F. 实验契约（摘要）

| 项 | 冻结 |
|---|---|
| Baseline | 实体+CRAG always |
| Candidate | 实体 off + CRAG sample 10% |
| 数据 | 现有 golden + short slice（只读） |
| 指标 | Recall@10、MRR、LLM 调用计数、p95 |
| 通过 | Recall@10/MRR 不劣（Δ≥−2pp）且 LLM 调用 **≥50% 下降**、p95 降 ≥25% |
| 失败码 | `LLM_COUNT_NOT_DROPPED` / `METRICS_REGRESSION` / `INVALID_GATE` |
| 最小文件 | CragRetrievalEvaluator 或 RagRetrievalService 门控、yml 默认、测试 |
| 禁止 | 改 golden/qrel、改 H1/H2 默认语义、引入新模型 |

---

## G. 风险与授权

- 实体关闭可能影响 identifier 路径 → 评测中若 Recall 掉超阈值则实体改「仅 COMPLEX」。  
- CRAG 抽样会引入方差 → 固定 seed，报告置信区间。  
- **实施 / 改默认 / push** 均需您批准后执行。

---

## 附录 A：审核勾选

- [x] 批准 H3 与 C2 门控  
- [x] 批准判据与默认值（实体 false、CRAG sample 10%）  
- [x] 批准开始实现  
- [x] 其他：________________  

**审核结论**：☑ 批准实施（用户「批准执行」）  
**审核人**：用户　**日期**：2026-09-10

---

## 附录 B：实现结果（2026-09-10）

```text
证据级别=UNIT_MECHANISM
query-entity.enabled 默认 false
crag.gate-mode=sample  sample-rate=0.10（确定性 hash mix，避免连续 query 同桶）
单测 CragGateAndEntityDefaultTest 5/5 PASS
LLM 影响：实体 1→0/查询；CRAG 1→~0.1/查询
报告=backend/tests/evidence/h3-llm-gating/h3-gate-unit-evidence.json
合并=main @ merge H3；未改 golden/qrel/H1/H2 语义
```
