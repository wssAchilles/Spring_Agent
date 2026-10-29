"""Build the 5:2 graphical abstract from grounded implementation metadata.

This is a conceptual implementation diagram, not a retrieval-quality result.
# @evidence[[paper_data.json#results]]
# @evidence[[paper_data.json#implementation_details.objective_counts]]
# @evidence[[paper_data.json#limitations]]
# @evidence[[paper_data.json#grounding_sources.retrieval-ranking-implementation]]
"""

import hashlib
import html
import json
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIGURES = ROOT / "figures"
DATA = json.loads((ROOT / "paper_data.json").read_text(encoding="utf-8"))
SVG = FIGURES / "graphical_abstract.svg"
PNG = FIGURES / "graphical_abstract.png"
PDF = FIGURES / "graphical_abstract.pdf"
SIDECAR = FIGURES / "graphical_abstract.png.prov.json"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    renderer = shutil.which("magick")
    font_matcher = shutil.which("fc-match")
    if renderer is None or font_matcher is None:
        raise SystemExit("ImageMagick 7 `magick` and fontconfig `fc-match` are required for SVG export")
    font_path = subprocess.check_output(
        [font_matcher, "-f", "%{file}", "Arial"], text=True
    ).strip()

    results = DATA["results"]
    counts = DATA["implementation_details"]["objective_counts"]
    text = html.escape
    lines = [
        ("Document adapter", f"{counts['document_adapter']} objectives"),
        ("Node-only path", f"{counts['node_only_adapter']} objectives"),
        ("Graph relevance", "document path only"),
    ]
    objective_lines = "".join(
        f'<text x="390" y="{244 + index * 35}" class="body">{text(label)}</text>'
        f'<text x="390" y="{264 + index * 35}" class="small">{text(value)}</text>'
        for index, (label, value) in enumerate(lines)
    )
    cards = [
        (48, "Fixed candidate set", [
            "Vector retrieval",
            "Metadata scope filtering",
            "Tenant isolation unverified",
        ], "#EAF2FA"),
        (356, "Objective vectors", [], "#FFFFFF"),
        (664, "Pareto ordering", [
            "Non-dominated fronts",
            "Crowding at the Top-K cut",
            "Relevance tie-break",
        ], "#FFF4D6"),
        (972, "Ranked Top-K", [
            "Returned to memory caller",
            "Existing recall update follows",
            "Selected items may change",
        ], "#E8F3EC"),
    ]
    card_svg = []
    for x, title, body, fill in cards:
        card_svg.append(
            f'<rect x="{x}" y="151" width="276" height="221" rx="16" '
            f'fill="{fill}" stroke="#B9C9DA" stroke-width="2"/>'
            f'<text x="{x + 18}" y="188" class="card-title">{text(title)}</text>'
        )
        if body:
            card_svg.extend(
                f'<text x="{x + 18}" y="{239 + index * 36}" class="body">{text(line)}</text>'
                for index, line in enumerate(body)
            )

    arrows = "".join(
        f'<line x1="{x}" y1="261" x2="{x + 20}" y2="261" class="arrow"/>'
        f'<polygon points="{x + 18},255 {x + 29},261 {x + 18},267" fill="#0F4D92"/>'
        for x in (326, 634, 942)
    )
    latency_ms = results["n1000_median_latency_ms"]
    metric = (
        f"Kernel evidence: {results['targeted_tests']} tests passed · "
        f"N={results['n1000_candidates']:,} · "
        f"{results['n1000_embedding_dimensions']}-D · {latency_ms:.3f} ms median"
    )
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1328" height="531" viewBox="0 0 1328 531">
<rect width="1328" height="531" fill="#F6F8FB"/>
<style>
  .title {{ font-family: Arial; font-size: 28px; font-weight: 700; fill: #172A46; }}
  .subtitle {{ font-family: Arial; font-size: 16px; fill: #53677F; }}
  .card-title {{ font-family: Arial; font-size: 19px; font-weight: 700; fill: #0F4D92; }}
  .body {{ font-family: Arial; font-size: 15px; fill: #172A46; }}
  .small {{ font-family: Arial; font-size: 13px; fill: #53677F; }}
  .metric {{ font-family: Arial; font-size: 16px; font-weight: 700; fill: #172A46; }}
  .limitation {{ font-family: Arial; font-size: 14px; fill: #53677F; }}
  .arrow {{ stroke: #0F4D92; stroke-width: 2.5; }}
</style>
<text x="48" y="52" class="title">Pareto ranking for long-horizon agent memory</text>
<text x="48" y="83" class="subtitle">A bounded re-ranking step preserves objective trade-offs after candidate generation.</text>
{''.join(card_svg)}
{objective_lines}
{arrows}
<text x="48" y="422" class="metric">{text(metric)}</text>
<text x="48" y="454" class="limitation">No retrieval-quality, statistical-significance, or end-to-end latency evaluation.</text>
<text x="1280" y="499" text-anchor="end" class="small">Implementation overview · not a quality result</text>
</svg>'''
    SVG.write_text(svg, encoding="utf-8")
    subprocess.run([
        renderer, "-font", font_path, "-background", "#F6F8FB", "-density", "300", str(SVG),
        "-resize", "1328x531!", "-units", "PixelsPerInch", "-density", "300", str(PNG),
    ], check=True)
    subprocess.run([
        renderer, "-font", font_path, "-density", "300", str(SVG), "-units", "PixelsPerInch", str(PDF),
    ], check=True)

    sidecar = {
        "schema_name": "scientific-evidence.artifact-manifest",
        "artifact": {
            "path": "figures/graphical_abstract.png",
            "kind": "figure",
            "media_type": "image/png",
            "sha256": sha256(PNG),
        },
        "derived_from": [
            "@evidence[[paper_data.json#results]]",
            "@evidence[[paper_data.json#implementation_details.objective_counts]]",
            "@evidence[[paper_data.json#grounding_sources.retrieval-ranking-implementation]]",
            "@evidence[[figures/make_graphical_abstract.py]]",
        ],
        "generated_by": {
            "script_path": "figures/make_graphical_abstract.py",
            "command": ["python3", "figures/make_graphical_abstract.py"],
            "cwd": ".",
            "script_sha256": sha256(Path(__file__)),
        },
        "notes": "SVG diagram rasterized by ImageMagick as a 1328x531, 300-DPI graphical abstract; no quality result is depicted.",
    }
    SIDECAR.write_text(json.dumps(sidecar, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
