#!/usr/bin/env python3
"""Validate rendered Supplementary Information in a compiled paper PDF."""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
from pathlib import Path


def _letter_spaced_word_pattern(word: str) -> str:
    return r"[ \t]*".join(re.escape(character) for character in word)


SUPPLEMENT_HEADING_RE = re.compile(
    rf"(?mi)^[ \t]*(?:(?:appendix[ \t]+)?[A-Z0-9]+(?:\.\d+)*[.:]?[ \t]+)?"
    rf"{_letter_spaced_word_pattern('supplementary')}[ \t]+"
    rf"{_letter_spaced_word_pattern('information')}[ \t]*$",
)
REFERENCE_HEADING_RE = re.compile(
    rf"(?mi)^[ \t]*(?:{_letter_spaced_word_pattern('references')}|"
    rf"{_letter_spaced_word_pattern('bibliography')})[ \t]*$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate rendered Supplementary Information in a PDF."
    )
    parser.add_argument("--pdf", required=True)
    return parser.parse_args()


def validate_rendered_supplementary_information(page_texts: list[str]) -> list[str]:
    supplement_page_index = next(
        (
            index
            for index, page_text in enumerate(page_texts)
            if SUPPLEMENT_HEADING_RE.search(page_text)
        ),
        None,
    )
    if supplement_page_index is None:
        return ["compiled paper is missing the Supplementary Information heading"]

    heading_page_text = page_texts[supplement_page_index]
    heading_match = SUPPLEMENT_HEADING_RE.search(heading_page_text)
    assert heading_match is not None
    reference_page_indices = [
        index
        for index, page_text in enumerate(page_texts[:supplement_page_index])
        if REFERENCE_HEADING_RE.search(page_text)
    ]
    errors: list[str] = []
    if not reference_page_indices:
        errors.append("compiled paper is missing the References heading before SI")
    heading_page_prefix = heading_page_text[: heading_match.start()]
    prefix_lines = [line.strip() for line in heading_page_prefix.splitlines() if line.strip()]
    earlier_header_lines = {
        line.strip()
        for page_text in page_texts[:supplement_page_index]
        for line in page_text.splitlines()[:2]
        if line.strip()
    }
    substantive_prefix_lines = [
        line
        for line in prefix_lines
        if re.fullmatch(r"(?:page\s+)?\d+", line, re.IGNORECASE) is None
        and line not in earlier_header_lines
    ]
    if substantive_prefix_lines:
        errors.append(
            "compiled Supplementary Information must start on a separate page after References"
        )

    supplement_chunks = [
        heading_page_text[heading_match.end() :],
        *page_texts[supplement_page_index + 1 :],
    ]
    substantive_chunks: list[str] = []
    for chunk in supplement_chunks:
        later_references = REFERENCE_HEADING_RE.search(chunk)
        if later_references is not None:
            substantive_chunks.append(chunk[: later_references.start()])
            break
        substantive_chunks.append(chunk)
    supplement_text = "\n".join(substantive_chunks)
    visible_character_count = len(re.sub(r"\s+", " ", supplement_text).strip())
    if visible_character_count < 100:
        errors.append(
            "compiled Supplementary Information must contain at least 100 visible characters"
        )
    return errors


def main() -> int:
    args = parse_args()
    pdf_path = Path(args.pdf)
    if not pdf_path.is_file():
        raise SystemExit(f"Missing compiled PDF: {pdf_path}")
    pdftotext = shutil.which("pdftotext")
    if pdftotext is None:
        raise SystemExit("Missing required command: pdftotext")
    extracted = subprocess.run(
        [pdftotext, "-layout", str(pdf_path), "-"],
        check=False,
        capture_output=True,
        text=True,
    )
    if extracted.returncode != 0:
        raise SystemExit(f"pdftotext failed: {extracted.stderr.strip()}")
    page_texts = extracted.stdout.split("\f")
    errors = validate_rendered_supplementary_information(page_texts)
    if errors:
        raise SystemExit("Rendered Supplementary Information validation failed: " + "; ".join(errors))
    print("Validated rendered Supplementary Information.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
