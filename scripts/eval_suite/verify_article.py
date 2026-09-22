#!/usr/bin/env python3
"""Check build, citations, vector pages and text bounds in article outputs."""
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARTICLE = ROOT / "article"


def command(*args):
    return subprocess.check_output(args, text=True)


def check_pdf(path):
    root = ET.fromstring(command("pdftotext", "-bbox", str(path), "-"))
    ns = {"x": "http://www.w3.org/1999/xhtml"}
    clipped = []
    pages = root.findall(".//x:page", ns)
    for page_number, page in enumerate(pages, 1):
        width, height = float(page.get("width")), float(page.get("height"))
        for word in page.findall("x:word", ns):
            x0, y0, x1, y1 = (float(word.get(k)) for k in ("xMin", "yMin", "xMax", "yMax"))
            if x0 < -0.5 or y0 < -0.5 or x1 > width + 0.5 or y1 > height + 0.5:
                clipped.append({"page": page_number, "text": word.text})
    metadata = command("pdfinfo", str(path))
    return {"pages": len(pages), "clippedWords": clipped,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "author": next((line.partition(":")[2].strip() for line in metadata.splitlines() if line.startswith("Author:")), "")}


def main():
    assert sys.prefix != sys.base_prefix, "Run with project .venv Python"
    log = (ARTICLE / "elsarticle-template-num.log").read_text()
    bad = [line for line in log.splitlines() if re.search(r"Undefined|undefined|Overfull|Float too large|Fatal|LaTeX Error|^!", line)]
    main_source = (ARTICLE / "elsarticle-template-num.tex").read_text()
    sources = main_source + "\n" + "\n".join(path.read_text() for path in ARTICLE.glob("generated_*.tex"))
    refs = set(re.findall(r"\\ref\{([^}]+)\}", sources))
    labels = set(re.findall(r"\\label\{([^}]+)\}", sources))
    cited = {key.strip() for group in re.findall(r"\\cite[pt]?\{([^}]+)\}", sources) for key in group.split(",")}
    bibkeys = set(re.findall(r"@\w+\{([^,]+),", (ARTICLE / "references.bib").read_text()))
    highlights = [line[2:] for line in (ARTICLE / "highlights.txt").read_text().splitlines() if line.startswith("- ")]
    paths = [ARTICLE / name for name in ("elsarticle-template-num.pdf", "title_page.pdf", "highlights.pdf")]
    stems = ["system_architecture", "performance_comparison", "ablation_comparison", "cd_diagram", "sensitivity_analysis", "e2e_tradeoff", "mttr_breakdown"]
    paths += [ARTICLE / "figures" / (stem + ".pdf") for stem in stems]
    pdfs = {str(path.relative_to(ROOT)): check_pdf(path) for path in paths}
    vector = {}
    for stem in stems:
        svg = ARTICLE / "figures" / (stem + ".svg")
        tree = ET.parse(svg)
        assert tree.getroot().tag.endswith("svg")
        raster_images = len(tree.findall(".//{http://www.w3.org/2000/svg}image"))
        vector[stem] = {"svgParsed": True, "rasterImagesInSvg": raster_images}
        assert raster_images == 0
    v2_audit_path = ROOT / "backend/tests/evidence/evidence_integrity_audit_v2.json"
    v2_audit = json.loads(v2_audit_path.read_text()) if v2_audit_path.exists() else {}
    report = {
        "status": "BUILD_AND_STRUCTURAL_CHECKS_PASS",
        "researchReadiness": "V2_DATASET_E2E_AND_MAJOR_REVISION_PASS; public benchmark source verification remains an author task" if v2_audit.get("status") == "VALID" else "V2_EVIDENCE_AUDIT_FAILED",
        "buildErrorsOrUndefinedReferences": bad,
        "missingLabels": sorted(refs - labels), "missingCitationKeys": sorted(cited - bibkeys),
        "highlightsCharacterCounts": list(map(len, highlights)), "pdfs": pdfs, "vectorFigures": vector,
        "visualReview": "PDF pages were rasterized under article/qa. This tool checks text bounds, not visual appearance; human visual review remains required.",
        "bibliographyWarnings": [line for line in (ARTICLE / "elsarticle-template-num.blg").read_text().splitlines() if line.startswith("Warning--")],
    }
    assert not bad and not (refs - labels) and not (cited - bibkeys)
    assert 3 <= len(highlights) <= 5 and max(map(len, highlights)) <= 85
    assert not pdfs["article/elsarticle-template-num.pdf"]["author"]
    assert all(not item["clippedWords"] for item in pdfs.values())
    assert all(pdfs[f"article/figures/{stem}.pdf"]["pages"] == 1 for stem in stems)
    output = ARTICLE / "build_verification.json"
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"status": report["status"], "mainPages": pdfs["article/elsarticle-template-num.pdf"]["pages"],
                      "figures": len(stems), "missingCitations": len(cited - bibkeys), "missingReferences": len(refs - labels),
                      "bibliographyWarnings": report["bibliographyWarnings"]}, indent=2))
    print(output)


if __name__ == "__main__":
    main()
