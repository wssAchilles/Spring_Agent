# LongMemEval session-level reranking: research-to-implementation gate

## A. 当前代码与失败机制

This stage tests one bounded question: under the same candidate sessions and the same measured features, does non-dominated Top-K selection retrieve more annotated evidence sessions than the project's weighted scalar score?

The selected production analogue is the `MemoryNode` route, not the distinct four-objective document adapter. `MemoryScoringServiceImpl.computeRecency` derives retention from age, base decay rate, and access count; `ParetoMemoryRanker.rankTopK` orders recency, cosine relevance, and importance; `computeCompositeScore` uses the fixed scalar weights 0.25 recency, 0.35 importance, and 0.40 relevance. The current source implements the Pareto route. This benchmark does not change production code.

The falsifiable hypothesis is: **on the complete LongMemEval-S candidate universe, Pareto Top-5 has higher macro session-level Recall@5 than the fixed weighted-sum baseline when both methods receive identical per-session recency, lexical-relevance-proxy, and importance features.** A result at or below zero improvement falsifies the directional hypothesis for this proxy setting.

The known failure mechanism under test is scalarization: fixed exchange rates can rank a candidate with strong relevance but low retention below a less relevant, fresher candidate. The alternative is the implemented non-dominated ordering with crowding-distance selection. This does not assume that recall will improve.

## B. Research Ledger

Experiment-specific, 14-field entries are in `research_ledger.jsonl`. The three read sources are the LongMemEval paper (benchmark/query-label and retrieval-metric design), Pérez-Iglesias et al. (BM25 scoring implementation and fixed parameters), and Deb et al. (non-dominated sorting and crowding-distance selection). The source readings support the protocol and mechanisms only; none proves a benefit on this project.

## C. 可迁移与不可迁移结论

- **可直接采用：** LongMemEval identifies evidence at session granularity and defines retrieval metrics over annotated evidence locations. Therefore, using each original session as a candidate preserves the direct `answer_session_ids` mapping.
- **需要改造：** the production cosine signal is unavailable for these sessions. BM25 is a transparent, deterministic lexical proxy, not Qwen embeddings or production vector recall. Recency is computed at each question timestamp with access count fixed to zero; the dataset contains no memory-use history. Importance is fixed at the Java `MemoryNode` primitive default (0.0), because the benchmark has no importance labels. This makes the effective Pareto trade-off two-dimensional (retention and lexical relevance).
- **必须拒绝：** equating session candidates with production compressed summaries; inferring forgetting labels from dates; treating question-level results as independent; tuning weights, BM25 parameters, qrels, or time handling after seeing results; or reporting a production/semantic retrieval result.
- **Cost and operations:** one standard-library Python process; no model/API calls, external services, new package, GPU, or source-data mutation. A deterministic implementation is easy to rerun; its main risk is limited external validity, not operational complexity.

## D. 候选方案比较

| Option | Correctness / falsifiability | Data need | Latency / cost | Complexity / dependency | Rollback and production impact |
|---|---|---|---|---|---|
| Existing node weighted-sum baseline | Matched scalar control; falsifiable against same qrels | Existing session text/date/qrels | Offline CPU; no API | Small; standard library | No production change |
| Minimal diagnostic: identity/order and label mapping validation | Detects mapping errors, but cannot test ranking quality | Same LongMemEval JSON | Negligible | Small; standard library | No production change |
| Candidate: Pareto rank with the same proxy feature vectors | Directly tests the stated hypothesis | Same sessions and features as baseline | Offline CPU; no API | Small faithful reimplementation of the ranker contract | No production change |
| Keep current manuscript unchanged | Avoids proxy overclaim but leaves the user's requested quality experiment unanswered | None | None | None | No production impact |

We reject a Qwen-vector comparison because no session embeddings or authorized API budget are present. We reject an independent significance claim because shared sessions connect the 500 questions into one component. The experiment is descriptive and exploratory.

## E. 推荐的最小算法

Run one Pareto-versus-weighted-sum comparison on identical per-question raw sessions. Use the existing node objective order (retention, relevance, importance), with the repository's fixed retention rule, importance default, and scalar weights. A standard-library BM25 score substitutes only for the unavailable cosine feature. The complete algorithm and deterministic tie rules are frozen in `experiment_contract.json` and `run_experiment.py`.

## F. 实验与实现计划

- **Input:** the repository-local `longmemeval_s_cleaned.json`, SHA-256 recorded in the contract; each original session is one candidate; `answer_session_ids` is the session-level qrel.
- **Identity validation:** 13 questions contain an intra-question repeated non-qrel session ID paired with distinct dates. These entries are preserved as separate position-aligned candidates (no data or qrel edits); each qrel ID is required to map to exactly one candidate position, and hit metrics count unique qrel IDs. Session-overlap components count an ID once per question.
- **Primary candidate universe:** the complete `haystack_sessions` for each question. No questions or qrels are removed. Report Recall@5 and Recall@10, nDCG@5, hit@5, precision@5, and descriptive category breakdowns.
- **Candidate:** rank the shared features by Pareto fronts and crowding distance, matching the `MemoryNode` ranker's objective semantics; relevance ties use the same BM25 proxy.
- **Baseline:** sort the same features by `0.25 * recency + 0.35 * importance + 0.40 * relevance`, matching the existing node scalar weights. Stable input order breaks exact score ties.
- **Time sensitivity:** separately repeat on candidates whose session date is no later than `question_date`; retain all original qrels and count unavailable gold as missed. This is a sensitivity analysis, not a redefinition of the benchmark labels.
- **No leakage:** qrels/answers are used only after ranking for scoring; no feature, parameter, tokenization, threshold, or tie rule is selected from outcomes. No train/selection split is claimed. The complete dataset is one connected session-overlap component.
- **Inference:** report descriptive paired differences only. Do not report p-values or confidence intervals from question resampling. The dependence structure and only two rankers do not justify Friedman/Nemenyi; the component structure does not justify ordinary per-question Wilcoxon inference.
- **Budget:** local CPU, Python standard library, no API/network/model/vector database/GPU. One run plus deterministic self-tests. No fixture, qrel, threshold, or production-source changes.
- **Stop condition:** stop after the frozen run and evidence validation. Any desired parameter or objective change is a new experiment requiring a new contract.
- **Reproduction:** `python3 results/longmemeval_session_rerank/run_experiment.py --data data/external/longmemeval/longmemeval_s_cleaned.json --contract results/longmemeval_session_rerank/experiment_contract.json --output results/longmemeval_session_rerank/run_v2`.

### Execution record

The preliminary output under `run/` is retained but explicitly invalidated: it omitted Lucene's `(k1 + 1)` multiplier before the bounded relevance mapping, so the feature scale and ranking could differ. It is not cited in the manuscript. The accepted contract is version 3, which fixes the full BM25 term factor, and the corrected run is preserved under `run_v2/`.

The corrected result does **not** support the directional hypothesis: Pareto macro Recall@5 is 0.25287, versus 0.25587 for the matched weighted-sum baseline (paired descriptive difference -0.003; 1 Pareto win, 496 ties, 3 baseline wins). Under the strict date cutoff, macro Recall@5 is 0.2494 versus 0.2524. These observations are descriptive only; the 500 questions form one shared-session component and no significance test is reported. Results and run provenance are in `run_v2/summary.json`.

## G. 风险、停止条件与后续授权边界

The experiment can establish only whether these two ranking rules differ on raw-session IDs under a lexical proxy and default/missing metadata. It cannot establish Qwen/cosine retrieval gains, compressed-summary retrieval gains, answer accuracy, forgetting benefit, production latency, or deployment safety. Future-dated evidence sessions appear in the local data; because the local package does not establish whether `question_date` is a universal visibility cutoff, the primary run keeps the supplied candidate universe and the strict cutoff remains a labeled sensitivity analysis. Shared sessions prohibit treating question-level observations as independent replicates. Production edits, feature changes, tuning, API usage, A/B, promotion, and online activation remain outside this experiment and require separate authorization.
