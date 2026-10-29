#!/usr/bin/env python3
"""Create placeholder idea_bundle.json and target_rubrics.json files.

Produces deterministic, structurally valid placeholder files that follow
the create-idea skill template.  The agent (or a human) then fills in
the actual hypothesis, implementation plan, and rubrics.

Usage:
    python create_idea_placeholder.py <working_dir> [--cycle-index N]
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path


def _placeholder_rubrics() -> list[dict[str, str]]:
    """Return the minimum valid rubric set (one metric + one implementation)."""
    return [
        {
            "id": "TODO_metric_rubric_id",
            "description": "TODO: describe a concrete, measurable metric threshold",
            "type": "metric",
        },
        {
            "id": "TODO_implementation_rubric_id",
            "description": "TODO: describe a concrete implementation requirement",
            "type": "implementation",
        },
    ]


def _placeholder_bundle(cycle_index: int) -> dict[str, object]:
    """Return a structurally valid idea bundle with placeholder values."""
    return {
        "idea_id": f"cycle{cycle_index:02d}_TODO_idea_slug",
        "title": "TODO: Idea Title",
        "hypothesis": "TODO: state the hypothesis to test",
        "implementation_plan": [
            "TODO: step 1",
        ],
        "target_rubrics": _placeholder_rubrics(),
        "notes_for_agent": "TODO: add guidance, risks, and constraints for the agent",
    }


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Create placeholder idea_bundle.json and target_rubrics.json.",
    )
    parser.add_argument(
        "working_dir",
        help="Path to the agent's working directory (idea output directory).",
    )
    parser.add_argument(
        "--cycle-index",
        type=int,
        default=None,
        help=(
            "Cycle index for the idea_id prefix (e.g. 1 -> 'cycle01_...'). "
            "Falls back to the CYCLE_INDEX environment variable, then defaults to 1."
        ),
    )
    return parser.parse_args()


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    working_dir = Path(args.working_dir)

    if not working_dir.is_dir():
        raise SystemExit(f"Working directory does not exist: {working_dir}")

    # Resolve cycle index: CLI flag > env var > default 1
    cycle_index = args.cycle_index
    if cycle_index is None:
        env_val = os.environ.get("CYCLE_INDEX", "")
        if env_val.strip().isdigit() and int(env_val.strip()) > 0:
            cycle_index = int(env_val.strip())
        else:
            cycle_index = 1

    bundle = _placeholder_bundle(cycle_index)
    rubrics = bundle["target_rubrics"]
    assert isinstance(rubrics, list)

    bundle_path = working_dir / "idea_bundle.json"
    rubrics_path = working_dir / "target_rubrics.json"

    bundle_path.write_text(
        json.dumps(bundle, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    rubrics_path.write_text(
        json.dumps(rubrics, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print(f"Created: {bundle_path}")
    print(f"Created: {rubrics_path}")
    print(f"  idea_id: {bundle['idea_id']}")
    print(f"  rubrics: {len(rubrics)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
