# ESWA experiment evidence

The submission-facing evidence is the v2 protocol. It contains 300 unique
questions, repository-source Track 2 anchors, 3,801 candidate segments, and a
four-column TREC qrels file. The earlier v1 diagnostic files remain archived
and are not used for v2 claims.

Authoritative status: `backend/tests/evidence/evidence_integrity_audit_v2.json` and `backend/tests/evidence/benchmark_report_v2.json`.

Run from the repository root using the project virtual environment:

```bash
.venv/bin/python scripts/eval_suite/benchmark_builder_v2.py
.venv/bin/python scripts/eval_suite/audit_evidence_v2.py
.venv/bin/python scripts/eval_suite/eval_runner_300.py --benchmark backend/tests/src/test/resources/eval/v2/benchmark_300.jsonl --corpus backend/tests/src/test/resources/eval/v2/corpus_300.jsonl --qrels backend/tests/src/test/resources/eval/v2/qrels_300.tsv --results backend/tests/evidence/eval_results_300_v2.jsonl --summary backend/tests/evidence/eval_summary_300_v2.csv
.venv/bin/python scripts/eval_suite/statistical_tests_300.py --results backend/tests/evidence/eval_results_300_v2.jsonl --output backend/tests/evidence/statistical_test_report_v2.json
.venv/bin/python scripts/eval_suite/make_scientific_figures.py --results backend/tests/evidence/eval_results_300_v2.jsonl --output-dir article/figures
.venv/bin/python scripts/eval_suite/sensitivity_green_ai.py
```

These commands regenerate the audit, statistics, editable tables and
publication-style vector figures. The offline runner is local and deterministic;
Dense and Graph are proxies, not Qwen embeddings or a hosted graph service.

The 100-question hosted-model experiment reads `HERMES_OPENAI_API_KEY` (or
`DEEPSEEK_API_KEY`) from the process environment or local `.env` and never
writes the key to evidence:

```bash
.venv/bin/python scripts/eval_suite/eval_e2e_generation.py --workers 4
```

It uses `deepseek-flash`, generation thinking disabled with temperature 0.2,
and a thinking-enabled high-effort judge without temperature or penalty
parameters. Missing credentials produce an explicit blocked status rather than
fabricated metrics.

For the major-revision analysis, regenerate subgroup answer-quality rows and
the representative MTTR/financial scenario with:

```bash
.venv/bin/python scripts/eval_suite/generate_e2e_subgroup_table.py
.venv/bin/python scripts/eval_suite/roi_scenario_simulation.py
```

The ROI runner reports the conservative and risk-inclusive cash-flow cases and
flags the supplied 312.6% ROI, NPV, and 4.6-month payback when they cannot be
obtained from one consistent row.

From `article/`, compile:

```bash
latexmk -pdf -interaction=nonstopmode -halt-on-error -file-line-error elsarticle-template-num.tex
```

Then from the repository root run `.venv/bin/python scripts/eval_suite/verify_article.py`. This checks build errors, references, citation keys, Highlights length, PDF text bounds and vector structure. Rendering does not replace human visual review or the scientific-validity audit.

The completed Java 21 nine-suite recheck and exact reproduction command are
documented in `article/EXPERIMENT_READINESS.md`.
