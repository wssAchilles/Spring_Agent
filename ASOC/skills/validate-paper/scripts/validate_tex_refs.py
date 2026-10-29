#!/usr/bin/env python3
"""Validate that paper citations align with bibliography entries."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

SECTION_PATTERNS = {
    "abstract": re.compile(
        r"\\begin\{abstract\}|\\section\*?\{[^}]*abstract[^}]*\}",
        re.IGNORECASE,
    ),
    "introduction": re.compile(r"\\section\*?\{[^}]*introduction[^}]*\}", re.IGNORECASE),
    "related work": re.compile(r"\\section\*?\{[^}]*related work[^}]*\}", re.IGNORECASE),
    "method": re.compile(r"\\section\*?\{[^}]*method[^}]*\}", re.IGNORECASE),
    "results": re.compile(r"\\section\*?\{[^}]*results[^}]*\}", re.IGNORECASE),
    "conclusion": re.compile(r"\\section\*?\{[^}]*conclusion[^}]*\}", re.IGNORECASE),
    "references": re.compile(
        r"\\bibliography\{|\\printbibliography|\\begin\{thebibliography\}|\\section\*?\{[^}]*references[^}]*\}",
        re.IGNORECASE,
    ),
}
CITE_RE = re.compile(r"\\cite[a-zA-Z*]*\{([^}]+)\}")
KEY_RE = re.compile(r"@\w+\{([^,]+),")
SUPPLEMENT_HEADING_RE = re.compile(
    r"\\section\s*\{\s*supplementary\s+information\s*\}",
    re.IGNORECASE,
)
BIBLIOGRAPHY_RE = re.compile(
    r"\\bibliography\s*\{[^}]+\}|\\printbibliography\b(?:\[[^\]]*\])?"
    r"|\\begin\s*\{thebibliography\}",
    re.IGNORECASE,
)
CLEARPAGE_RE = re.compile(r"\\clearpage\b", re.IGNORECASE)
NEXT_SECTION_RE = re.compile(r"\\section\*?\s*(?:\[[^\[\]]*\]\s*)?\{")
LET_SECTION_ALIAS_RE = re.compile(
    r"\\let\s*\\[a-zA-Z@]+\s*=?\s*\\section\b", re.IGNORECASE
)
NEWCOMMAND_DEFINITION_RE = re.compile(
    r"\\(?:newcommand|renewcommand)\*?\s*\{", re.IGNORECASE
)
DEF_DEFINITION_RE = re.compile(
    r"\\(?:def|gdef|edef|xdef)\s*\\[a-zA-Z@]+", re.IGNORECASE
)
TEX_IMPORT_RE = re.compile(r"\\(?:input|include)\b", re.IGNORECASE)
SUBSTANTIVE_SUPPLEMENT_RE = re.compile(
    r"\\subsection\*?\s*\{|\\begin\s*\{(?:table|figure)\*?\}",
    re.IGNORECASE,
)
SUPPLEMENT_PLACEHOLDER_TEXTS = (
    "add supplementary material only when grounded artifacts support it",
    "replace with grounded extended methods, reproducibility details, additional analyses, or supporting tables and figures",
)
CONDITIONAL_TOKEN_RE = re.compile(r"\\[a-zA-Z@]+")
NEWIF_RE = re.compile(r"\\newif\s*(?P<name>\\if[a-zA-Z@]+)")
CONDITIONAL_OPENERS = frozenset(
    {
        r"\if",
        r"\ifcase",
        r"\ifcat",
        r"\ifcondition",
        r"\ifcsname",
        r"\ifdefined",
        r"\ifdim",
        r"\ifeof",
        r"\iffalse",
        r"\iffontchar",
        r"\ifhbox",
        r"\ifhmode",
        r"\ifincsname",
        r"\ifinner",
        r"\ifluatex",
        r"\ifmmode",
        r"\ifnum",
        r"\ifodd",
        r"\ifpdf",
        r"\ifpdfabsdim",
        r"\ifpdfabsnum",
        r"\ifpdfprimitive",
        r"\iftrue",
        r"\ifvbox",
        r"\ifvmode",
        r"\ifvoid",
        r"\ifx",
        r"\ifxetex",
    }
)
NON_CONDITIONAL_IF_MACROS = frozenset(
    {
        r"\ifblank",
        r"\ifbool",
        r"\ifboolexpr",
        r"\ifcsdef",
        r"\ifcsempty",
        r"\ifcsequal",
        r"\ifcsmacro",
        r"\ifcsparam",
        r"\ifcsprotected",
        r"\ifcsstring",
        r"\ifcsundef",
        r"\ifcsvoid",
        r"\ifdimcomp",
        r"\ifdimequal",
        r"\ifdimgreater",
        r"\ifdimless",
        r"\ifdef",
        r"\ifdefempty",
        r"\ifdefequal",
        r"\ifdefltxprotect",
        r"\ifdefmacro",
        r"\ifdefparam",
        r"\ifdefprotected",
        r"\ifdefstring",
        r"\ifdefvoid",
        r"\iflanguage",
        r"\ifltxcount",
        r"\ifltxcounter",
        r"\ifnumcomp",
        r"\ifnumequal",
        r"\ifnumgreater",
        r"\ifnumless",
        r"\ifstrequal",
        r"\ifstrempty",
        r"\ifthenelse",
        r"\iftoggle",
        r"\ifundef",
    }
)
VERBATIM_ENV_RE = re.compile(
    r"\\begin\s*\{(?P<env>verbatim\*?|lstlisting|minted)\}.*?"
    r"\\end\s*\{(?P=env)\}",
    re.DOTALL,
)


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Validate LaTeX sections and citation keys.")
    parser.add_argument("--tex", required=True)
    parser.add_argument("--bib", required=True)
    return parser.parse_args()


def _strip_tex_comments(tex_text: str) -> str:
    cleaned_lines: list[str] = []
    for line in tex_text.splitlines():
        kept: list[str] = []
        index = 0
        while index < len(line):
            character = line[index]
            if line.startswith(r"\verb", index):
                delimiter_index = index + len(r"\verb")
                if delimiter_index < len(line) and line[delimiter_index] == "*":
                    delimiter_index += 1
                if (
                    delimiter_index < len(line)
                    and not line[delimiter_index].isspace()
                    and not line[delimiter_index].isalpha()
                ):
                    closing_index = line.find(line[delimiter_index], delimiter_index + 1)
                    if closing_index >= 0:
                        kept.append(line[index : closing_index + 1])
                        index = closing_index + 1
                        continue
            if character == "%" and not _is_escaped(line, index):
                break
            kept.append(character)
            index += 1
        cleaned_lines.append("".join(kept))
    return "\n".join(cleaned_lines)


def _is_escaped(text: str, index: int) -> bool:
    backslash_count = 0
    cursor = index - 1
    while cursor >= 0 and text[cursor] == "\\":
        backslash_count += 1
        cursor -= 1
    return backslash_count % 2 == 1


def _strip_false_conditionals(
    tex_text: str, conditional_openers: set[str] | None = None
) -> str:
    if conditional_openers is None:
        conditional_openers = set(CONDITIONAL_OPENERS)
        conditional_openers.update(
            match.group("name").lower() for match in NEWIF_RE.finditer(tex_text)
        )
    output: list[str] = []
    cursor = 0
    while (false_match := re.search(r"\\iffalse\b", tex_text[cursor:])) is not None:
        false_start = cursor + false_match.start()
        output.append(tex_text[cursor:false_start])
        depth = 1
        false_end = len(tex_text)
        else_start: int | None = None
        closing_start = len(tex_text)
        for token in CONDITIONAL_TOKEN_RE.finditer(tex_text, false_start + len(r"\iffalse")):
            token_text = token.group(0).lower()
            if token_text == r"\fi":
                depth -= 1
                if depth == 0:
                    closing_start = token.start()
                    false_end = token.end()
                    break
            elif token_text == r"\else":
                if depth == 1 and else_start is None:
                    else_start = token.end()
            elif _is_conditional_opener(token, token_text, conditional_openers):
                depth += 1
        if else_start is not None and closing_start < len(tex_text):
            output.append(
                _strip_false_conditionals(
                    tex_text[else_start:closing_start], conditional_openers
                )
            )
        cursor = false_end
    output.append(tex_text[cursor:])
    return "".join(output)


def _is_conditional_opener(
    token: re.Match[str],
    normalized_token: str,
    conditional_openers: set[str],
) -> bool:
    if normalized_token in conditional_openers:
        return True
    return token.group(0).startswith(r"\if") and (
        normalized_token not in NON_CONDITIONAL_IF_MACROS
    )


def _mask_tex_verbatim(tex_text: str) -> str:
    output: list[str] = []
    for line in tex_text.splitlines(keepends=True):
        characters = list(line)
        index = 0
        while index < len(line):
            if not line.startswith(r"\verb", index):
                index += 1
                continue
            delimiter_index = index + len(r"\verb")
            if delimiter_index < len(line) and line[delimiter_index] == "*":
                delimiter_index += 1
            if (
                delimiter_index >= len(line)
                or line[delimiter_index].isspace()
                or line[delimiter_index].isalpha()
            ):
                index += 1
                continue
            closing_index = line.find(line[delimiter_index], delimiter_index + 1)
            if closing_index < 0:
                index += 1
                continue
            characters[index : closing_index + 1] = " " * (closing_index + 1 - index)
            index = closing_index + 1
        output.append("".join(characters))
    inline_masked = "".join(output)
    return VERBATIM_ENV_RE.sub(lambda match: _mask_text(match.group(0)), inline_masked)


def _mask_text(text: str) -> str:
    return "".join("\n" if character == "\n" else " " for character in text)


def _visible_tex_text(tex_text: str) -> str:
    visible = re.sub(r"\\includegraphics(?:\[[^\]]*\])?\{[^}]+\}", " ", tex_text)
    visible = re.sub(
        r"\\(?:bibliography|bibliographystyle|label|ref|eqref|pageref|cite[a-zA-Z*]*)"
        r"\*?(?:\[[^\]]*\])?\{[^}]*\}",
        " ",
        visible,
    )
    visible = re.sub(r"\\(?:input|include)\{[^}]*\}", " ", visible)
    visible = re.sub(r"\\[a-zA-Z@]+\*?(?:\[[^\]]*\])?", " ", visible)
    visible = re.sub(r"[{}]", " ", visible)
    return re.sub(r"\s+", " ", visible).strip()


def validate_supplementary_information(tex_text: str) -> list[str]:
    uncommented_tex = _strip_tex_comments(tex_text)
    structural_tex = _strip_false_conditionals(_mask_tex_verbatim(uncommented_tex))
    heading_match = SUPPLEMENT_HEADING_RE.search(structural_tex)
    appendix_matches = list(re.finditer(r"\\appendix\b", structural_tex))
    appendix_before_heading = _last_match_before(appendix_matches, heading_match)
    appendix_match = appendix_before_heading or (appendix_matches[0] if appendix_matches else None)
    bibliography_matches = list(BIBLIOGRAPHY_RE.finditer(structural_tex))
    bibliography_match = bibliography_matches[-1] if bibliography_matches else None
    section_before_bibliography = _last_match_before(
        list(NEXT_SECTION_RE.finditer(structural_tex)), bibliography_match
    )
    errors: list[str] = []
    if appendix_match is None:
        errors.append(r"paper.tex missing \appendix for Supplementary Information")
    if heading_match is None:
        errors.append("paper.tex missing Supplementary Information heading")
        return errors
    if appendix_match is not None and appendix_match.start() > heading_match.start():
        errors.append(r"\appendix must appear before the Supplementary Information heading")
    if (
        bibliography_match is None
        or appendix_before_heading is None
        or bibliography_match.start() > appendix_before_heading.start()
    ):
        errors.append("Supplementary Information must appear after the bibliography")
    elif (
        section_before_bibliography is None
        or CLEARPAGE_RE.search(
            structural_tex, section_before_bibliography.end(), bibliography_match.start()
        )
        is None
    ):
        errors.append(r"paper.tex requires \clearpage before the bibliography")
    if (
        bibliography_match is not None
        and appendix_before_heading is not None
        and bibliography_match.start() < appendix_before_heading.start()
        and CLEARPAGE_RE.search(
            structural_tex, bibliography_match.end(), appendix_before_heading.start()
        )
        is None
    ):
        errors.append(
            r"Supplementary Information requires \clearpage between the bibliography and \appendix"
        )

    next_section = NEXT_SECTION_RE.search(structural_tex, heading_match.end())
    document_end = structural_tex.find(r"\end{document}", heading_match.end())
    end_candidates = [
        position
        for position in (document_end, next_section.start() if next_section else -1)
        if position >= 0
    ]
    supplement_end = min(end_candidates) if end_candidates else len(structural_tex)
    supplement_source = structural_tex[heading_match.end() : supplement_end]
    visible_content = _visible_tex_text(supplement_source)
    if any(placeholder in visible_content.lower() for placeholder in SUPPLEMENT_PLACEHOLDER_TEXTS):
        errors.append("Supplementary Information still contains template placeholder text")
    if len(visible_content) < 100:
        errors.append("Supplementary Information must contain at least 100 visible characters")
    if not SUBSTANTIVE_SUPPLEMENT_RE.search(supplement_source):
        errors.append(
            "Supplementary Information must contain at least one substantive subsection, table, or figure"
        )
    later_section_titles = _section_titles_after(structural_tex, heading_match.end())
    allowed_terminal_section = (
        len(later_section_titles) == 1
        and later_section_titles[0] in {"references", "bibliography"}
    )
    if later_section_titles and not allowed_terminal_section:
        errors.append("Supplementary Information must be the final top-level section")
    if r"\csname" in structural_tex[heading_match.end() :] or _has_section_alias(
        structural_tex
    ):
        errors.append("Supplementary Information must not use dynamic top-level sections")
    if TEX_IMPORT_RE.search(structural_tex, heading_match.end()):
        errors.append("Supplementary Information must not import unchecked TeX files")
    return errors


def _section_titles_after(tex_text: str, start: int) -> list[str | None]:
    titles: list[str | None] = []
    for match in NEXT_SECTION_RE.finditer(tex_text, start):
        title = _balanced_braced_content(tex_text, match.end() - 1)
        if title is None:
            titles.append(None)
            continue
        normalized_title = _visible_tex_text(title).strip().lower()
        titles.append(normalized_title or None)
    return titles


def _balanced_braced_content(tex_text: str, open_brace_index: int) -> str | None:
    span = _balanced_braced_span(tex_text, open_brace_index)
    return span[0] if span is not None else None


def _balanced_braced_span(tex_text: str, open_brace_index: int) -> tuple[str, int] | None:
    depth = 0
    for index in range(open_brace_index, len(tex_text)):
        character = tex_text[index]
        if character == "{" and not _is_escaped(tex_text, index):
            depth += 1
        elif character == "}" and not _is_escaped(tex_text, index):
            depth -= 1
            if depth == 0:
                return tex_text[open_brace_index + 1 : index], index + 1
    return None


def _has_section_alias(tex_text: str) -> bool:
    if LET_SECTION_ALIAS_RE.search(tex_text):
        return True
    for match in NEWCOMMAND_DEFINITION_RE.finditer(tex_text):
        name_span = _balanced_braced_span(tex_text, match.end() - 1)
        if name_span is None:
            continue
        cursor = name_span[1]
        while True:
            optional_argument = re.match(r"\s*\[[^\[\]]*\]", tex_text[cursor:])
            if optional_argument is None:
                break
            cursor += optional_argument.end()
        cursor += len(tex_text[cursor:]) - len(tex_text[cursor:].lstrip())
        if cursor >= len(tex_text) or tex_text[cursor] != "{":
            continue
        body_span = _balanced_braced_span(tex_text, cursor)
        if body_span is not None and re.search(r"\\section\b", body_span[0]):
            return True
    for match in DEF_DEFINITION_RE.finditer(tex_text):
        body_start = tex_text.find("{", match.end(), min(len(tex_text), match.end() + 500))
        if body_start < 0:
            continue
        body_span = _balanced_braced_span(tex_text, body_start)
        if body_span is not None and re.search(r"\\section\b", body_span[0]):
            return True
    return False


def _last_match_before(
    matches: list[re.Match[str]], boundary: re.Match[str] | None
) -> re.Match[str] | None:
    if boundary is None:
        return None
    preceding = [match for match in matches if match.start() < boundary.start()]
    return preceding[-1] if preceding else None


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    tex_text = Path(args.tex).read_text(encoding="utf-8")
    bib_text = Path(args.bib).read_text(encoding="utf-8")

    missing_sections = [
        name for name, pattern in SECTION_PATTERNS.items() if not pattern.search(tex_text)
    ]
    if missing_sections:
        raise SystemExit(f"Missing required sections: {missing_sections}")

    supplementary_errors = validate_supplementary_information(tex_text)
    if supplementary_errors:
        raise SystemExit(
            "Supplementary Information validation failed: " + "; ".join(supplementary_errors)
        )

    bib_keys = {
        match.group(1).strip() for match in KEY_RE.finditer(bib_text) if match.group(1).strip()
    }
    if not bib_keys:
        raise SystemExit("No citation keys found in bib file.")

    cited_keys: set[str] = set()
    for match in CITE_RE.finditer(tex_text):
        for part in match.group(1).split(","):
            key = part.strip()
            if key:
                cited_keys.add(key)
    unknown = sorted(key for key in cited_keys if key not in bib_keys)
    if unknown:
        raise SystemExit(f"Unknown citation keys in tex: {unknown}")

    print(f"Validated sections and {len(cited_keys)} citation key references.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
