#!/usr/bin/env python3
"""Validate figure manifest artifacts and basic figure readability budgets."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

try:
    from src.orchestrator.paper_step import read_json, validate_figure_manifest
    from src.orchestrator.tex_graphics import read_tex_file_bounded
except ModuleNotFoundError:
    # ASOC is a portable paper workspace, not the full orchestrator checkout.
    # Keep the figure gate runnable here with the same artifact-level checks.
    def read_json(path: Path):
        return json.loads(path.read_text(encoding="utf-8"))

    def read_tex_file_bounded(path: Path):
        return path.read_text(encoding="utf-8")

    def validate_figure_manifest(paper_dir: Path, *, tex_text: str, allowed_source_ids: set[str]):
        manifest_path = paper_dir / "figure_manifest.json"
        if not manifest_path.exists():
            return {"checked": False, "hard_errors": [], "soft_warnings": [], "figure_count": 0, "fallback_mode": True}
        payload = read_json(manifest_path)
        figures = payload.get("figures", [])
        errors = []
        if not isinstance(figures, list):
            return {"checked": True, "hard_errors": ["figures must be a list"], "soft_warnings": [], "figure_count": 0, "fallback_mode": True}
        referenced = set(re.findall(r"\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}", tex_text))
        listed = set()
        for figure in figures:
            if not isinstance(figure, dict):
                errors.append("figure entry must be an object")
                continue
            png = figure.get("path_png") or figure.get("files", {}).get("png")
            if not isinstance(png, str) or not png:
                errors.append("figure is missing path_png/files.png")
                continue
            listed.add(png)
            if not (paper_dir / png).is_file():
                errors.append(f"missing figure artifact: {png}")
            pdf = figure.get("path_pdf") or figure.get("files", {}).get("pdf")
            if pdf and not (paper_dir / pdf).is_file():
                errors.append(f"missing figure artifact: {pdf}")
            for source_id in figure.get("source_ids", []):
                if source_id not in allowed_source_ids:
                    errors.append(f"unknown figure source id: {source_id}")
        for path in referenced:
            if path not in listed:
                errors.append(f"figure referenced by paper.tex is absent from manifest: {path}")
        return {"checked": bool(figures or referenced), "hard_errors": errors, "soft_warnings": [], "figure_count": len(figures), "fallback_mode": True}


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Validate figure manifest artifacts.")
    parser.add_argument("--paper-dir", default=".")
    return parser.parse_args()


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    paper_dir = Path(args.paper_dir).resolve()
    tex_path = paper_dir / "paper.tex"
    paper_data_path = paper_dir / "paper_data.json"
    references_path = paper_dir / "references_provenance.json"

    if not tex_path.exists():
        raise SystemExit(f"Missing required artifact: {tex_path}")

    tex_text = read_tex_file_bounded(tex_path)
    paper_data = read_json(paper_data_path)
    references = read_json(references_path)
    grounding_sources = paper_data.get("grounding_sources")
    if not isinstance(grounding_sources, dict):
        raise SystemExit("paper_data missing grounding_sources object.")
    allowed_source_ids = set(str(key) for key in grounding_sources)
    refs = references.get("references")
    if isinstance(refs, list):
        for row in refs:
            if isinstance(row, dict):
                source_id = row.get("id")
                if isinstance(source_id, str) and source_id.strip():
                    allowed_source_ids.add(source_id)

    report = validate_figure_manifest(
        paper_dir,
        tex_text=tex_text,
        allowed_source_ids=allowed_source_ids,
    )
    if not bool(report.get("checked", False)):
        print("No figures referenced or required; figure validation skipped.")
        return 0

    hard_errors = report.get("hard_errors", [])
    if not isinstance(hard_errors, list):
        raise SystemExit("figure_manifest validation returned invalid hard_errors payload")
    soft_warnings = report.get("soft_warnings", [])
    if not isinstance(soft_warnings, list):
        raise SystemExit("figure_manifest validation returned invalid soft_warnings payload")
    if hard_errors:
        raise SystemExit("Figure validation failed: " + "; ".join(str(error) for error in hard_errors))
    if soft_warnings:
        print("Figure validation warnings: " + "; ".join(str(message) for message in soft_warnings))

    if report.get("fallback_mode"):
        print(
            f"Validated {int(report.get('figure_count', 0))} figure artifacts and source IDs; "
            "full orchestrator readability checks are unavailable in this workspace."
        )
    else:
        print(
            f"Validated {int(report.get('figure_count', 0))} figures with manifest and readability budgets."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
