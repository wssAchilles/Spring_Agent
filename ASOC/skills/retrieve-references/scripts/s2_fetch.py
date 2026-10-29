#!/usr/bin/env python3
"""Fetch reference candidates from Semantic Scholar APIs."""

from __future__ import annotations

import argparse
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

BASE_URL = "https://api.semanticscholar.org/graph/v1"
KEY_RE = re.compile(r"@\w+\{([^,]+),")
def utc_now() -> str:
    """Return the current UTC timestamp string."""
    return datetime.now(timezone.utc).isoformat()


def http_json(url: str, *, timeout: int, api_key: str | None) -> dict[str, Any]:
    """Handle http json logic for this module."""
    max_attempts = 5
    for attempt in range(1, max_attempts + 1):
        req = urllib.request.Request(
            url,
            headers={
                "Accept": "application/json",
                **({"x-api-key": api_key} if api_key else {}),
            },
            method="GET",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code in {403, 429} and attempt < max_attempts:
                retry_after = exc.headers.get("Retry-After")
                try:
                    wait_seconds = float(retry_after) if retry_after is not None else 1.5 * attempt
                except ValueError:
                    wait_seconds = 1.5 * attempt
                time.sleep(max(0.5, min(wait_seconds, 20.0)))
                continue
            raise SystemExit(f"Semantic Scholar HTTP {exc.code}: {url}") from exc
        except urllib.error.URLError as exc:
            raise SystemExit(f"Semantic Scholar request failed: {exc}") from exc
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Semantic Scholar returned invalid JSON: {exc}") from exc

        if not isinstance(payload, dict):
            raise SystemExit("Semantic Scholar response was not a JSON object.")
        return payload

    raise SystemExit(f"Semantic Scholar request failed after retries: {url}")


def search(query: str, *, limit: int, timeout: int, api_key: str | None) -> list[dict[str, Any]]:
    """Handle search logic for this module."""
    params = urllib.parse.urlencode(
        {
            "query": query,
            "limit": str(limit),
            "fields": "paperId,title,year,venue,citationCount",
        }
    )
    payload = http_json(f"{BASE_URL}/paper/search?{params}", timeout=timeout, api_key=api_key)
    rows = payload.get("data")
    if not isinstance(rows, list):
        return []
    return [row for row in rows if isinstance(row, dict)]


def details(paper_id: str, *, timeout: int, api_key: str | None) -> dict[str, Any]:
    """Handle details logic for this module."""
    safe_paper_id = urllib.parse.quote(paper_id, safe="")
    params = urllib.parse.urlencode({"fields": "paperId,title,year,venue,citationCount,citationStyles,url"})
    return http_json(f"{BASE_URL}/paper/{safe_paper_id}?{params}", timeout=timeout, api_key=api_key)


def bibtex_key(bibtex: str) -> str | None:
    """Handle bibtex key logic for this module."""
    match = KEY_RE.search(bibtex)
    if not match:
        return None
    key = match.group(1).strip()
    return key or None


def normalize_queries(values: list[str]) -> list[str]:
    """Normalize queries."""
    seen: set[str] = set()
    out: list[str] = []
    for query in values:
        clean = " ".join(query.split()).strip()
        if not clean:
            continue
        if len(clean) > 200:
            clean = clean[:200].rstrip()
        token = clean.lower()
        if token in seen:
            continue
        seen.add(token)
        out.append(clean)
    return out


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Fetch grounded BibTeX references from Semantic Scholar.")
    parser.add_argument("--query", action="append", default=[], help="Search query (repeatable).")
    parser.add_argument("--min-refs", type=int, default=8, help="Minimum required references.")
    parser.add_argument("--max-refs", type=int, default=15, help="Maximum references to keep.")
    parser.add_argument("--search-limit", type=int, default=25, help="Search hits per query.")
    parser.add_argument("--timeout-seconds", type=int, default=20, help="Request timeout.")
    parser.add_argument("--out-bib", required=True, help="Output path for references.bib.")
    parser.add_argument("--out-json", required=True, help="Output path for provenance JSON.")
    return parser.parse_args()


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    if args.min_refs <= 0:
        raise SystemExit("--min-refs must be > 0")
    if args.max_refs < args.min_refs:
        raise SystemExit("--max-refs must be >= --min-refs")
    if args.search_limit <= 0:
        raise SystemExit("--search-limit must be > 0")
    if args.timeout_seconds <= 0:
        raise SystemExit("--timeout-seconds must be > 0")

    api_key = os.environ.get("S2_API_KEY")
    queries = normalize_queries(list(args.query))
    if not queries:
        raise SystemExit("At least one non-empty --query is required.")

    references: list[dict[str, Any]] = []
    used_keys: set[str] = set()
    seen_paper_ids: set[str] = set()
    for query in queries:
        if len(references) >= args.min_refs:
            break
        query_candidates: list[dict[str, Any]] = []
        for row in search(query, limit=args.search_limit, timeout=args.timeout_seconds, api_key=api_key):
            paper_id = row.get("paperId")
            if not isinstance(paper_id, str) or not paper_id.strip():
                continue
            paper_id = paper_id.strip()
            if paper_id in seen_paper_ids:
                continue
            query_candidates.append(
                {
                    "paper_id": paper_id,
                    "query": query,
                    "citation_count": int(row.get("citationCount") or 0),
                }
            )

        ranked = sorted(query_candidates, key=lambda item: (-int(item["citation_count"]), item["paper_id"]))
        for row in ranked:
            if len(references) >= args.min_refs:
                break
            paper_id = str(row["paper_id"])
            seen_paper_ids.add(paper_id)
            payload = details(paper_id, timeout=args.timeout_seconds, api_key=api_key)
            citation_styles = payload.get("citationStyles")
            bibtex = citation_styles.get("bibtex") if isinstance(citation_styles, dict) else None
            if not isinstance(bibtex, str) or not bibtex.strip():
                continue
            key = bibtex_key(bibtex)
            if key is None or key in used_keys:
                continue
            used_keys.add(key)
            references.append(
                {
                    "id": f"ref.s2.{paper_id}",
                    "paper_id": paper_id,
                    "citation_key": key,
                    "title": payload.get("title"),
                    "year": payload.get("year"),
                    "venue": payload.get("venue"),
                    "citation_count": payload.get("citationCount", row["citation_count"]),
                    "query": row["query"],
                    "source_url": f"{BASE_URL}/paper/{urllib.parse.quote(paper_id, safe='')}",
                    "retrieved_at": utc_now(),
                    "bibtex": bibtex.strip(),
                }
            )

    if len(references) < args.min_refs:
        raise SystemExit(
            f"Not enough grounded references: {len(references)} < required {args.min_refs}"
        )

    out_bib = Path(args.out_bib)
    out_json = Path(args.out_json)
    out_bib.parent.mkdir(parents=True, exist_ok=True)
    out_json.parent.mkdir(parents=True, exist_ok=True)

    out_bib.write_text("\n\n".join(row["bibtex"] for row in references) + "\n", encoding="utf-8")
    out_json.write_text(
        json.dumps(
            {
                "generated_at": utc_now(),
                "count": len(references),
                "queries": queries,
                "references": [{k: v for k, v in row.items() if k != "bibtex"} for row in references],
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )
    print(f"Wrote {len(references)} references to {out_bib} and {out_json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
