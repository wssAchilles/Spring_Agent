# Invalidated preliminary run

This output directory records a preliminary execution and is retained for auditability only. Do not cite or use its metrics in the manuscript.

The executed BM25 scoring term omitted Lucene's `(k1 + 1)` multiplier before the bounded relevance mapping. Because this changes the bounded feature scale and can change both weighted ordering and Pareto crowding-distance truncation, the run does not implement the cited/frozen scoring method. The source data was unchanged. The accepted corrected execution is recorded separately in `../run_v2/` under contract version 3.

Recorded preliminary run: `2026-10-29T12:07:44+08:00` to `2026-10-29T12:07:56+08:00`; data SHA-256 `d6f21ea9d60a0d56f34a05b609c79c88a451d2ae03597821ea3d5a9678c3a442`; contract SHA-256 `c24c2379734493e7f1c1502c6b092c2a7f227add2fee5e4d2e8041ed32f88b2a`; script SHA-256 `998ba4bf1e12c8ba71b47c598114ae58b62e0e18e988c82ea1a1b7447708deed`.
