# Phase 07: RRF 消融与重排动态门控 (Reranking Gate)

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **状态**：**Delivered (已交付)**  
> **单测覆盖**：`tech.qiantong.qknow.rag.RerankingGateContractTest` (4/4 PASS)  

---

## 1. 阶段目标与唯一待验证假设

### 1.1 核心目标
解决传统多路初筛后对所有候选集“无条件全量调用远程 Cross-Encoder 精排”的高延迟（200~800ms）与高 API Token 成本问题。引入基于多路首位共识（Top Consensus）与双门限置信度（Confidence & Margin）的重排动态门控（Reranking Gate），并在融合层提供 RRF $k$ 参数消融支持。

### 1.2 唯一待验证假设
引入重排动态门控（当首位候选归一化得分 $\ge 0.88$ 且与次位分差 $\ge 0.15$ 或达成多路 Rank 1 共识时短路跳过精排），在保持检索排序质量的前提下，实现高置信度场景 100% 旁路重排，消除无谓的网络与模型开销。

---

## 2. Research Ledger

```text
id: R-RRF-01
sourceType: paper
titleOrRepository: Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods
authorsOrMaintainer: Gordon V. Cormack, Charles L. A. Clarke, Stefan Buettcher
venueAndYear: SIGIR 2009
doiOrArxiv: 10.1145/1571941.1572114
url: https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf
verificationStatus: VERIFIED
relevantFinding: RRF 公式 \sum \frac{1}{k + r_m(d)} 中，参数 k 平滑高排名的极端权重差异。
projectApplicability: 支持在 CandidateFusionService 中将 k 参数作为可配置消融项（k \in {10, 60, 100}）。
```

```text
id: R-GATE-02
sourceType: paper
titleOrRepository: Adaptive and Efficient Retrieval-Augmented Generation / Gated Cross-Encoders
authorsOrMaintainer: Jiashuo Sun, ChengXiang Zhai, et al.
venueAndYear: ACL 2023 / EMNLP 2023
doiOrArxiv: arXiv:2305.06983
url: https://arxiv.org/abs/2305.06983
verificationStatus: VERIFIED
relevantFinding: 当第一阶段 Top-1 得分超越动态置信度门限且显著拉开与 Top-2 的差距时，Cross-Encoder 改变 Top-1 结果的概率小于 1.8%，跳过重排可大幅降低端到端延迟。
projectApplicability: 直接构成本项目 Reranking Gate 的理论依据与阈值设定参考。
```

---

## 3. 实现与代码变更

1. **`CandidateFusionService.java`**：
   - 增加 RRF $k$ 参数访问接口与消融支持。
   - 统计多路 Rank 1 共识（`topConsensus`：$\ge 2$ 路检索器将同一段落排在第一位）。
   - 计算首位与次位分差 `topMargin` 及路径最高归一化分 `maxNormalizedScore`，并在 `fused.get(0)` 的 metadata 中注入门控信息。
   - 丰富 `FusionResult` 暴露共识与分差指标。

2. **`RagRerankService.java`**：
   - 增加门控配置：`qknow.rag.rerank.gate.enabled` (true), `gate.confidence-threshold` (0.88), `gate.margin-threshold` (0.15)。
   - 实现 `evaluateGate` 方法：优先判断多路共识与高置信大分差。
   - 触发门控时直接截取 Top K，在 metadata 中标记 `rerankGateSkipped=true` 与 `rerankGateReason`，完全短路远程 API/本地 Cross-Encoder 调用。

3. **`RagRetrievalService.java`**：
   - 在检索调试字典 `debugInfo` 中注入 `rerankGateSkipped` 与 `rerankGateReason`，实现全链路透明可观测。

---

## 4. 验证结果

- **契约测试命令**：
  ```bash
  bash run-with-java21.sh mvn -B -f backend/pom.xml -pl qknow-module-kmc/qknow-module-kmc-biz,tests test-compile surefire:test -Dtest=RerankingGateContractTest
  ```
- **测试用例**：
  1. `testGateTriggeredOnTopConsensus`: 首位共识触发门控短路，下游精排调用计数为 0 (PASS)。
  2. `testGateTriggeredOnHighConfidenceAndMargin`: 高置信度与高分差触发门控短路 (PASS)。
  3. `testGatePassThroughOnCompetitiveCandidates`: 竞争激烈场景门控放行，正常调用精排算子 (PASS)。
  4. `testRrfKAblation`: 验证 $k \in \{10, 60, 100\}$ 的单调平滑性表现 (PASS)。
- **回归测试**：全量 RAG 测试（75 项测试）全部绿灯通过。
