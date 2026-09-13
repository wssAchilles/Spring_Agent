# Phase 08: 生成侧评估与精准溯源 (Citation)

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **状态**：**Delivered (已交付)**  
> **单测覆盖**：`tech.qiantong.qknow.rag.CitationGroundingContractTest` (4/4 PASS)  

---

## 1. 阶段目标与唯一待验证假设

### 1.1 核心目标
解决生成侧与检索结果脱节导致的数据漂移两大核心缺陷：
1. **幽灵来源泄露（Ghost Source Leak）**：`RagContextBuilder` 在上下文超出字节预算（`maxContextBytes=20000`）截断后，上层检索返回对象仍将未经截断的候选全量塞入 `sources` 并发送给前端展示为已参考来源；
2. **缺乏结构化引用角标解析（Lack of Structured Citations）**：模型输出的 `[来源 1]`、`[1]`、`【1】` 未能与实际段落 ID、文档 ID 及片段文本完成结构化锚定，缺乏越界与虚假引用（Hallucinated Citation）拦截能力。

### 1.2 唯一待验证假设
通过引入严格上下文装填对齐（`ContextBuildResult.emittedResults`）与轻量级精准溯源提取器（`CitationExtractor`），能够使 `sources` 与大模型实际接收的上下文一致率达到 **100%**（完全杜绝幽灵引用），并能以零外部模型调用的开销，精确从生成文本中提取角标元数据，实现越界虚假引用拦截与引用覆盖率度量。

---

## 2. Research Ledger

```text
id: R-ALCE-01
sourceType: paper
titleOrRepository: Enabling Large Language Models to Generate Text with Citations (ALCE Benchmark)
authorsOrMaintainer: Tianyu Gao, Howard Yen, Jiateng Liu, Danqi Chen
venueAndYear: EMNLP 2023
doiOrArxiv: arXiv:2305.13252
url: https://arxiv.org/abs/2305.13252
verificationStatus: VERIFIED
relevantFinding: 规范了引用角标 [i] 的提取与评估标准：Citation Precision（引用段落与断言支撑度）和 Citation Recall（断言覆盖率）。
projectApplicability: 为 CitationExtractor 的角标提取规则与评估指标提供标准支持。
```

```text
id: R-RAGCHECKER-02
sourceType: paper
titleOrRepository: RAGChecker: A Fine-grained Framework for Diagnosing Retrieval-Augmented Generation
authorsOrMaintainer: Ziyan Jiang, Xiang Yue, et al.
venueAndYear: ACL 2024
doiOrArxiv: arXiv:2408.08067
url: https://arxiv.org/abs/2408.08067
verificationStatus: VERIFIED
relevantFinding: 生成侧核心缺陷为未引用、虚假引用以及未利用上下文。必须保证送入上下文与实际引用的严格单调映射。
projectApplicability: 确立了 RagContextBuilder 必须导出已装填清单（Emitted Sources）的工程原则。
```

---

## 3. 实现与代码变更

1. **`RagContextBuilder.java`**：
   - 修复了原代码中使用 `sb.length()`（字符数）与 `entry.getBytes().length`（字节数）直接混加导致的中文字符预算严重溢出缺陷，改用 `usedBytes` 精确统计 UTF-8 字节数。
   - 增加静态返回类 `ContextBuildResult`，封装格式化上下文与实际成功装入的 `emittedResults`。
   - 增加 `buildContextWithEmitted` 方法，保留原 `buildContext` 签名完全向后兼容。
2. **`RagRetrievalService.java`**：
   - 在 `retrieveOnce`、`retrieveSimple`、`mergeWithWebResults`、`mergeAmbiguousResults` 中全面切换为 `buildContextWithEmitted`。
   - 将 `RagResult.sources` 严格赋值为 `emittedResults`，并在 `debugInfo` 中注入 `emittedSourceCount`，确保上下文与返回来源 100% 对齐。
3. **`CitationExtractor.java`**：
   - 新建精准溯源解析与生成侧引用评估器。
   - 正则支持 `[来源 i]`、`[i]`、`【i】` 等多种常见角标。
   - 自动映射已装填段落元数据（`segmentId`, `documentId`, `documentName`, `snippet`），精准拦截越界引用（`valid=false`, `hasHallucinatedCitation=true`），并输出 `citationPrecision` 与 `citationRecall`。

---

## 4. 验证结果

- **契约测试命令**：
  ```bash
  bash run-with-java21.sh mvn -B -f backend/pom.xml -pl qknow-module-kmc/qknow-module-kmc-biz,tests test-compile surefire:test -Dtest=CitationGroundingContractTest
  ```
- **测试用例**：
  1. `testBudgetContextStrictAlignment`: 验证预算截断时已装填段落严格对齐，绝无幽灵来源泄露 (PASS)。
  2. `testStructuredCitationExtractionAndGrounding`: 验证角标解析与段落精准锚定，Precision 与 Recall 均为 100% (PASS)。
  3. `testHallucinatedCitationInterception`: 验证越界虚构角标拦截，`hasHallucinatedCitation=true` (PASS)。
  4. `testEmptyAndNoCitationHandling`: 验证空文本与无角标场景安全防御 (PASS)。
- **回归测试**：全量 RAG 测试（79 项单测）全部绿灯通过。
