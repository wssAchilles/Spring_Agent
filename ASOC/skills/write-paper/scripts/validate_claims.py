#!/usr/bin/env python3
"""Validate generated claim-grounding artifacts before paper finalization."""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

KEY_RE = re.compile(r"@\w+\{([^,]+),")
EVIDENCE_REF_RE = re.compile(r"@evidence\[\[[^\]]+\]\]")
TEX_INPUT_RE = re.compile(
    r"\\(?:input|include)(?![A-Za-z@])\s*"
    r"(?:\{(?P<braced>[^{}]+)\}|(?P<unbraced>[^\s%{}]+))"
)
TEX_IMPORT_COMMAND_RE = re.compile(r"\\(?:input|include)(?![A-Za-z@])")
TEX_LOCAL_SUPPORT_RE = re.compile(
    r"\\(?P<command>usepackage|RequirePackage|documentclass|LoadClass)"
    r"(?![A-Za-z@])(?:\s*\[[^\]]*\])?\s*\{(?P<targets>[^{}]+)\}"
)
TEX_LOCAL_SUPPORT_COMMAND_RE = re.compile(
    r"\\(?:usepackage|RequirePackage|documentclass|LoadClass)(?![A-Za-z@])"
)
DYNAMIC_TEX_IMPORT_RE = re.compile(r"\\csname\s*(?:input|include)\s*\\endcsname")
CONDITIONAL_TOKEN_RE = re.compile(r"\\if[a-zA-Z@]+\b|\\else\b|\\fi\b")
VERBATIM_ENV_RE = re.compile(
    r"\\begin\s*\{(?P<env>verbatim\*?|lstlisting|minted)\}.*?"
    r"\\end\s*\{(?P=env)\}",
    re.DOTALL,
)
PROMPT_ARTIFACT_RE = re.compile(r".+_prompt(?:_round_\d+(?:_contract_retry_\d+)?)?\.md$")
GENERATED_EVIDENCE_SCAN_ARTIFACT_NAMES = {
    "validation_feedback.json",
    "validation_feedback.md",
    "verification-report.json",
}
ROOT_GENERATED_EVIDENCE_SCAN_ARTIFACT_NAMES = {"validation_report.json"}
GENERATED_EVIDENCE_SCAN_ARTIFACT_PREFIXES = (
    "paper_evidence_audit",
)
EVIDENCE_BUNDLE_DIRNAME = "scientific-evidence"
EVIDENCE_SCAN_SUFFIXES = (
    ".tex",
    ".md",
    ".json",
    ".yaml",
    ".yml",
    ".py",
    ".sh",
    ".r",
    ".csv",
    ".tsv",
)
EVIDENCE_SKIP_DIR_NAMES = {
    ".git",
    "__pycache__",
    EVIDENCE_BUNDLE_DIRNAME,
}
AGENT_SKILL_DIR_PREFIXES = (
    (".claude", "skills"),
    (".codex", "skills"),
    (".cursor", "skills"),
    (".opencode", "skills"),
    (".gemini", "skills"),
    (".pi", "agent", "skills"),
)
VERIFICATION_SUMMARY_KEYS = (
    "total_references",
    "resolved_references",
    "anchored_references",
    "dangling_references",
    "manifests_checked",
)
SCIENTIFIC_EVIDENCE_SCRIPT = (
    Path(__file__).resolve().parent.parent.parent
    / "scientific-evidence"
    / "scripts"
    / "scientific_evidence.py"
)


def read_json(path: Path) -> dict[str, Any]:
    """Read JSON data from disk when available."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON in {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise SystemExit(f"Expected JSON object in {path}")
    return payload


def read_bib_keys(path: Path) -> set[str]:
    """Read bib keys."""
    text = path.read_text(encoding="utf-8")
    return {match.group(1).strip() for match in KEY_RE.finditer(text) if match.group(1).strip()}


def validate_reference_provenance(*, references_payload: dict[str, Any], bib_keys: set[str]) -> tuple[set[str], list[str]]:
    """Validate references_provenance.json against references.bib."""
    errors: list[str] = []
    refs = references_payload.get("references")
    if not isinstance(refs, list):
        return set(), ["references_provenance.json must contain a `references` list."]

    provenance_keys: set[str] = set()
    provenance_ids: set[str] = set()
    for idx, row in enumerate(refs):
        if not isinstance(row, dict):
            errors.append(f"references[{idx}] must be object")
            continue
        source_id = row.get("id")
        if not isinstance(source_id, str) or not source_id.strip():
            errors.append(f"references[{idx}].id missing")
        else:
            if source_id in provenance_ids:
                errors.append(f"references[{idx}].id duplicates existing id: {source_id!r}")
            provenance_ids.add(source_id)

        citation_key = row.get("citation_key")
        if not isinstance(citation_key, str) or not citation_key.strip():
            errors.append(f"references[{idx}].citation_key missing")
            continue
        if citation_key in provenance_keys:
            errors.append(
                f"references[{idx}].citation_key duplicates existing key: {citation_key!r}"
            )
        provenance_keys.add(citation_key)

    missing_in_provenance = sorted(bib_keys - provenance_keys)
    if missing_in_provenance:
        errors.append(
            "references.bib keys missing from references_provenance.json: "
            + ", ".join(repr(key) for key in missing_in_provenance)
        )

    missing_in_bib = sorted(provenance_keys - bib_keys)
    if missing_in_bib:
        errors.append(
            "references_provenance.json keys missing from references.bib: "
            + ", ".join(repr(key) for key in missing_in_bib)
        )

    return provenance_ids, errors


def is_excluded_evidence_scan_path(path: Path, *, paper_dir: Path) -> bool:
    """Return True for operational artifacts that should not enter evidence scans."""
    rel_parts = path.relative_to(paper_dir).parts
    if any(rel_parts[: len(prefix)] == prefix for prefix in AGENT_SKILL_DIR_PREFIXES):
        return True
    if any(part in EVIDENCE_SKIP_DIR_NAMES for part in rel_parts):
        return True
    if len(rel_parts) == 1 and rel_parts[0] in ROOT_GENERATED_EVIDENCE_SCAN_ARTIFACT_NAMES:
        return True
    if path.name in GENERATED_EVIDENCE_SCAN_ARTIFACT_NAMES:
        return True
    if any(path.name.startswith(prefix) for prefix in GENERATED_EVIDENCE_SCAN_ARTIFACT_PREFIXES):
        return True
    return bool(PROMPT_ARTIFACT_RE.fullmatch(path.name))


def strip_tex_comments(tex_text: str) -> str:
    """Remove LaTeX comments while preserving escaped percent signs."""
    cleaned_lines: list[str] = []
    for line in tex_text.splitlines():
        comment_at = len(line)
        index = 0
        while index < len(line):
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
                        index = closing_index + 1
                        continue
            char = line[index]
            if char != "%":
                index += 1
                continue
            backslashes = 0
            cursor = index - 1
            while cursor >= 0 and line[cursor] == "\\":
                backslashes += 1
                cursor -= 1
            if backslashes % 2 == 0:
                comment_at = index
                break
            index += 1
        cleaned_lines.append(line[:comment_at])
    return "\n".join(cleaned_lines)


def strip_false_conditionals(tex_text: str) -> str:
    r"""Remove literal ``\iffalse ... \fi`` regions before dependency parsing."""
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
            else:
                depth += 1
        if else_start is not None and closing_start < len(tex_text):
            output.append(strip_false_conditionals(tex_text[else_start:closing_start]))
        cursor = false_end
    output.append(tex_text[cursor:])
    return "".join(output)


def mask_tex_verbatim(tex_text: str) -> str:
    """Mask inline and environment verbatim text while preserving line structure."""
    output: list[str] = []
    for line in tex_text.splitlines(keepends=True):
        chars = list(line)
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
            chars[index : closing_index + 1] = " " * (closing_index + 1 - index)
            index = closing_index + 1
        output.append("".join(chars))
    inline_masked = "".join(output)
    return VERBATIM_ENV_RE.sub(
        lambda match: "".join(
            "\n" if character == "\n" else " " for character in match.group(0)
        ),
        inline_masked,
    )


def paper_tex_dependency_paths(paper_dir: Path) -> set[Path]:
    """Return existing local files recursively imported by paper.tex."""
    root = paper_dir.resolve()
    pending = [root / "paper.tex"]
    visited: set[Path] = set()
    dependencies: set[Path] = set()
    while pending:
        source = pending.pop()
        if source in visited or not source.is_file():
            continue
        visited.add(source)
        try:
            text = strip_false_conditionals(
                mask_tex_verbatim(strip_tex_comments(source.read_text(encoding="utf-8")))
            )
        except (OSError, UnicodeDecodeError):
            continue
        if DYNAMIC_TEX_IMPORT_RE.search(text):
            raise RuntimeError(f"paper.tex dependency uses a dynamic import command: {source}")
        input_matches = list(TEX_INPUT_RE.finditer(text))
        parsed_import_starts = {match.start() for match in input_matches}
        if any(
            match.start() not in parsed_import_starts
            for match in TEX_IMPORT_COMMAND_RE.finditer(text)
        ):
            raise RuntimeError(f"paper.tex dependency uses a dynamic import command: {source}")
        for match in input_matches:
            target_text = (match.group("braced") or match.group("unbraced")).strip()
            if "\\" in target_text or "#" in target_text:
                raise RuntimeError(f"paper.tex dependency uses a dynamic import target: {source}")
            target = Path(target_text)
            candidates: list[Path] = []
            for base in (root, source.parent):
                candidate = Path(os.path.abspath(base / target))
                try:
                    relative_candidate = candidate.relative_to(root)
                except ValueError as exc:
                    raise RuntimeError(
                        f"paper.tex dependency escapes the paper workspace: {candidate}"
                    ) from exc
                if any(
                    relative_candidate.parts[: len(prefix)] == prefix
                    for prefix in AGENT_SKILL_DIR_PREFIXES
                ):
                    raise RuntimeError(f"paper.tex must not import agent skill files: {source}")
                candidates.append(candidate)
                if not candidate.suffix:
                    candidates.append(candidate.with_suffix(".tex"))
            for candidate in candidates:
                if not candidate.is_file():
                    continue
                resolved = candidate.resolve(strict=True)
                try:
                    resolved.relative_to(root)
                except ValueError as exc:
                    raise RuntimeError(
                        f"paper.tex dependency escapes the paper workspace: {candidate}"
                    ) from exc
                if resolved != candidate:
                    raise RuntimeError(f"paper.tex dependency must not use symlinks: {candidate}")
                dependencies.add(resolved)
                pending.append(resolved)
                break
        support_matches = list(TEX_LOCAL_SUPPORT_RE.finditer(text))
        parsed_support_starts = {match.start() for match in support_matches}
        if any(
            match.start() not in parsed_support_starts
            for match in TEX_LOCAL_SUPPORT_COMMAND_RE.finditer(text)
        ):
            raise RuntimeError(f"paper.tex dependency uses a dynamic support import: {source}")
        for match in support_matches:
            suffix = ".cls" if "class" in match.group("command").lower() else ".sty"
            for raw_target in match.group("targets").split(","):
                target_text = raw_target.strip()
                if "\\" in target_text or "#" in target_text:
                    raise RuntimeError(
                        f"paper.tex dependency uses a dynamic support import: {source}"
                    )
                target = Path(target_text)
                if not target.name:
                    continue
                if not target.suffix:
                    target = target.with_suffix(suffix)
                for base in (root, source.parent):
                    candidate = Path(os.path.abspath(base / target))
                    try:
                        relative_candidate = candidate.relative_to(root)
                    except ValueError as exc:
                        raise RuntimeError(
                            f"paper.tex dependency escapes the paper workspace: {candidate}"
                        ) from exc
                    if any(
                        relative_candidate.parts[: len(prefix)] == prefix
                        for prefix in AGENT_SKILL_DIR_PREFIXES
                    ):
                        raise RuntimeError(
                            f"paper.tex must not import agent skill files: {source}"
                        )
                    if not candidate.is_file():
                        continue
                    resolved = candidate.resolve(strict=True)
                    if resolved != candidate:
                        raise RuntimeError(
                            f"paper.tex dependency must not use symlinks: {candidate}"
                        )
                    dependencies.add(resolved)
                    pending.append(resolved)
                    break
    return dependencies


def scan_paper_dir_for_evidence_tokens(paper_dir: Path) -> list[tuple[Path, int, str]]:
    """Return every `@evidence[[...]]` occurrence found under paper_dir."""
    occurrences: list[tuple[Path, int, str]] = []
    imported_paths = paper_tex_dependency_paths(paper_dir)
    for path in sorted(paper_dir.rglob("*")):
        if not path.is_file():
            continue
        if path not in imported_paths and is_excluded_evidence_scan_path(
            path, paper_dir=paper_dir
        ):
            continue
        if path not in imported_paths and path.suffix.lower() not in EVIDENCE_SCAN_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for line_no, line in enumerate(text.splitlines(), start=1):
            for match in EVIDENCE_REF_RE.finditer(line):
                occurrences.append((path.relative_to(paper_dir), line_no, match.group(0)))
    return occurrences


def validate_evidence_bundle(paper_dir: Path) -> list[str]:
    """Delegate to the scientific-evidence verifier; return actionable hard errors.

    The bundle is opt-in: a missing bundle with zero `@evidence[[...]]` tokens in
    paper text is a silent pass. Any dangling ref, or a missing bundle when
    tokens exist, fails hard so the paper-writing agent can fix the links.
    """
    bundle_dir = paper_dir / EVIDENCE_BUNDLE_DIRNAME
    tokens = scan_paper_dir_for_evidence_tokens(paper_dir)

    if bundle_dir.is_symlink():
        return [f"scientific-evidence bundle must be a real directory: {bundle_dir}"]
    if not bundle_dir.is_dir():
        if not tokens:
            return []
        rel_path, line_no, token = tokens[0]
        return [
            (
                f"scientific-evidence/ bundle missing under {paper_dir} but paper text carries "
                f"{len(tokens)} `@evidence[[...]]` token(s); first occurrence: {rel_path}:{line_no} {token}. "
                "Generate the bundle with "
                "`scientific_evidence.sh emit-bundle --root-dir <paper_dir>` "
                "or remove the evidence tokens."
            )
        ]

    if not SCIENTIFIC_EVIDENCE_SCRIPT.is_file():
        return [f"scientific-evidence verifier script missing at {SCIENTIFIC_EVIDENCE_SCRIPT}"]

    verification_report_path = bundle_dir / "verification-report.json"
    payload, verification_error = run_evidence_verifier_atomically(
        paper_dir=paper_dir,
        bundle_dir=bundle_dir,
        verification_report_path=verification_report_path,
    )
    if verification_error is not None or payload is None:
        return [verification_error or "scientific-evidence verifier failed"]

    raw_results = payload.get("results")
    results: list[Any] = raw_results if isinstance(raw_results, list) else []
    dangling: list[dict[str, Any]] = [
        item for item in results if isinstance(item, dict) and item.get("status") == "dangling"
    ]
    if not dangling:
        return []

    preview_limit = 5
    parts = [f"scientific-evidence verifier found {len(dangling)} dangling `@evidence[[...]]` link(s)."]
    for item in dangling[:preview_limit]:
        subject = item.get("subject_path") or "<unknown-subject>"
        token = item.get("raw_reference") or item.get("target") or "<unknown-token>"
        raw_messages = item.get("messages")
        messages: list[Any] = raw_messages if isinstance(raw_messages, list) else []
        detail = "; ".join(str(message) for message in messages) or "dangling link"
        parts.append(f"{subject} {token} -> {detail}")
    if len(dangling) > preview_limit:
        parts.append(f"(+{len(dangling) - preview_limit} more; see {verification_report_path})")
    parts.append(
        "Fix or remove every dangling link, rerun "
        "`scientific_evidence.sh verify --root-dir <paper_dir>`, and repeat "
        "until zero dangling references remain."
    )
    return [" ".join(parts)]


def is_usable_verification_payload(
    payload: object, *, expected_root: Path
) -> bool:
    if not isinstance(payload, dict):
        return False
    if payload.get("schema_name") != "scientific-evidence.verification-report":
        return False
    if not all(isinstance(payload.get(key), str) and payload[key] for key in ("verified_at", "root_dir")):
        return False
    if Path(payload["root_dir"]).expanduser().resolve() != expected_root.resolve():
        return False
    summary = payload.get("summary")
    results = payload.get("results")
    if not isinstance(summary, dict) or not isinstance(results, list):
        return False
    if any(
        not isinstance(summary.get(key), int)
        or isinstance(summary.get(key), bool)
        or summary[key] < 0
        for key in VERIFICATION_SUMMARY_KEYS
    ):
        return False
    statuses: list[str] = []
    for result in results:
        if not isinstance(result, dict):
            return False
        required_strings = ("subject_path", "subject_kind", "raw_reference", "target", "status")
        if any(not isinstance(result.get(key), str) or not result[key] for key in required_strings):
            return False
        status_value = result["status"]
        if status_value not in {"resolved", "anchored", "dangling"}:
            return False
        if result["subject_kind"] not in {"text-artifact", "manifest", "ledger-entry", "other"}:
            return False
        if any(
            key in result and (not isinstance(result[key], str) or not result[key])
            for key in ("locator", "resolved_path")
        ):
            return False
        if "hash_checked" in result and not isinstance(result["hash_checked"], bool):
            return False
        messages = result.get("messages")
        if not isinstance(messages, list) or any(not isinstance(message, str) for message in messages):
            return False
        statuses.append(status_value)
    return (
        summary["total_references"] == len(results)
        and summary["resolved_references"] == statuses.count("resolved")
        and summary["anchored_references"] == statuses.count("anchored")
        and summary["dangling_references"] == statuses.count("dangling")
    )


def run_evidence_verifier_atomically(
    *, paper_dir: Path, bundle_dir: Path, verification_report_path: Path
) -> tuple[dict[str, Any] | None, str | None]:
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=".verification-report.", suffix=".json", dir=bundle_dir
    )
    os.close(descriptor)
    temporary_path = Path(temporary_name)
    try:
        proc = subprocess.run(
            [
                sys.executable,
                str(SCIENTIFIC_EVIDENCE_SCRIPT),
                "verify",
                "--root-dir",
                str(paper_dir),
                "--output",
                str(temporary_path),
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        stdout_tail = (proc.stdout or "")[-400:]
        stderr_tail = (proc.stderr or "")[-400:]
        if proc.returncode not in {0, 1}:
            return None, (
                "scientific-evidence verifier failed "
                f"(rc={proc.returncode}). stdout_tail={stdout_tail!r} stderr_tail={stderr_tail!r}"
            )
        try:
            temporary_stat = temporary_path.lstat()
            payload = json.loads(temporary_path.read_text(encoding="utf-8"))
        except (FileNotFoundError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            return None, f"scientific-evidence verifier produced an invalid report: {exc}"
        if not stat.S_ISREG(temporary_stat.st_mode) or temporary_stat.st_uid != os.getuid():
            return None, "scientific-evidence verifier produced an untrusted report file"
        if not is_usable_verification_payload(payload, expected_root=paper_dir):
            return None, "scientific-evidence verifier produced a schema-invalid report"
        summary = payload["summary"]
        assert isinstance(summary, dict)
        has_dangling = summary["dangling_references"] > 0
        if (proc.returncode == 1) != has_dangling:
            return None, (
                "scientific-evidence verifier exit status disagrees with "
                "dangling reference count"
            )
        os.replace(temporary_path, verification_report_path)
        return payload, None
    finally:
        temporary_path.unlink(missing_ok=True)



def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Validate claims grounding artifact.")
    parser.add_argument("--paper-data", required=True)
    parser.add_argument("--references", required=True, help="references_provenance.json")
    parser.add_argument("--bib", required=True, help="references.bib")
    parser.add_argument("--claims", required=True, help="claims_grounding.json")
    parser.add_argument(
        "--paper-dir",
        default=".",
        help=(
            "Paper workspace root used to trace every `@evidence[[...]]` link against the "
            "scientific-evidence/ bundle. Defaults to the current directory."
        ),
    )
    return parser.parse_args()


def main() -> int:
    """Run the command-line entry point."""
    args = parse_args()
    paper_data = read_json(Path(args.paper_data))
    references = read_json(Path(args.references))
    claims = read_json(Path(args.claims))
    bib_keys = read_bib_keys(Path(args.bib))
    if not bib_keys:
        raise SystemExit("No citation keys found in BibTeX file.")

    grounding_sources = paper_data.get("grounding_sources")
    if not isinstance(grounding_sources, dict):
        raise SystemExit("paper_data missing grounding_sources object.")
    allowed_sources = set(grounding_sources.keys())

    provenance_ids, reference_errors = validate_reference_provenance(
        references_payload=references,
        bib_keys=bib_keys,
    )
    allowed_sources.update(provenance_ids)

    claim_rows = claims.get("claims")
    if not isinstance(claim_rows, list) or not claim_rows:
        raise SystemExit("claims_grounding.json must contain non-empty claims list.")

    errors: list[str] = []
    for idx, row in enumerate(claim_rows):
        if not isinstance(row, dict):
            errors.append(f"claims[{idx}] must be object")
            continue
        claim_text = row.get("claim")
        if not isinstance(claim_text, str) or not claim_text.strip():
            errors.append(f"claims[{idx}].claim missing")

        source_ids = row.get("source_ids")
        if not isinstance(source_ids, list) or not source_ids:
            errors.append(f"claims[{idx}].source_ids missing")
        else:
            for source_id in source_ids:
                if not isinstance(source_id, str) or source_id not in allowed_sources:
                    errors.append(f"claims[{idx}] unknown source id: {source_id!r}")

        citation_keys = row.get("citation_keys", [])
        if not isinstance(citation_keys, list):
            errors.append(f"claims[{idx}].citation_keys must be list")
        else:
            for citation_key in citation_keys:
                if not isinstance(citation_key, str) or citation_key not in bib_keys:
                    errors.append(f"claims[{idx}] unknown citation key: {citation_key!r}")

    evidence_errors = validate_evidence_bundle(Path(args.paper_dir).expanduser().resolve())

    errors = [*reference_errors, *errors, *evidence_errors]
    if errors:
        raise SystemExit("Claims validation failed: " + "; ".join(errors))

    print(
        f"Validated {len(claim_rows)} grounded claims and {len(provenance_ids)} provenance references."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
