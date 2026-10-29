"""Render the grounded memory-ranking overview figure.

The diagram is deliberately schematic: it shows the fixed execution path and
the four objectives used by the VectorStore-backed long-term-memory path.
# @evidence[[paper_data.json#grounding_sources.retrieval-ranking-implementation]]
"""

from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "figures" / "pareto_memory_overview"


def add_box(ax, x, title, lines, width=2.55):
    box = FancyBboxPatch(
        (x, 0.55), width, 2.9,
        boxstyle="round,pad=0.03,rounding_size=0.10",
        linewidth=1.5, edgecolor="#28496d", facecolor="#ffffff",
    )
    ax.add_patch(box)
    ax.text(x + 0.16, 3.16, title, fontsize=12, fontweight="bold",
            color="#172a46", va="top")
    for i, line in enumerate(lines):
        ax.text(x + 0.16, 2.62 - 0.43 * i, line, fontsize=10,
                color="#304968", va="top")


def main():
    fig, ax = plt.subplots(figsize=(12.0, 4.2), dpi=180)
    fig.patch.set_facecolor("#f6f8fb")
    ax.set_facecolor("#f6f8fb")
    add_box(ax, 0.25, "Agent context", [
        "query and history",
        "VectorStore over-fetch",
        "scope filtering",
        "graph activation",
    ])
    add_box(ax, 3.20, "Objective vector", [
        "calibrated similarity",
        "dynamic retention",
        "importance",
        "graph relevance",
    ])
    add_box(ax, 6.15, "Pareto ranker", [
        "non-dominated fronts",
        "front rank ordering",
        "crowding distance",
        "similarity tie-break",
    ])
    add_box(ax, 9.10, "Agent input", [
        "Top-K memories",
        "diverse trade-offs",
        "context assembly",
    ], width=2.30)

    for x in (2.83, 5.78, 8.73):
        ax.add_patch(FancyArrowPatch(
            (x, 2.0), (x + 0.30, 2.0), arrowstyle="-|>",
            mutation_scale=14, linewidth=1.6, color="#5b789d",
        ))
    ax.text(6.0, 0.20, "Candidate generation and reinforcement mechanism remain unchanged; selected memories may change.",
            ha="center", fontsize=9.5, color="#475d78")
    ax.set_xlim(0, 11.7)
    ax.set_ylim(0, 3.75)
    ax.axis("off")
    fig.tight_layout(pad=0.25)
    # Keep the raster export at the journal-facing 300-DPI contract; vector
    # exports remain resolution independent.
    for suffix, kwargs in (("png", {"dpi": 300}), ("pdf", {}), ("svg", {})):
        fig.savefig(OUT.with_suffix(f".{suffix}"), bbox_inches="tight", **kwargs)
    plt.close(fig)


if __name__ == "__main__":
    main()
