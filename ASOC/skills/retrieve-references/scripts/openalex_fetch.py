#!/usr/bin/env python3
"""Search OpenAlex and retrieve BibTeX entries for matching works."""

from __future__ import annotations

import argparse
import json
import os
import sys
import unicodedata
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


OPENALEX_WORKS_URL = "https://api.openalex.org/works"


def utc_now() -> str:
    """Return the current UTC timestamp string."""
    return datetime.now(timezone.utc).isoformat()


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Retrieve BibTeX entries for a query using OpenAlex works search."
    )
    parser.add_argument("--query", action="append", default=[], help="OpenAlex works search query.")
    parser.add_argument("--min-refs", type=int, default=8, help="Minimum required references.")
    parser.add_argument("--max-refs", type=int, default=15, help="Maximum references to keep.")
    parser.add_argument("--search-limit", type=int, default=10, help="Maximum OpenAlex results per query.")
    parser.add_argument(
        "--mailto",
        default=os.getenv("OPENALEX_EMAIL"),
        help="Contact email for OpenAlex polite pool.",
    )
    parser.add_argument(
        "--allow-partial-output",
        action="store_true",
        help="Write any retrieved references even if count < --min-refs, then exit nonzero.",
    )
    parser.add_argument("--out-bib", required=True, help="Output path for references.bib.")
    parser.add_argument("--out-json", required=True, help="Output path for provenance JSON.")
    return parser.parse_args()


def fetch_json(url: str) -> dict[str, Any]:
    """Fetch a JSON object from a URL."""
    req = urllib.request.Request(url, headers={"Accept": "application/json"}, method="GET")
    with urllib.request.urlopen(req, timeout=30) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not isinstance(payload, dict):
        raise SystemExit(f"Expected JSON object from {url}")
    return payload


def normalize_queries(values: list[str]) -> list[str]:
    """Normalize queries without injecting generic fallback terms."""
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


def search_openalex(query: str, *, limit: int, mailto: str | None) -> list[dict[str, Any]]:
    """Search OpenAlex works."""
    params = {"search": query, "per-page": str(limit)}
    if mailto:
        params["mailto"] = mailto
    payload = fetch_json(f"{OPENALEX_WORKS_URL}?{urllib.parse.urlencode(params)}")
    results = payload.get("results")
    if not isinstance(results, list):
        return []
    return [row for row in results if isinstance(row, dict)]


def _has_non_latin_letters(name: str) -> bool:
    """Detect letters outside the Latin script, e.g. Cyrillic homoglyphs in profile names."""
    return any(ch.isalpha() and "LATIN" not in unicodedata.name(ch, "") for ch in name)


def author_names_from_work(work: dict[str, Any]) -> list[str]:
    """Extract ordered author names from an OpenAlex work."""
    authorships = work.get("authorships")
    if not isinstance(authorships, list):
        return []

    names: list[str] = []
    seen: set[str] = set()
    for authorship in authorships:
        if not isinstance(authorship, dict):
            continue
        author = authorship.get("author")
        display_name = ""
        if isinstance(author, dict):
            raw_display_name = author.get("display_name")
            if isinstance(raw_display_name, str):
                display_name = " ".join(raw_display_name.split()).strip()
        raw_author_name = authorship.get("raw_author_name")
        fallback_name = (
            " ".join(raw_author_name.split()).strip()
            if isinstance(raw_author_name, str)
            else ""
        )
        if display_name and fallback_name and _has_non_latin_letters(display_name):
            display_name = ""
        name = display_name or fallback_name
        if not name:
            continue
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        names.append(name)
    return names


def doi_url_from_work(work: dict[str, Any]) -> str | None:
    """Return the DOI URL for an OpenAlex work when available."""
    doi = work.get("doi")
    if isinstance(doi, str) and doi.strip():
        return doi.strip()
    ids = work.get("ids")
    if isinstance(ids, dict):
        doi = ids.get("doi")
        if isinstance(doi, str) and doi.strip():
            return doi.strip()
    return None


def bibtex_for_doi(doi_url: str) -> str | None:
    """Resolve BibTeX for a DOI using content negotiation."""
    req = urllib.request.Request(
        doi_url,
        headers={"Accept": "application/x-bibtex"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read().decode("utf-8").strip()
    except Exception:
        return None
    return payload or None


def _split_bibtex_body_fields(body: str) -> tuple[str, list[str]] | None:
    """Split a BibTeX body into citation key and top-level fields."""
    if "," not in body:
        return None
    key, remainder = body.split(",", 1)
    citation_key = key.strip()
    if not citation_key:
        return None

    fields: list[str] = []
    current: list[str] = []
    brace_depth = 0
    in_quotes = False
    escape = False
    for char in remainder:
        if escape:
            current.append(char)
            escape = False
            continue
        if char == "\\":
            current.append(char)
            escape = True
            continue
        if char == '"' and brace_depth == 0:
            in_quotes = not in_quotes
            current.append(char)
            continue
        if not in_quotes:
            if char == "{":
                brace_depth += 1
            elif char == "}":
                brace_depth = max(0, brace_depth - 1)
            elif char == "," and brace_depth == 0:
                field = "".join(current).strip()
                if field:
                    fields.append(field)
                current = []
                continue
        current.append(char)
    tail = "".join(current).strip()
    if tail:
        fields.append(tail)
    return citation_key, fields


def upsert_bibtex_author_field(bibtex: str, author_names: list[str]) -> str:
    """Replace or insert the BibTeX author field."""
    clean_authors = [" ".join(name.split()).strip() for name in author_names if name.strip()]
    if not clean_authors:
        return bibtex

    entry_start = bibtex.find("{")
    entry_end = bibtex.rfind("}")
    if entry_start == -1 or entry_end <= entry_start:
        return bibtex

    body = bibtex[entry_start + 1 : entry_end].strip()
    split_body = _split_bibtex_body_fields(body)
    if split_body is None:
        return bibtex
    citation_key, fields = split_body

    author_field = f"author={{{' and '.join(clean_authors)}}}"
    updated_fields: list[str] = []
    replaced = False
    for field in fields:
        field_name, sep, _ = field.partition("=")
        if sep and field_name.strip().casefold() == "author":
            updated_fields.append(author_field)
            replaced = True
            continue
        updated_fields.append(field.strip())
    if not replaced:
        updated_fields.append(author_field)

    multiline = "\n" in bibtex
    prefix = bibtex[: entry_start + 1]
    if multiline:
        return f"{prefix}{citation_key},\n  " + ",\n  ".join(updated_fields) + "\n}"
    return f"{prefix}{citation_key}, " + ", ".join(updated_fields) + " }"


def inject_openalex_authors(bibtex: str, work: dict[str, Any]) -> str:
    """Rewrite the BibTeX author field from OpenAlex authorships."""
    return upsert_bibtex_author_field(bibtex, author_names_from_work(work))


def title_of(work: dict[str, Any]) -> str:
    """Return the display title of an OpenAlex work."""
    value = work.get("display_name")
    return value.strip() if isinstance(value, str) and value.strip() else "(missing title)"


def venue_of(work: dict[str, Any]) -> str | None:
    """Return the venue name when OpenAlex includes a primary source."""
    primary_location = work.get("primary_location")
    if not isinstance(primary_location, dict):
        return None
    source = primary_location.get("source")
    if not isinstance(source, dict):
        return None
    venue = source.get("display_name")
    return venue.strip() if isinstance(venue, str) and venue.strip() else None


def bibtex_key(bibtex: str) -> str | None:
    """Extract the BibTeX key from a BibTeX entry."""
    marker = "@"
    if marker not in bibtex:
        return None
    try:
        head = bibtex.split("{", 1)[1]
        key = head.split(",", 1)[0].strip()
    except Exception:
        return None
    return key or None


def build_reference_row(work: dict[str, Any], *, query: str, bibtex: str) -> dict[str, Any]:
    """Build a provenance/reference row for an OpenAlex match."""
    openalex_id = work.get("id")
    return {
        "id": f"ref.openalex.{str(openalex_id).rsplit('/', 1)[-1]}" if isinstance(openalex_id, str) else "",
        "paper_id": openalex_id,
        "citation_key": bibtex_key(bibtex),
        "title": title_of(work),
        "year": work.get("publication_year"),
        "venue": venue_of(work),
        "citation_count": work.get("cited_by_count", 0),
        "query": query,
        "source_url": doi_url_from_work(work) or (str(openalex_id) if isinstance(openalex_id, str) else ""),
        "retrieved_at": utc_now(),
        "bibtex": bibtex,
    }


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    if args.min_refs <= 0:
        raise SystemExit("--min-refs must be > 0")
    if args.max_refs < args.min_refs:
        raise SystemExit("--max-refs must be >= --min-refs")
    if args.search_limit <= 0:
        raise SystemExit("--search-limit must be > 0")

    queries = normalize_queries(list(args.query))
    if not queries:
        raise SystemExit("At least one non-empty --query is required.")

    references: list[dict[str, Any]] = []
    used_keys: set[str] = set()
    used_paper_ids: set[str] = set()

    for query in queries:
        if len(references) >= args.max_refs:
            break
        print(f"[openalex_fetch] query start: {query}", file=sys.stderr, flush=True)
        works = search_openalex(query, limit=args.search_limit, mailto=args.mailto)
        print(
            f"[openalex_fetch] raw results | query={query}\n"
            + json.dumps(
                {"query": query, "results": works},
                indent=2,
                sort_keys=True,
                ensure_ascii=False,
            ),
            file=sys.stderr,
            flush=True,
        )
        for work in works:
            if len(references) >= args.max_refs:
                break
            openalex_id = work.get("id")
            paper_id = str(openalex_id).strip() if isinstance(openalex_id, str) else ""
            if not paper_id or paper_id in used_paper_ids:
                continue
            doi_url = doi_url_from_work(work)
            if not doi_url:
                continue
            bibtex = bibtex_for_doi(doi_url)
            if bibtex is None:
                continue
            bibtex = inject_openalex_authors(bibtex, work)
            key = bibtex_key(bibtex)
            if key is None or key in used_keys:
                continue
            used_paper_ids.add(paper_id)
            used_keys.add(key)
            references.append(build_reference_row(work, query=query, bibtex=bibtex))
        print(
            f"[openalex_fetch] query complete | refs={len(references)} / {args.max_refs}",
            file=sys.stderr,
            flush=True,
        )

    out_bib = Path(args.out_bib)
    out_json = Path(args.out_json)
    out_bib.parent.mkdir(parents=True, exist_ok=True)
    out_json.parent.mkdir(parents=True, exist_ok=True)

    if references:
        out_bib.write_text("\n\n".join(row["bibtex"] for row in references) + "\n", encoding="utf-8")
        out_json.write_text(
            json.dumps(
                {
                    "source": "openalex",
                    "count": len(references),
                    "queries": queries,
                    "retrieved_at": utc_now(),
                    "references": [{k: v for k, v in row.items() if k != "bibtex"} for row in references],
                },
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        print(f"Wrote {len(references)} references to {out_bib} and {out_json}")

    if len(references) < args.min_refs:
        if args.allow_partial_output and references:
            raise SystemExit(
                f"Partial OpenAlex output written: {len(references)} < required {args.min_refs}"
            )
        raise SystemExit(
            f"Not enough grounded references from OpenAlex: {len(references)} < required {args.min_refs}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
