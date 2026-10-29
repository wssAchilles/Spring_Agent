# Scientific evidence output contract

Optional evaluator-owned `evaluation_evidence.json` preserves validated observations before
scalar aggregation. Its top-level object has exactly `schema_version: 1` and `episodes`.
`episodes` is a nonempty array of unique signed 64-bit seed records. Each record requires
`seed` (integer, not boolean), `score` (finite evaluator-computed number), `correct`
(evaluator-checked boolean), and `duration_seconds` (finite nonnegative evaluator-measured number).
Optional record fields are `solution` (validated inline JSON), `candidate_diagnostics` (named
finite numbers), and `candidate_timeseries` (records with exactly `series_name`, nonnegative
integer `step`, and finite `value`). Candidate diagnostics/timeseries remain unverified and
cannot certify correctness, feasibility, or compute compliance. Never relabel trusted evaluator
measurements as candidate diagnostics just to fit additional metadata into this schema.

The file is bounded to 16 MiB, nesting depth 12, 10,000 unique seeds, and 100,000 total timeseries
rows. Diagnostic/series names are at most 80 ASCII letters/digits/underscore/hyphen/period,
beginning with letter/digit/underscore/hyphen. No duplicate keys, nonfinite numbers, symlinks,
or extra schema fields. Do not use `plane`, `aggregate_seed_summaries`, transport metadata,
controller state, or publication decisions as extra top-level keys.

Use this envelope for already validated per-seed observations:

```python
def make_evaluation_evidence(seed_records):
    return {"schema_version": 1, "episodes": seed_records}
```

If a seed occurs in several declared cases/replays, compute one record per seed using the
predeclared aggregation, measured durations, and checked correctness; retain the declared
failure contributions. Never fabricate missing observations or omit failures to improve scores.
If evidence is unavailable, omit the optional file rather than writing an empty/invalid envelope.
Keep search and final evidence separate. Candidate responses remain finite inline JSON over the
existing broker: no shared writable mounts or candidate-to-host artifact paths.

Reviewers and repair agents must check this exact schema, not merely a version number or bounded
JSON. Extra fields are an output-contract defect; evidence must never override scalar metrics,
scientific gates, or harness publication authority. For circle packing retain validated geometry;
other tasks retain their corresponding checked outputs under `solution` when applicable.
