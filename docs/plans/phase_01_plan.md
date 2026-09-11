# Phase 01 — 评估地基（Q0 + Q1 + 指标 Runner）

> **状态**：Designed（**未编码**，待批准后 TDD 落地）  
> **日期**：2026-09-10  
> **总计划**：`plans/RAG长期优化链路-v2.md`（阶段 E：Q0/Q1）  
> **流程**：双路研读完成 → 本方案 → 批准 → 先测后码 → 全绿 → 更新总索引  
> **性质**：评估数据与指标基建；**不改检索算法默认值**

---

## 1. 唯一假设（本阶段）

> **H-P01**：现有 `rag-eval-holdout-v1`（n=42，holdout 仅 17，无 `lang`/`answerable`，文档级 Hit）在中文组织库上**区分度不足**（medium holdout vector Hit@10=1.00）；升级为 **v2 统一 schema + 分层 holdout（含 negative）+ 分层宏平均 + MRR@10** 后，baseline 在至少一层上出现可追踪失败（Hit@10&lt;0.95 或 short-zh 维持约 0.7），从而为 Phase 05+ 算法候选提供可证伪出口。

**可证伪**：若扩容后所有层 Hit@10 仍 ≥0.95 且无失败案例，则承认「当前库+查询分布」无法区分检索算法，Phase 05/07 申请保持 blocked。

---

## 2. 文献支撑证据链

| 来源 | 结论 | 用于本阶段 |
|---|---|---|
| BEIR 2104.08663 | 异构分层；in-domain 调分 ≠ 零样本泛化 | stratum 定义；禁止用 selection 对外承诺 |
| Bruch et al. 2210.11934 | train/selection 只调参；eval 固定；paired t-test | selection/holdout 纪律；显著性脚本 |
| RGB 2309.01431 | 负例=相关但不含答案；拒答难 | **S4 negative** 必建 |
| RAGChecker 2408.08067 | 检索/生成分侧诊断 | Phase 01 只锁 IR；answer-level → Phase 08 |
| CRUD-RAG 2401.17043 | 中文 RAG 非仅 QA | Q0 意图标签预留 |
| 开源：Ragas / RAGChecker / LlamaIndex eval | production→dataset；batch runner；schema | 导出与 Runner 设计，**不引入 Python 依赖** |

完整 Ledger 见会话内 general-9 / general-10 报告；验收时以本表 + 仓库证据为准。

---

## 3. 现状差距（只读证据）

| 项 | 现状 | 目标 |
|---|---|---|
| holdout n | 17（全 42=25+17） | holdout **≥40**；selection ≥30 |
| stratum | short/medium（按长度） | + `multihop`/`negative`/`rewrite`；`lang` 字段 |
| 中文占比 | ~8/42 | holdout 中 **zh ≥ 60%** |
| 负例 | **0** | negative **≥8** |
| 指标 | 有 Hit/MRR/NDCG，**无分层宏平均、无配对检验** | 分层 + paired bootstrap/t-test |
| hit 逻辑 | `SimpleLightRetrievalGateTest` 私有 `hit()` 与 `LiveRetrievalMetrics` **不一致** | 统一到 Metrics |
| 双数据体系 | jsonl 文档级 vs `rag-eval/` 段落级未打通 | Phase 01 **只统一 jsonl 文档级契约**；段落级桥接列入 Phase 01 可选 T7 |
| 生产导出 | `kmc_knowledge_recall_log` 仅 query TEXT | 脱敏导出候选 → 人工标注 |

---

## 4. 设计

### 4.1 Schema v2（冻结文件 `rag-eval-v2.jsonl`）

```json
{
  "id": "eval-hold-zh-001",
  "query": "会话长度拦截器是做什么的？",
  "lang": "zh",
  "stratum": "short|medium|multihop|negative|rewrite",
  "split": "selection|holdout",
  "answerable": true,
  "kbId": 8,
  "expectedSources": ["大数据.pdf"],
  "expectedSegments": [],
  "familyId": "optional-zh-en-pair",
  "tags": ["from-v1", "manual"]
}
```

- **只增不改**：v1 的 42 条迁入 v2 时保留原 id/qrel；新条目新 id。  
- `negative`：`expectedSources: []`，`answerable: false`；Hit 记「不应命中已知无关源」或记 empty-hit（实现时在 Runner 中单独分支）。  
- 文件头可加注释行：`# frozen: YYYY-MM-DD`。

### 4.2 数据流

```text
[kmc_knowledge_recall_log] --export+anonymize--> [candidate-*.jsonl]
                                                      ↓ 人工标注
                                              rag-eval-v2.jsonl (frozen)
                                                      ↓
[RetrievalEvalRunner] → 按 split/stratum → LiveRetrievalMetrics
                                                      ↓
[Aggregate report] → backend/tests/evidence/phase01-eval/*.json
```

### 4.3 指标

| 指标 | 定义 |
|---|---|
| Hit@5 / Hit@10 | 文档名前缀匹配（沿用 `matchesSource`） |
| MRR@10 / NDCG@10 | 现有实现 |
| **StratumMacro** | 各层等权平均，防 medium 淹没 short |
| **PairedTest** | 两系统同 query：paired bootstrap 1000 次或 Wilcoxon；输出 p 与 95% CI |

Negative 层：报告 **false-positive rate**（错误命中目标文档比例），不是 Hit@10。

---

## 5. 模块接口

### 5.1 `RetrievalEvalRunner`（tests）

```java
public final class RetrievalEvalRunner {
  public record CaseInput(String id, String query, String stratum, boolean answerable,
                          List<String> expectedSources, List<String> retrievedDocs) {}
  public record CaseResult(String id, double hit5, double hit10, double mrr10, double ndcg10) {}
  public record Report(int n, Map<String, LiveRetrievalMetrics.Aggregate> byStratum,
                       LiveRetrievalMetrics.Aggregate overall, double negativeFpRate) {}

  public static CaseResult score(CaseInput in);
  public static Report aggregate(List<CaseInput> inputs);
  public static Map<String, Object> pairedCompare(List<double[]> perQueryMetricA,
                                                   List<double[]> perQueryMetricB);
}
```

- 复用 `LiveRetrievalMetrics.score/aggregate`；**禁止**再写私有 `hit()`。  
- Loader：`EvalDatasetSplitTest` 扩展或新建 `RagEvalV2Loader` 校验必填字段与 split 计数。

### 5.2 导出脚本（可选 T5）

- `scripts/eval/export_from_recall_log.py` 或纯 SQL+人工：脱敏规则 PHONE/EMAIL/ID；`split=candidate` 待标。  
- **停止条件**：脱敏后仍可识别 &gt;20% 则暂停并加强规则。

---

## 6. 文件级编码 Todo（TDD）

| ID | 文件 | 动作 | 验收 |
|---|---|---|---|
| T1 | `backend/tests/src/test/resources/rag-eval-v2.jsonl` | 由 v1+short-slice 迁移并补齐 lang；**先写 Loader 单测再写数据** | 文件可解析；id 唯一；v1 全部 id 仍在 |
| T2 | `.../rag/eval/RagEvalV2Loader.java` | load + 校验必填字段/stratum 枚举 | 单测：缺字段抛错；negative 允许空 expected |
| T3 | `.../rag/LiveRetrievalMetrics.java` | 可选：抽出 package-private 匹配；**加 `aggregateByStratum`** | 单测：宏平均 ≠  overall 被 medium 拉偏时可区分 |
| T4 | `.../rag/eval/RetrievalEvalRunner.java` | score/aggregate/negative FPR | 单测：与手工算例一致 |
| T5 | `SimpleLightRetrievalGateTest` | **删除私有 hit()**，改调 Metrics/Runner | 行为不回归（A0/A1 仍 0 / 0.7） |
| T6 | `EvalDatasetSplitTest` | 扩展 v2：holdout≥40、每主层≥8、negative≥8、zh≥60% | **扩容前可先 skip/xfail**；扩容后强制 |
| T7 | （可选）`scripts/eval/export_from_recall_log.py` | 脱敏导出 | 无密钥入库；样例 JSON 合法 |
| T8 | `backend/tests/evidence/phase01-eval/` | baseline 分层报告 JSON | 命令可复现写入 README |

**顺序**：T3→T4（测）→ T4 实现 → T2 Loader 测+实现 → T1 数据扩容（人工/标注，可与码并行）→ T5 → T6 → T8。

---

## 7. 测试与验收准则

### 单元测试

- [ ] Metrics：分层宏平均、negative FPR、Day01 前缀匹配  
- [ ] Runner：与 3 条手工算例一致  
- [ ] Loader：v2 schema 校验  
- [ ] SimpleLightRetrievalGate 回归仍绿  

### 集成 / Gate

- [ ] `mvn -pl tests -am -Dtest=EvalDatasetSplitTest,SimpleLightRetrievalGateTest,... test` 绿  
- [ ] holdout 扩容后：`EvalDatasetSplitTest` 全量断言通过  
- [ ] `RetrievalEvalRunner` 在 **ANN baseline**（text-embedding-v4）上产出分层 JSON 并 commit evidence  

### 通过判据（本阶段）

1. v2 文件冻结；v1 qrel 未改。  
2. 分层脚本可复现。  
3. baseline 报告：至少一层 Hit@10&lt;0.95 **或** 明确记录「全层饱和」并阻断 Phase 05 申请。  
4. 不改任何生产检索默认。

---

## 8. 风险与停止

| 风险 | 缓解 |
|---|---|
| 标注泄漏/分布偏 | 脱敏 + 人工 review；zh 优先 |
| 全层饱和无法测算法 | 如实写 INSUFFICIENT；不伪造失败 |
| 双指标体系 | 文档级为本阶段唯一契约 |

**停止**：需改旧 qrel 才能过线；导出含敏感信息无法脱敏。

---

## 9. 批准后编码纪律

- Workspace：`feat/phase01-eval-foundation` worktree  
- TDD：每个 T* 先测后实现  
- 证据边跑边 commit 到 `backend/tests/evidence/phase01-eval/`  
- 完成后更新 `docs/plans/00_master_index.md` 状态 → Delivered  

---

## 10. 审核勾选

- [ ] 批准 H-P01 与范围（仅评估地基）  
- [ ] 批准 schema v2 与分层定义  
- [ ] 批准文件级 todo 与验收  
- [ ] 批准开始 T1–T8（不含改算法默认）  

**审核结论**：☐ 批准编码　☐ 驳回/修改　☐ 仅批准先做 T7 导出  
**审核人**：________　**日期**：________
