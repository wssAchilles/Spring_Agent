# Review Packet

## Manuscript scope

`paper.tex` reports a bounded implementation study of Pareto ranking after a fixed memory candidate set. It describes the four-objective long-term-memory adapter separately from the three-objective node-only timing path. It reports a targeted 14-test run whose log preamble prints OpenJDK 21.0.5 (the Surefire fork runtime is not separately recorded), a warmed N=1000, 1024-dimensional microbenchmark with a 2.537 ms median, and a corrected frozen LongMemEval-S raw-session reranking proxy. The proxy reports Pareto macro Recall@5 0.2529 versus weighted sum 0.2559, a descriptive -0.003 difference with 496 ties. An independent rerun on 2026-10-29 reproduced the complete summary and per-question CSVs exactly, and its 10 built-in self-tests passed. It makes no Qwen-vector, compressed-summary, answer-quality, statistical-significance, or end-to-end production-latency claim, and it treats missing user-level filtering as a production-safety blocker.

## Grounding artifacts

- `claims_grounding.json`: 13 claims covering targeted test results, the node-only timing observation, class-level coverage, implementation boundaries, complexity, NSGA-II interpretation, corrected LongMemEval proxy comparison, and independent rerun reproducibility.
- `paper_data.json`: kernel results, corrected LongMemEval proxy metrics, independent rerun evidence, inherited dynamic-retention details, hashed implementation/test sources with exact line ranges, JaCoCo coverage evidence, and limitations.
- `results/longmemeval_session_rerank/run_v2/summary.json`: accepted corrected offline run with full BM25 term factor, 500 questions, 948 qrels, paired outcomes, and strict-cutoff sensitivity.
- `results/longmemeval_session_rerank/reproduction_check_20261029/`: independent frozen-contract rerun. The full and sensitivity per-question CSVs are byte-identical to `run_v2`; the result summary's dataset, results, and interpretation fields match; the sidecars record hashes and the exact rerun command.
- `results/kernel/memory-tests-final-14-post-coverage-fix.log`: post-source-update non-JaCoCo run; 14 tests, 0 failures/errors, 2.537 ms median, completed 2026-10-29 06:07 +08:00.
- `results/kernel/pareto-jacoco-report-post-coverage-fix.csv`: post-source-update class-level JaCoCo counters for the ranker; 100% instruction/branch/line/method coverage, report generated 2026-10-29 06:15 +08:00.
- `results/kernel/memory-tests-final-14-rerun.log` and `pareto-jacoco-report-final-14.csv`: earlier 04:28/04:29 evidence (2.505 ms and 99.6% branch coverage) predates the ranker source update at 05:59 and is not the source snapshot used by the manuscript.
- `results/kernel/core-compile-post-coverage-fix.log`: Java 21 core clean-compile record.
- `references_provenance.json` and `results/longmemeval_session_rerank/research_ledger.jsonl`: five cited sources, including the BM25 implementation source; NSGA-II is conservatively aligned to `PARTIALLY_VERIFIED`, with a note that the two records disagree on the exact sections reviewed.
- `scientific-evidence/verification-report.json`: refreshed evidence-link report with 127 links (68 resolved, 59 anchored, 0 dangling) across 8 manifests. Anchored means the target is reachable; the base verifier does not parse locator semantics.

## Review focus

Assess whether implementation and test descriptions are supported by the current artifacts; whether the manuscript keeps the measured kernel result distinct from retrieval quality and production performance; whether the Supplementary Information is visible, substantive, and grounded; and whether the disclosed user-isolation limitation is clear enough not to imply production safety.

## Validation snapshot

- `validate_tex_refs.py`: passed; five citation-key references validated.
- `validate_claims.py`: passed after the final manuscript and evidence-source update; 13 grounded claims and 5 provenance references validated.
- `validate_figures.py`: validates the implementation overview and the 5:2 graphical abstract; the full orchestrator readability checks are unavailable in this workspace.
- `compile_latex.sh`: passed after the final manuscript edits; generated a 24-page `paper.pdf` and validated that Supplementary Information renders after References. Duplicate hyperref page anchors and several overfull/underfull boxes remain non-fatal; no undefined citations/references or compile errors were reported.
- The final figure caption explicitly states that downstream context assembly and agent task success are not evaluated.

## Known limitations to retain

- The LongMemEval comparison is raw-session and lexical-proxy evidence; no frozen production summary-to-session mapping or Qwen-vector result is available.
- The corrected matched weighted-sum comparison does not show Pareto superiority; interpret the -0.003 difference as descriptive only.
- The timing is a single-machine warmed JUnit microbenchmark, not production p50/p95 or end-to-end latency.
- The class-level JaCoCo report is not whole-project coverage: the ranker class reports 100% instruction/branch/line/method coverage; nested records and module-wide coverage are not claimed.
- `LongTermMemory.recall` does not apply `userId` as a vector-store filter; manuscript claims about this path should remain explicitly scoped and the issue should remain a production-safety blocker pending separate remediation and verification.
- Author identity, funding, competing-interest, contribution details, and final review of the AI-use disclosure remain for author confirmation before submission.
- The implementation grounding includes SHA-256 hashes and exact source line ranges; the snapshot is still an uncommitted working tree rather than an immutable commit.
- TeXcount reports 3,550 total words (3,302 body-text words), and the paper has five cited references. It still contains anonymous-author and author-confirmation declaration placeholders, so it is a review draft, not a submission-ready paper; the missing metadata must be supplied by the authors, and research depth remains limited.
- The evidence verifier checks path reachability but leaves locator semantics as `anchored`; this is a verifier limitation, so the latest values were manually cross-checked against the final logs and CSV.
