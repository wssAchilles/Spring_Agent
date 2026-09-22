#!/usr/bin/env python3
"""Run the deterministic evaluator for one or more frozen fixtures."""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from benchmark_builder import build_manifest
from evaluate_rag import evaluate


def run(fixtures: list[Path], output: Path, methods: list[str], k: int) -> Path:
    output.mkdir(parents=True, exist_ok=True)
    summary_rows = []
    for fixture in fixtures:
        name = fixture.name
        fixture_output = output / name
        evaluate(fixture, fixture_output, methods, k)
        manifest = build_manifest(fixture)
        (fixture_output / "benchmark_manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        with (fixture_output / "eval_summary.csv").open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                summary_rows.append({"fixture": name, **row})
    combined = output / "eval_summary_all.csv"
    fields = ["fixture", "method", "queries", "recall@5", "recall@10", "mrr@10", "ndcg@10", "hopRecall@10"]
    with combined.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(summary_rows)
    return combined


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, action="append", required=True, help="repeat for each fixture")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--k", type=int, default=10)
    parser.add_argument("--methods", nargs="+", default=["token_overlap", "exact_token_overlap"])
    args = parser.parse_args()
    print(run(args.fixture, args.output, args.methods, args.k))


if __name__ == "__main__":
    main()
