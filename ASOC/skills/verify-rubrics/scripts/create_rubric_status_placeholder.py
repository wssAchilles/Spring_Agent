#!/usr/bin/env python3
"""Create placeholder rubric_status.json from active_rubrics.json.

Reads the active rubric bundle from the idea directory, extracts every
rubric id, and writes a structurally valid rubric_status.json with all
references in place (pass=false, empty evidence) ready to be filled by
the agent after evaluating a program.

Usage:
    python create_rubric_status_placeholder.py <working_dir> [options]

Examples:
    # Idea-level placeholder only (./rubric_status.json):
    python create_rubric_status_placeholder.py /path/to/idea_dir

    # Also create program-level placeholder (./program_1/rubric_status.json):
    python create_rubric_status_placeholder.py /path/to/idea_dir --program-index 1
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any


def _load_json(path: Path) -> Any:
    """Load and parse a JSON file."""
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON in {path}: {exc}") from exc


def _resolve_idea_id(working_dir: Path, cli_idea_id: str) -> str:
    """Resolve the idea_id from CLI arg, active_idea.json, or env var."""
    if cli_idea_id:
        return cli_idea_id

    # Try active_idea.json
    active_idea_path = working_dir / "active_idea.json"
    if active_idea_path.exists():
        try:
            data = _load_json(active_idea_path)
            if isinstance(data, dict):
                idea_id = data.get("idea_id", "")
                if isinstance(idea_id, str) and idea_id.strip():
                    return idea_id.strip()
        except SystemExit:
            pass

    # Fall back to environment variable
    env_val = os.environ.get("IDEA_ID", "")
    return env_val.strip() if env_val.strip() else ""


def _resolve_attempt_id(cli_attempt_id: str) -> str:
    """Resolve the attempt_id from CLI arg or env var."""
    if cli_attempt_id:
        return cli_attempt_id
    env_val = os.environ.get("ATTEMPT_ID", "")
    return env_val.strip() if env_val.strip() else ""


def _build_placeholder(
    rubrics: list[dict[str, Any]],
    attempt_id: str,
    idea_id: str,
    variant_id: str,
) -> dict[str, Any]:
    """Build a structurally valid rubric_status.json placeholder."""
    rubric_entries = []
    for rubric in rubrics:
        if not isinstance(rubric, dict):
            continue
        rubric_id = rubric.get("id", "")
        if not isinstance(rubric_id, str) or not rubric_id.strip():
            continue
        rubric_entries.append({
            "id": rubric_id,
            "pass": False,
            "evidence": "",
        })

    return {
        "variant_id": variant_id,
        "attempt_id": attempt_id,
        "idea_id": idea_id,
        "overall_pass": False,
        "rubrics": rubric_entries,
        "metrics": {
            "combined_score": None,
            "correct": False,
        },
        "next_recommendation": "",
    }


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description=(
            "Create placeholder rubric_status.json from active_rubrics.json. "
            "Reads the active rubric bundle, extracts rubric ids, and writes "
            "a ready-to-fill rubric_status.json."
        ),
    )
    parser.add_argument(
        "working_dir",
        help="Path to the agent's working directory (idea directory).",
    )
    parser.add_argument(
        "--program-index",
        type=int,
        default=None,
        help=(
            "Program index (e.g. 0, 1, 2). When provided, also creates "
            "rubric_status.json inside program_<idx>/."
        ),
    )
    parser.add_argument(
        "--variant-id",
        default="",
        help="Variant ID to pre-fill. Falls back to the VARIANT_ID environment variable.",
    )
    parser.add_argument(
        "--attempt-id",
        default="",
        help=(
            "Attempt ID to pre-fill. Falls back to the ATTEMPT_ID "
            "environment variable."
        ),
    )
    parser.add_argument(
        "--idea-id",
        default="",
        help=(
            "Idea ID to pre-fill. Falls back to active_idea.json, then "
            "the IDEA_ID environment variable."
        ),
    )
    return parser.parse_args()


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    working_dir = Path(args.working_dir)

    if not working_dir.is_dir():
        raise SystemExit(f"Working directory does not exist: {working_dir}")

    # Load active rubrics
    active_rubrics_path = working_dir / "active_rubrics.json"
    if not active_rubrics_path.exists():
        raise SystemExit(f"Missing active_rubrics.json: {active_rubrics_path}")

    rubrics = _load_json(active_rubrics_path)
    if not isinstance(rubrics, list):
        raise SystemExit(
            f"active_rubrics.json must be a JSON array, "
            f"got {type(rubrics).__name__}"
        )

    # Resolve IDs
    idea_id = _resolve_idea_id(working_dir, args.idea_id)
    attempt_id = _resolve_attempt_id(args.attempt_id)

    # Build and write placeholder
    variant_id = args.variant_id.strip() or os.environ.get("VARIANT_ID", "").strip()
    placeholder = _build_placeholder(rubrics, attempt_id, idea_id, variant_id)
    content = json.dumps(placeholder, indent=2, sort_keys=True) + "\n"

    # Write idea-level rubric_status.json
    idea_level_path = working_dir / "rubric_status.json"
    idea_level_path.write_text(content, encoding="utf-8")
    print(f"Created: {idea_level_path}")

    # Write program-level rubric_status.json if requested
    if args.program_index is not None:
        program_dir = working_dir / f"program_{args.program_index}"
        program_dir.mkdir(parents=True, exist_ok=True)
        program_level_path = program_dir / "rubric_status.json"
        program_level_path.write_text(content, encoding="utf-8")
        print(f"Created: {program_level_path}")

    print(f"  idea_id: {idea_id or '(empty)'}")
    print(f"  variant_id: {variant_id or '(empty)'}")
    print(f"  attempt_id: {attempt_id or '(empty)'}")
    print(f"  rubrics: {len(placeholder['rubrics'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
