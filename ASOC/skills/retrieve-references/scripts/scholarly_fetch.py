#!/usr/bin/env python3
"""Fetch and rank references using the scholarly package."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping

try:
    from scholarly import ProxyGenerator as SCHOLARLY_PROXY_GENERATOR
    from scholarly import scholarly as SCHOLARLY_CLIENT
except ImportError:
    SCHOLARLY_CLIENT = None
    SCHOLARLY_PROXY_GENERATOR = None

ProxyGenerator = SCHOLARLY_PROXY_GENERATOR

KEY_RE = re.compile(r"@\w+\{([^,]+),")
YEAR_RE = re.compile(r"\b(19|20)\d{2}\b")
PROXY_MODE_CHOICES = ("auto", "none", "free", "single")
@dataclass
class Candidate:
    paper_id: str
    query: str
    title: str | None
    year: int | None
    venue: str | None
    citation_count: int
    source_url: str
    rank_score: float
    raw_publication: Any


@dataclass(frozen=True)
class ProxySettings:
    mode: str
    http: str | None
    https: str | None


class ScholarlyTimeoutError(RuntimeError):
    """Raised when a scholarly operation exceeds its budget."""


def utc_now() -> str:
    """Return the current UTC timestamp string."""
    return datetime.now(timezone.utc).isoformat()


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


def _default_proxy_mode() -> str:
    """Resolve the default proxy mode from the environment."""
    value = os.getenv("SCHOLARLY_PROXY_MODE")
    clean = " ".join(value.split()).strip().lower() if isinstance(value, str) else ""
    return clean if clean in PROXY_MODE_CHOICES else "auto"


def _default_proxy_http() -> str | None:
    """Resolve the default HTTP proxy from the environment."""
    value = os.getenv("SCHOLARLY_PROXY_HTTP")
    return _as_text(value)


def _default_proxy_https() -> str | None:
    """Resolve the default HTTPS proxy from the environment."""
    value = os.getenv("SCHOLARLY_PROXY_HTTPS")
    return _as_text(value)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Fetch grounded BibTeX references from Google Scholar via scholarly."
    )
    parser.add_argument("--query", action="append", default=[], help="Search query (repeatable).")
    parser.add_argument("--min-refs", type=int, default=8, help="Minimum required references.")
    parser.add_argument("--max-refs", type=int, default=15, help="Maximum references to keep.")
    parser.add_argument("--search-limit", type=int, default=25, help="Search hits per query.")
    parser.add_argument(
        "--sort-by",
        choices=["relevance", "date"],
        default="relevance",
        help="Google Scholar ordering mode.",
    )
    parser.add_argument(
        "--year-low",
        type=int,
        default=None,
        help="Optional lower publication year bound.",
    )
    parser.add_argument(
        "--year-high",
        type=int,
        default=None,
        help="Optional upper publication year bound.",
    )
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=0.5,
        help="Sleep between Scholar fetches to reduce blocking risk.",
    )
    parser.add_argument(
        "--proxy-mode",
        choices=PROXY_MODE_CHOICES,
        default=_default_proxy_mode(),
        help="Proxy mode for scholarly: auto, none, free, or single.",
    )
    parser.add_argument(
        "--proxy-setup-timeout-seconds",
        type=float,
        default=20.0,
        help="Timeout for proxy setup operations.",
    )
    parser.add_argument(
        "--proxy-http",
        default=_default_proxy_http(),
        help="HTTP proxy URL for --proxy-mode single. Defaults to SCHOLARLY_PROXY_HTTP.",
    )
    parser.add_argument(
        "--proxy-https",
        default=_default_proxy_https(),
        help="HTTPS proxy URL for --proxy-mode single. Defaults to SCHOLARLY_PROXY_HTTPS.",
    )
    parser.add_argument(
        "--query-timeout-seconds",
        type=float,
        default=45.0,
        help="Maximum wall-clock time per query.",
    )
    parser.add_argument(
        "--candidate-timeout-seconds",
        type=float,
        default=20.0,
        help="Maximum wall-clock time to resolve a candidate BibTeX entry.",
    )
    parser.add_argument(
        "--total-timeout-seconds",
        type=float,
        default=300.0,
        help="Maximum wall-clock time for the entire scholarly run.",
    )
    parser.add_argument(
        "--max-consecutive-failures",
        type=int,
        default=4,
        help="Abort after this many consecutive query/candidate failures.",
    )
    parser.add_argument(
        "--progress-interval-seconds",
        type=float,
        default=15.0,
        help="Emit stderr progress at most once per interval.",
    )
    parser.add_argument(
        "--allow-partial-output",
        action="store_true",
        help="Write any retrieved references even if count < --min-refs, then exit nonzero.",
    )
    parser.add_argument("--out-bib", required=True, help="Output path for references.bib.")
    parser.add_argument("--out-json", required=True, help="Output path for provenance JSON.")
    return parser.parse_args(argv)


def _value(obj: Any, key: str) -> Any:
    """Handle value logic for this module."""
    if isinstance(obj, dict):
        return obj.get(key)
    getter = getattr(obj, "get", None)
    if callable(getter):
        try:
            return getter(key)
        except TypeError:
            return None
    return getattr(obj, key, None)


def _as_int(value: Any) -> int | None:
    """Convert value to int form when possible."""
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        text = value.strip()
        if text.isdigit():
            return int(text)
        match = YEAR_RE.search(text)
        if match:
            try:
                return int(match.group(0))
            except ValueError:
                return None
    return None


def _as_text(value: Any) -> str | None:
    """Convert value to text form when possible."""
    if isinstance(value, str):
        clean = " ".join(value.split()).strip()
        return clean or None
    return None


def resolve_proxy_settings(
    args: argparse.Namespace,
    *,
    env: Mapping[str, str] | None = None,
) -> ProxySettings:
    """Normalize proxy settings from CLI flags and environment-backed defaults."""
    source_env = os.environ if env is None else env
    mode = (_as_text(getattr(args, "proxy_mode", None)) or "auto").lower()
    if mode not in PROXY_MODE_CHOICES:
        raise SystemExit(f"Unsupported --proxy-mode: {mode}")

    http = _as_text(getattr(args, "proxy_http", None))
    https = _as_text(getattr(args, "proxy_https", None))
    if http is None:
        http = _as_text(source_env.get("SCHOLARLY_PROXY_HTTP"))
    if https is None:
        https = _as_text(source_env.get("SCHOLARLY_PROXY_HTTPS"))
    if http is None and https is not None:
        http = https
    if https is None and http is not None:
        https = http

    if mode == "auto":
        mode = "single" if http or https else "none"

    return ProxySettings(mode=mode, http=http, https=https)


def _sanitize_proxy_error(message: str, settings: ProxySettings) -> str:
    """Redact proxy URLs and credentials from proxy setup errors."""
    redacted = message
    for value in {settings.http, settings.https}:
        if value:
            redacted = redacted.replace(value, "<redacted-proxy>")
    return re.sub(r"://([^/@\s]+):([^/@\s]+)@", "://<redacted>@", redacted)


def _log(message: str) -> None:
    """Emit progress for long-running scholarly fetches."""
    print(f"[scholarly_fetch] {message}", file=sys.stderr, flush=True)


def _render_progress_bar(completed: int, total: int, *, width: int = 12) -> str:
    """Render a compact ASCII progress bar."""
    safe_total = max(total, 1)
    clamped = max(0, min(completed, safe_total))
    if clamped <= 0:
        filled = 0
    else:
        filled = max(1, round(width * clamped / safe_total))
    filled = min(width, filled)
    return f"[{'#' * filled}{'-' * (width - filled)}]"


def _format_reference_progress(
    *,
    accepted_refs: int,
    target_refs: int,
    processed_candidates: int | None = None,
    total_candidates: int | None = None,
    query_index: int | None = None,
    total_queries: int | None = None,
    stage: str | None = None,
) -> str:
    """Format a console-friendly progress line for scholarly retrieval."""
    remaining_refs = max(target_refs - accepted_refs, 0)
    visible_target = max(target_refs, 1)
    parts = [
        f"{_render_progress_bar(min(accepted_refs, visible_target), visible_target)}",
        f"refs {accepted_refs}/{target_refs} ({remaining_refs} left)",
    ]
    if processed_candidates is not None and total_candidates is not None:
        parts.append(f"candidates {processed_candidates}/{total_candidates}")
    if query_index is not None and total_queries is not None:
        parts.append(f"query {query_index}/{total_queries}")
    body = " | ".join(parts)
    if stage:
        return f"{stage} | {body}"
    return body


def _log_reference_progress(**kwargs: Any) -> None:
    """Emit structured reference progress to stderr."""
    _log(_format_reference_progress(**kwargs))


def _remaining_seconds(deadline: float | None) -> float | None:
    """Return the remaining time budget when a deadline is active."""
    if deadline is None:
        return None
    return max(0.0, deadline - time.monotonic())


def _bounded_timeout(limit_seconds: float | None) -> float | None:
    """Normalize timeouts for a single operation."""
    if limit_seconds is not None and limit_seconds > 0:
        return limit_seconds
    return None


def _tightest_timeout(*limits: float | None) -> float | None:
    """Return the smallest active timeout from a set of optional limits."""
    active_limits = [limit for limit in limits if limit is not None]
    return min(active_limits) if active_limits else None


def _deadline_after(timeout_seconds: float | None) -> float | None:
    """Create a monotonic deadline from a timeout budget."""
    if timeout_seconds is None or timeout_seconds <= 0:
        return None
    return time.monotonic() + timeout_seconds


def _ensure_time_remaining(deadline: float | None, label: str) -> None:
    """Raise when a total or query budget has expired."""
    remaining = _remaining_seconds(deadline)
    if remaining is not None and remaining <= 0:
        raise ScholarlyTimeoutError(f"{label} timed out")


def _run_with_timeout(timeout_seconds: float | None, label: str, func: Any, /, *args: Any, **kwargs: Any) -> Any:
    """Run a blocking scholarly operation with a hard timeout on POSIX."""
    timeout_seconds = _bounded_timeout(timeout_seconds)
    if timeout_seconds is None:
        return func(*args, **kwargs)
    if os.name == "nt":
        return func(*args, **kwargs)

    previous_handler = signal.getsignal(signal.SIGALRM)
    previous_timer = signal.setitimer(signal.ITIMER_REAL, 0)

    def _handle_alarm(signum: int, frame: Any) -> None:
        raise ScholarlyTimeoutError(f"{label} timed out after {timeout_seconds:.1f}s")

    signal.signal(signal.SIGALRM, _handle_alarm)
    signal.setitimer(signal.ITIMER_REAL, timeout_seconds)
    try:
        return func(*args, **kwargs)
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous_handler)
        if previous_timer != (0.0, 0.0):
            signal.setitimer(signal.ITIMER_REAL, *previous_timer)


def configure_scholarly_proxy(
    settings: ProxySettings,
    *,
    setup_timeout_seconds: float | None = None,
) -> bool:
    """Configure scholarly proxy usage before any network calls."""
    if settings.mode == "none":
        return False
    if settings.mode == "single" and settings.http is None and settings.https is None:
        raise SystemExit(
            "--proxy-mode single requires --proxy-http/--proxy-https or matching env vars"
        )
    if SCHOLARLY_CLIENT is None:
        raise SystemExit(
            "Missing dependency `scholarly`. Install with: python -m pip install scholarly"
        )
    if ProxyGenerator is None:
        raise SystemExit("Installed `scholarly` does not expose ProxyGenerator support")

    try:
        proxy_generator = ProxyGenerator()
        if settings.mode == "free":
            success = _run_with_timeout(
                setup_timeout_seconds,
                "free proxy setup",
                proxy_generator.FreeProxies,
            )
        elif settings.mode == "single":
            success = _run_with_timeout(
                setup_timeout_seconds,
                "single proxy setup",
                proxy_generator.SingleProxy,
                http=settings.http,
                https=settings.https,
            )
        else:
            raise SystemExit(f"Unsupported --proxy-mode: {settings.mode}")
        if not success:
            raise RuntimeError(f"Failed to enable scholarly proxy mode: {settings.mode}")
        SCHOLARLY_CLIENT.use_proxy(proxy_generator)
    except SystemExit:
        raise
    except Exception as exc:
        detail = _sanitize_proxy_error(str(exc), settings).strip()
        suffix = f": {detail}" if detail else ""
        raise SystemExit(f"Failed to configure scholarly proxy{suffix}") from exc
    return True


def configure_proxy(
    args: argparse.Namespace,
    *,
    env: Mapping[str, str] | None = None,
) -> str | None:
    """Resolve and configure a scholarly proxy from CLI-style arguments."""
    requested_mode = (_as_text(getattr(args, "proxy_mode", None)) or "auto").lower()
    settings = resolve_proxy_settings(args, env=env)
    try:
        configured = configure_scholarly_proxy(
            settings,
            setup_timeout_seconds=getattr(args, "proxy_setup_timeout_seconds", None),
        )
    except SystemExit:
        if requested_mode != "auto":
            raise
        _log("auto proxy setup failed; continuing without proxy")
        return None
    return settings.mode if configured else None


def _extract_bib(publication: Any) -> dict[str, Any]:
    """Extract bib."""
    bib = _value(publication, "bib")
    if isinstance(bib, dict):
        return bib
    return {}


def _source_url(publication: Any) -> str:
    """Handle source url logic for this module."""
    for key in ("pub_url", "eprint_url", "url_scholarbib", "author_pub_id"):
        candidate = _as_text(_value(publication, key))
        if candidate:
            return candidate
    return ""


def _stable_paper_id(publication: Any, title: str | None, year: int | None) -> str:
    """Handle stable paper id logic for this module."""
    explicit_id = _as_text(_value(publication, "author_pub_id"))
    if explicit_id:
        return explicit_id

    url = _source_url(publication)
    if url:
        return url

    seed = f"{title or ''}|{year or ''}"
    digest = hashlib.sha1(seed.encode("utf-8")).hexdigest()
    return f"scholar:{digest}"


def _rank_score(citation_count: int, year: int | None) -> float:
    """Handle rank score logic for this module."""
    current_year = datetime.now(timezone.utc).year
    recency = max(1, current_year + 1 - year) if year is not None else 25
    return citation_count / (recency**1.5)


def _build_candidate(publication: Any, query: str) -> Candidate | None:
    """Build candidate."""
    bib = _extract_bib(publication)
    title = _as_text(bib.get("title"))
    year = _as_int(bib.get("pub_year"))
    venue = _as_text(bib.get("venue"))
    citation_count = _as_int(_value(publication, "num_citations")) or 0
    source_url = _source_url(publication)
    paper_id = _stable_paper_id(publication, title, year)
    if not paper_id.strip():
        return None

    return Candidate(
        paper_id=paper_id,
        query=query,
        title=title,
        year=year,
        venue=venue,
        citation_count=citation_count,
        source_url=source_url,
        rank_score=_rank_score(citation_count, year),
        raw_publication=publication,
    )


def _search_candidates(
    *,
    query: str,
    limit: int,
    sort_by: str,
    year_low: int | None,
    year_high: int | None,
    delay_seconds: float,
    query_timeout_seconds: float,
    total_deadline: float | None,
) -> list[Candidate]:
    """Handle search candidates logic for this module."""
    if SCHOLARLY_CLIENT is None:
        return []
    out: list[Candidate] = []
    query_deadline = _deadline_after(query_timeout_seconds)
    setup_timeout = _tightest_timeout(
        _bounded_timeout(query_timeout_seconds),
        _remaining_seconds(total_deadline),
    )
    try:
        iterator = _run_with_timeout(
            setup_timeout,
            f"query setup for '{query}'",
            SCHOLARLY_CLIENT.search_pubs,
            query,
            sort_by=sort_by,
            year_low=year_low,
            year_high=year_high,
        )
    except TypeError:
        iterator = _run_with_timeout(
            setup_timeout,
            f"query setup for '{query}'",
            SCHOLARLY_CLIENT.search_pubs,
            query,
            sort_by=sort_by,
        )

    for idx in range(limit):
        _ensure_time_remaining(query_deadline, f"query '{query}'")
        _ensure_time_remaining(total_deadline, "total scholarly run")
        item_timeout = _tightest_timeout(
            _remaining_seconds(query_deadline),
            _remaining_seconds(total_deadline),
        )
        try:
            publication = _run_with_timeout(
                item_timeout,
                f"query iteration for '{query}'",
                next,
                iterator,
            )
        except StopIteration:
            break
        candidate = _build_candidate(publication, query)
        if candidate is not None:
            out.append(candidate)
        if delay_seconds > 0:
            time.sleep(delay_seconds)
    return out


def _bibtex_for_candidate(
    candidate: Candidate,
    delay_seconds: float,
    *,
    candidate_timeout_seconds: float,
    total_deadline: float | None,
) -> str | None:
    """Handle bibtex for candidate logic for this module."""
    if SCHOLARLY_CLIENT is None:
        return None
    candidate_deadline = _deadline_after(candidate_timeout_seconds)
    try:
        _ensure_time_remaining(candidate_deadline, f"candidate '{candidate.paper_id}'")
        _ensure_time_remaining(total_deadline, "total scholarly run")
        bibtex = _run_with_timeout(
            _tightest_timeout(
                _remaining_seconds(candidate_deadline),
                _remaining_seconds(total_deadline),
            ),
            f"bibtex fetch for '{candidate.paper_id}'",
            SCHOLARLY_CLIENT.bibtex,
            candidate.raw_publication,
        )
    except Exception:
        try:
            _ensure_time_remaining(candidate_deadline, f"candidate '{candidate.paper_id}'")
            _ensure_time_remaining(total_deadline, "total scholarly run")
            filled = _run_with_timeout(
                _tightest_timeout(
                    _remaining_seconds(candidate_deadline),
                    _remaining_seconds(total_deadline),
                ),
                f"candidate fill for '{candidate.paper_id}'",
                SCHOLARLY_CLIENT.fill,
                candidate.raw_publication,
            )
            if delay_seconds > 0:
                time.sleep(delay_seconds)
            _ensure_time_remaining(candidate_deadline, f"candidate '{candidate.paper_id}'")
            _ensure_time_remaining(total_deadline, "total scholarly run")
            bibtex = _run_with_timeout(
                _tightest_timeout(
                    _remaining_seconds(candidate_deadline),
                    _remaining_seconds(total_deadline),
                ),
                f"filled bibtex fetch for '{candidate.paper_id}'",
                SCHOLARLY_CLIENT.bibtex,
                filled,
            )
        except Exception:
            return None

    if not isinstance(bibtex, str):
        return None
    stripped = bibtex.strip()
    return stripped if stripped else None


def retrieve_references(
    *,
    queries: list[str],
    max_refs: int,
    search_limit: int,
    sort_by: str,
    year_low: int | None,
    year_high: int | None,
    delay_seconds: float,
    query_timeout_seconds: float,
    candidate_timeout_seconds: float,
    total_timeout_seconds: float,
    max_consecutive_failures: int,
    progress_interval_seconds: float,
) -> tuple[list[str], list[dict[str, Any]]]:
    """Retrieve scholarly references with bounded time budgets."""
    normalized_queries = normalize_queries(list(queries))
    if not normalized_queries:
        raise SystemExit("At least one non-empty --query is required.")

    total_deadline = _deadline_after(total_timeout_seconds)
    total_queries = len(normalized_queries)
    candidates: dict[str, Candidate] = {}
    consecutive_failures = 0
    last_progress_at = 0.0

    for query_index, query in enumerate(normalized_queries, start=1):
        _ensure_time_remaining(total_deadline, "total scholarly run")
        _log(f"query start | query {query_index}/{total_queries} | {query}")
        try:
            rows = _search_candidates(
                query=query,
                limit=search_limit,
                sort_by=sort_by,
                year_low=year_low,
                year_high=year_high,
                delay_seconds=delay_seconds,
                query_timeout_seconds=query_timeout_seconds,
                total_deadline=total_deadline,
            )
        except Exception as exc:
            consecutive_failures += 1
            _log(f"query failed | query {query_index}/{total_queries} | {query} | {exc}")
            if consecutive_failures >= max_consecutive_failures:
                raise SystemExit(
                    f"Too many consecutive scholarly failures ({consecutive_failures}) while searching"
                ) from exc
            continue

        consecutive_failures = 0
        for candidate in rows:
            existing = candidates.get(candidate.paper_id)
            if existing is None or candidate.rank_score > existing.rank_score:
                candidates[candidate.paper_id] = candidate
        now = time.monotonic()
        if progress_interval_seconds <= 0 or now - last_progress_at >= progress_interval_seconds:
            last_progress_at = now
            _log_reference_progress(
                accepted_refs=0,
                target_refs=max_refs,
                processed_candidates=len(candidates),
                total_candidates=max(len(candidates), 1),
                query_index=query_index,
                total_queries=total_queries,
                stage=f"query complete: hits={len(rows)} unique_candidates={len(candidates)}",
            )

    ranked = sorted(
        candidates.values(),
        key=lambda row: (-row.rank_score, -row.citation_count, row.paper_id),
    )

    references: list[dict[str, Any]] = []
    used_keys: set[str] = set()
    used_paper_ids: set[str] = set()
    processed_candidates = 0
    for candidate in ranked:
        _ensure_time_remaining(total_deadline, "total scholarly run")
        if len(references) >= max_refs:
            break

        bibtex = _bibtex_for_candidate(
            candidate,
            delay_seconds,
            candidate_timeout_seconds=candidate_timeout_seconds,
            total_deadline=total_deadline,
        )
        if bibtex is None:
            consecutive_failures += 1
            if consecutive_failures >= max_consecutive_failures:
                raise SystemExit(
                    f"Too many consecutive scholarly failures ({consecutive_failures}) while resolving BibTeX"
                )
        else:
            consecutive_failures = 0

            key = bibtex_key(bibtex)
            if key is not None and key not in used_keys and candidate.paper_id not in used_paper_ids:
                used_keys.add(key)
                used_paper_ids.add(candidate.paper_id)
                source_url = candidate.source_url or f"https://scholar.google.com/scholar?q={candidate.paper_id}"
                references.append(
                    {
                        "id": f"ref.scholar.{hashlib.sha1(candidate.paper_id.encode('utf-8')).hexdigest()[:16]}",
                        "paper_id": candidate.paper_id,
                        "citation_key": key,
                        "title": candidate.title,
                        "year": candidate.year,
                        "venue": candidate.venue,
                        "citation_count": candidate.citation_count,
                        "query": candidate.query,
                        "source_url": source_url,
                        "retrieved_at": utc_now(),
                        "bibtex": bibtex,
                    }
                )
        processed_candidates += 1
        now = time.monotonic()
        if progress_interval_seconds <= 0 or now - last_progress_at >= progress_interval_seconds:
            last_progress_at = now
            _log_reference_progress(
                accepted_refs=len(references),
                target_refs=max_refs,
                processed_candidates=processed_candidates,
                total_candidates=len(ranked),
                stage="fetching bibtex",
            )

    return normalized_queries, references


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    if SCHOLARLY_CLIENT is None:
        raise SystemExit(
            "Missing dependency `scholarly`. Install with: python -m pip install scholarly"
        )
    if args.min_refs <= 0:
        raise SystemExit("--min-refs must be > 0")
    if args.max_refs < args.min_refs:
        raise SystemExit("--max-refs must be >= --min-refs")
    if args.search_limit <= 0:
        raise SystemExit("--search-limit must be > 0")
    if args.delay_seconds < 0:
        raise SystemExit("--delay-seconds must be >= 0")
    if args.proxy_setup_timeout_seconds <= 0:
        raise SystemExit("--proxy-setup-timeout-seconds must be > 0")
    if args.query_timeout_seconds <= 0:
        raise SystemExit("--query-timeout-seconds must be > 0")
    if args.candidate_timeout_seconds <= 0:
        raise SystemExit("--candidate-timeout-seconds must be > 0")
    if args.total_timeout_seconds <= 0:
        raise SystemExit("--total-timeout-seconds must be > 0")
    if args.max_consecutive_failures <= 0:
        raise SystemExit("--max-consecutive-failures must be > 0")
    if args.progress_interval_seconds < 0:
        raise SystemExit("--progress-interval-seconds must be >= 0")
    if args.year_low is not None and args.year_low < 1800:
        raise SystemExit("--year-low must be >= 1800")
    if args.year_high is not None and args.year_high < 1800:
        raise SystemExit("--year-high must be >= 1800")
    if args.year_low is not None and args.year_high is not None and args.year_low > args.year_high:
        raise SystemExit("--year-low must be <= --year-high")

    configured_proxy = configure_proxy(args)
    _log(f"proxy mode: {configured_proxy or 'none'}")

    queries, references = retrieve_references(
        queries=list(args.query),
        max_refs=args.max_refs,
        search_limit=args.search_limit,
        sort_by=args.sort_by,
        year_low=args.year_low,
        year_high=args.year_high,
        delay_seconds=args.delay_seconds,
        query_timeout_seconds=args.query_timeout_seconds,
        candidate_timeout_seconds=args.candidate_timeout_seconds,
        total_timeout_seconds=args.total_timeout_seconds,
        max_consecutive_failures=args.max_consecutive_failures,
        progress_interval_seconds=args.progress_interval_seconds,
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

    if len(references) < args.min_refs:
        if args.allow_partial_output and references:
            raise SystemExit(
                f"Partial scholarly output written: {len(references)} < required {args.min_refs}"
            )
        raise SystemExit(
            f"Not enough grounded references: {len(references)} < required {args.min_refs}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
