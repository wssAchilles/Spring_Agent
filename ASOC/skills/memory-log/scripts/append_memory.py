#!/usr/bin/env python3
"""Append validated local/global memory entries for idea workflows.

Two modes:

* default (event append) — appends one memory event to ``memory.md`` (local +
  global) plus one JSONL record to ``memory.jsonl``. Existing behavior.
* ``--write-conclusions`` — atomically replaces ``conclusions.md`` with a
  validated single-paragraph distilled summary and appends a record to
  ``conclusions_history.jsonl``. Validation rejects empty input,
  multi-paragraph input, or input over 300 words.
"""

from __future__ import annotations

import argparse
import fcntl
import json
import os
import stat
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

CONCLUSIONS_WORD_LIMIT = 300
GATE_TRIGGER_VALUES = ("a", "b", "c", "d")

LOCAL_MEMORY_HEADERS = """# Memory Log

## Context
## Idea Discoveries
## Program Evaluations
## Failures / Regressions
## Next Hypotheses
"""

GLOBAL_MEMORY_HEADER = "# Global Memory Log\n\n"


def utc_now() -> str:
    """Return the current UTC timestamp string."""
    return datetime.now(timezone.utc).isoformat()


def _load_event(*, event_json: str | None, event_file: Path | None) -> dict[str, Any]:
    """Load event."""
    if event_json:
        parsed = json.loads(event_json)
    elif event_file is not None:
        parsed = json.loads(event_file.read_text(encoding="utf-8"))
    else:
        raw = input().strip()
        if not raw:
            raise ValueError("missing event payload")
        parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError("event payload must be a JSON object")
    return parsed


def _ensure_required_fields(event: dict[str, Any]) -> dict[str, Any]:
    """Ensure required fields."""
    normalized = dict(event)
    if not isinstance(normalized.get("timestamp"), str) or not normalized["timestamp"].strip():
        normalized["timestamp"] = utc_now()

    required_string_fields = ("step", "scope", "summary", "evidence", "next_action")
    for key in required_string_fields:
        value = normalized.get(key)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"missing required string field: {key}")
        normalized[key] = value.strip()

    for key in ("idea_id", "attempt_id", "program_id"):
        value = normalized.get(key)
        if value is None:
            continue
        if not isinstance(value, str):
            normalized[key] = str(value)
        normalized[key] = normalized[key].strip() or None

    metrics = normalized.get("metrics")
    if metrics is not None and not isinstance(metrics, dict):
        raise ValueError("metrics must be an object when present")
    rubrics = normalized.get("rubrics")
    if rubrics is not None and not isinstance(rubrics, list):
        raise ValueError("rubrics must be an array when present")
    tags = normalized.get("tags")
    if tags is not None:
        if not isinstance(tags, list):
            raise ValueError("tags must be an array when present")
        normalized["tags"] = [str(item).strip() for item in tags if str(item).strip()]
    return normalized


def _markdown_entry(event: dict[str, Any]) -> str:
    """Handle markdown entry logic for this module."""
    lines = [
        f"### {event['timestamp']} | step={event['step']} | scope={event['scope']}",
        f"- summary: {event['summary']}",
        f"- evidence: {event['evidence']}",
        f"- next_action: {event['next_action']}",
    ]

    for key in ("idea_id", "attempt_id", "program_id"):
        value = event.get(key)
        if isinstance(value, str) and value.strip():
            lines.append(f"- {key}: {value.strip()}")

    metrics = event.get("metrics")
    if isinstance(metrics, dict) and metrics:
        lines.append(f"- metrics: {json.dumps(metrics, sort_keys=True)}")
    rubrics = event.get("rubrics")
    if isinstance(rubrics, list) and rubrics:
        lines.append(f"- rubrics: {json.dumps(rubrics, sort_keys=True)}")
    tags = event.get("tags")
    if isinstance(tags, list) and tags:
        lines.append(f"- tags: {', '.join(str(item) for item in tags)}")

    return "\n".join(lines) + "\n\n"


def _append_text(path: Path, text: str, *, header: str) -> None:
    """Safely initialize and append text to a regular file."""
    fd = _open_regular_for_append(path)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        size = os.fstat(fd).st_size
        if not size:
            _write_all(fd, header.encode("utf-8"))
        elif os.pread(fd, 1, size - 1) != b"\n":
            _write_all(fd, b"\n")
        _write_all(fd, text.encode("utf-8"))
    finally:
        try:
            fcntl.flock(fd, fcntl.LOCK_UN)
        finally:
            os.close(fd)


def _append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    """Append one JSON object as a JSONL record."""
    line = (json.dumps(payload, sort_keys=True) + "\n").encode("utf-8")
    fd = _open_regular_for_append(path)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        size = os.fstat(fd).st_size
        if size and os.pread(fd, 1, size - 1) != b"\n":
            _write_all(fd, b"\n")
        _write_all(fd, line)
    finally:
        try:
            fcntl.flock(fd, fcntl.LOCK_UN)
        finally:
            os.close(fd)


def _open_regular_for_append(path: Path) -> int:
    """Open an append target without following its parent or leaf."""
    directory_fd = _walk_directory_no_follow(path.parent)
    try:
        fd = os.open(
            path.name,
            os.O_APPEND
            | os.O_CREAT
            | os.O_RDWR
            | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_NONBLOCK", 0),
            0o644,
            dir_fd=directory_fd,
        )
    finally:
        os.close(directory_fd)
    if stat.S_ISREG(os.fstat(fd).st_mode):
        return fd
    os.close(fd)
    raise OSError(f"unsafe non-regular append target: {path}")


def _walk_directory_no_follow(directory: Path) -> int:
    """Create and walk a directory path without following any component."""
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
    if directory.is_absolute():
        descriptor = os.open(directory.anchor, flags)
        components = directory.parts[1:]
    else:
        descriptor = os.open(".", flags)
        components = directory.parts
    try:
        for component in components:
            if component in ("", "."):
                continue
            try:
                os.mkdir(component, mode=0o777, dir_fd=descriptor)
            except FileExistsError:
                pass
            next_descriptor = os.open(component, flags, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = next_descriptor
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _write_all(fd: int, data: bytes) -> None:
    """Write every byte or propagate the write failure."""
    remaining = memoryview(data)
    while remaining:
        written = os.write(fd, remaining)
        if written <= 0:
            raise OSError("write returned no progress")
        remaining = remaining[written:]


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Append one memory event, or atomically rewrite conclusions.md.",
    )
    parser.add_argument(
        "--write-conclusions",
        action="store_true",
        help="Switch into conclusions-write mode (validates and replaces conclusions.md).",
    )
    # Event-append mode (existing behavior).
    parser.add_argument("--event-json", default=None, help="Event payload as JSON object string")
    parser.add_argument("--event-file", default=None, help="Path to event JSON file")
    parser.add_argument(
        "--local-memory-md",
        default="./memory.md",
        help="Local workspace memory markdown path",
    )
    parser.add_argument(
        "--global-memory-md",
        default=None,
        help="Global markdown memory path (event mode)",
    )
    parser.add_argument(
        "--global-memory-jsonl",
        default=None,
        help="Global JSONL memory path (event mode)",
    )
    # Conclusions-write mode.
    parser.add_argument(
        "--conclusions-md",
        default=None,
        help="Path of conclusions.md to overwrite (--write-conclusions mode)",
    )
    parser.add_argument(
        "--conclusions-history-jsonl",
        default=None,
        help="Path of conclusions_history.jsonl to append a record to",
    )
    parser.add_argument(
        "--conclusions-text",
        default=None,
        help="Conclusion paragraph (omit to read from stdin)",
    )
    parser.add_argument(
        "--cycle-index",
        type=int,
        default=None,
        help="Current cycle index for the history record",
    )
    parser.add_argument(
        "--worker-index",
        type=int,
        default=None,
        help="Current worker index for the history record",
    )
    parser.add_argument(
        "--gate-trigger",
        default=None,
        help="Gate trigger letter (a|b|c|d) recorded in history",
    )
    parser.add_argument(
        "--gate-reason",
        default=None,
        help="One-sentence reason for the update; for trigger d name the dimension",
    )
    return parser.parse_args()


def _read_conclusions_text(args: argparse.Namespace) -> str:
    """Resolve the candidate paragraph from ``--conclusions-text`` or stdin."""
    if isinstance(args.conclusions_text, str):
        return args.conclusions_text
    return sys.stdin.read()


def _validate_conclusions_paragraph(text: str) -> str:
    """Return the trimmed paragraph or raise ``ValueError`` describing the failure."""
    stripped = text.strip()
    if not stripped:
        raise ValueError("conclusion paragraph is empty after trimming")
    if "\n\n" in stripped:
        raise ValueError("single paragraph required: text contains a blank line")
    word_count = len(stripped.split())
    if word_count > CONCLUSIONS_WORD_LIMIT:
        raise ValueError(
            f"word count > {CONCLUSIONS_WORD_LIMIT} (got {word_count}); shorten the paragraph"
        )
    return stripped


def _atomic_overwrite(path: Path, text: str) -> None:
    """Atomically replace ``path`` with ``text`` (write to ``.tmp`` then ``os.replace``)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _read_prior_conclusions(path: Path) -> str:
    """Return the prior content of conclusions.md or an empty string if missing."""
    if not path.is_file():
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def _validate_conclusions_args(args: argparse.Namespace) -> None:
    """Raise ``ValueError`` if conclusions-mode args are missing or invalid."""
    missing: list[str] = []
    if not args.conclusions_md:
        missing.append("--conclusions-md")
    if not args.conclusions_history_jsonl:
        missing.append("--conclusions-history-jsonl")
    if args.cycle_index is None:
        missing.append("--cycle-index")
    if args.worker_index is None:
        missing.append("--worker-index")
    if not args.gate_trigger:
        missing.append("--gate-trigger")
    if not args.gate_reason:
        missing.append("--gate-reason")
    if missing:
        raise ValueError(f"missing required conclusions-mode flags: {', '.join(missing)}")
    trigger = str(args.gate_trigger).strip().lower()
    if trigger not in GATE_TRIGGER_VALUES:
        raise ValueError(
            f"gate-trigger must be one of {GATE_TRIGGER_VALUES} (got {args.gate_trigger!r})"
        )


def _run_write_conclusions(args: argparse.Namespace) -> int:
    """Execute the ``--write-conclusions`` mode."""
    try:
        _validate_conclusions_args(args)
    except ValueError as exc:
        print(f"append_memory: {exc}", file=sys.stderr)
        return 1

    raw_text = _read_conclusions_text(args)
    try:
        paragraph = _validate_conclusions_paragraph(raw_text)
    except ValueError as exc:
        print(f"append_memory: rejected conclusions update — {exc}", file=sys.stderr)
        return 1

    conclusions_path = Path(args.conclusions_md)
    history_path = Path(args.conclusions_history_jsonl)
    prior = _read_prior_conclusions(conclusions_path)

    trigger = str(args.gate_trigger).strip().lower()
    record = {
        "timestamp": utc_now(),
        "cycle_index": int(args.cycle_index),
        "worker_index": int(args.worker_index),
        "gate_trigger": trigger,
        "gate_reason": str(args.gate_reason).strip(),
        "prior": prior,
        "new": paragraph,
    }
    # History append must succeed before the live file changes so a crash
    # mid-write can never lose the prior version.
    _append_jsonl(history_path, record)
    _atomic_overwrite(conclusions_path, paragraph + "\n")
    return 0


def _run_append_event(args: argparse.Namespace) -> int:
    """Execute the default event-append mode."""
    if not args.global_memory_md or not args.global_memory_jsonl:
        print(
            "append_memory: --global-memory-md and --global-memory-jsonl are required "
            "in event-append mode",
            file=sys.stderr,
        )
        return 1
    event_file = Path(args.event_file) if isinstance(args.event_file, str) and args.event_file else None
    event = _ensure_required_fields(
        _load_event(event_json=args.event_json, event_file=event_file),
    )

    local_memory_path = Path(args.local_memory_md)
    global_memory_md_path = Path(args.global_memory_md)
    global_memory_jsonl_path = Path(args.global_memory_jsonl)

    _append_text(local_memory_path, _markdown_entry(event), header=LOCAL_MEMORY_HEADERS)
    _append_text(global_memory_md_path, _markdown_entry(event), header=GLOBAL_MEMORY_HEADER)
    _append_jsonl(global_memory_jsonl_path, event)
    return 0


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    if args.write_conclusions:
        return _run_write_conclusions(args)
    return _run_append_event(args)


if __name__ == "__main__":
    raise SystemExit(main())
