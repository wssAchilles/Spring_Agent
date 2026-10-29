#!/usr/bin/env python3
"""Emit and verify the canonical scientific-evidence bundle and schemas."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, TypeAlias, cast

JsonScalar: TypeAlias = str | int | float | bool | None
JsonValue: TypeAlias = JsonScalar | list["JsonValue"] | dict[str, "JsonValue"]
JsonObject: TypeAlias = dict[str, JsonValue]

SPEC_TITLE = "scientific-evidence"
SPEC_ROOT_DIRNAME = "scientific-evidence"
SCHEMA_URI_PREFIX = "https://schemas.agentic-scientist.local/scientific-evidence"
EVIDENCE_REF_RE = re.compile(r"@evidence\[\[(?P<inner>[^\]]+)\]\]")
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
GENERATED_SCAN_ARTIFACT_NAMES = {
    "validation_feedback.json",
    "validation_feedback.md",
    "verification-report.json",
}
ROOT_GENERATED_SCAN_ARTIFACT_NAMES = {"validation_report.json"}
ROOT_ARCHIVE_DIR_PREFIXES = (("previous_paper",),)
GENERATED_SCAN_ARTIFACT_PREFIXES = (
    "paper_evidence_audit",
)
SKIP_DIR_NAMES = {
    ".git",
    ".venv",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    "__pycache__",
    SPEC_ROOT_DIRNAME,
}
AGENT_SKILL_DIR_PREFIXES = (
    ("skills",),
    ("prompts",),
    (".claude", "skills"),
    (".codex", "skills"),
    (".cursor", "skills"),
    (".opencode", "skills"),
    (".gemini", "skills"),
    (".pi", "agent", "skills"),
)
SCAN_EXTENSIONS = {
    ".md",
    ".txt",
    ".tex",
    ".py",
    ".sh",
    ".bash",
    ".zsh",
    ".r",
    ".R",
    ".yaml",
    ".yml",
    ".toml",
    ".json",
    ".jsonl",
    ".csv",
    ".tsv",
    ".js",
    ".ts",
    ".tsx",
    ".jsx",
    ".sql",
    ".ini",
    ".cfg",
    ".conf",
    ".rst",
}


@dataclass(frozen=True)
class SchemaFile:
    name: str
    relative_path: str
    title: str


@dataclass(frozen=True)
class ParsedEvidenceRef:
    raw: str
    target: str
    locator: str | None
    target_kind: str


@dataclass(frozen=True)
class Resolution:
    status: str
    resolved_path: str | None
    messages: tuple[str, ...]
    hash_checked: bool = False


SCHEMA_FILES: tuple[SchemaFile, ...] = (
    SchemaFile(
        name="evidence-reference",
        relative_path="schemas/evidence-reference.schema.json",
        title="Scientific Evidence Reference",
    ),
    SchemaFile(
        name="artifact-manifest",
        relative_path="schemas/artifact-manifest.schema.json",
        title="Scientific Evidence Artifact Manifest",
    ),
    SchemaFile(
        name="ledger-entry",
        relative_path="schemas/ledger-entry.schema.json",
        title="Scientific Evidence Ledger Entry",
    ),
    SchemaFile(
        name="verification-report",
        relative_path="schemas/verification-report.schema.json",
        title="Scientific Evidence Verification Report",
    ),
)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def schema_header(*, schema_name: str, title: str) -> JsonObject:
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": f"{SCHEMA_URI_PREFIX}/{schema_name}.schema.json",
        "title": title,
        "type": "object",
        "additionalProperties": False,
    }


def string_array_schema(*, min_items: int = 0) -> JsonObject:
    schema: JsonObject = {
        "type": "array",
        "items": {"type": "string", "minLength": 1},
    }
    if min_items > 0:
        schema["minItems"] = min_items
    return schema


def evidence_reference_schema() -> JsonObject:
    schema = schema_header(schema_name="evidence-reference", title=SCHEMA_FILES[0].title)
    schema["required"] = ["schema_name", "raw", "target", "target_kind"]
    schema["properties"] = {
        "schema_name": {"const": "scientific-evidence.evidence-reference"},
        "raw": {
            "type": "string",
            "pattern": r"^@evidence\[\[[^\]]+\]\]$",
            "description": "Raw inline evidence token as written in a text-like artifact.",
        },
        "target": {
            "type": "string",
            "minLength": 1,
            "description": "Target path, URI, or stable identifier before the optional #locator.",
        },
        "target_kind": {
            "type": "string",
            "enum": ["path", "absolute-path", "uri", "id", "unknown"],
        },
        "locator": {
            "type": "string",
            "minLength": 1,
            "description": "Opaque locator fragment after #. The base verifier does not need universal parsing.",
        },
        "normalized_target": {
            "type": "string",
            "minLength": 1,
            "description": "Optional normalized path, URI, or identifier used by the verifier.",
        },
        "notes": {"type": "string"},
    }
    return schema


def artifact_descriptor_schema() -> JsonObject:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["path", "kind"],
        "properties": {
            "id": {"type": "string", "minLength": 1},
            "path": {
                "type": "string",
                "minLength": 1,
                "description": "Path to the artifact relative to the scientific-evidence root when possible.",
            },
            "kind": {
                "type": "string",
                "enum": [
                    "source",
                    "derived",
                    "manuscript",
                    "figure",
                    "table",
                    "script",
                    "log",
                    "other",
                ],
            },
            "media_type": {"type": "string", "minLength": 1},
            "sha256": {
                "type": "string",
                "pattern": r"^[0-9a-f]{64}$",
                "description": "Optional hash recorded when the producing workflow computes one.",
            },
        },
    }


def generated_by_schema() -> JsonObject:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["script_path"],
        "properties": {
            "script_path": {"type": "string", "minLength": 1},
            "command": string_array_schema(min_items=1),
            "cwd": {"type": "string", "minLength": 1},
            "script_sha256": {"type": "string", "pattern": r"^[0-9a-f]{64}$"},
            "started_at": {"type": "string", "format": "date-time"},
            "finished_at": {"type": "string", "format": "date-time"},
        },
    }


def artifact_manifest_schema() -> JsonObject:
    schema = schema_header(schema_name="artifact-manifest", title=SCHEMA_FILES[1].title)
    schema["required"] = ["schema_name", "artifact", "derived_from"]
    schema["properties"] = {
        "schema_name": {"const": "scientific-evidence.artifact-manifest"},
        "artifact": artifact_descriptor_schema(),
        "derived_from": {
            "type": "array",
            "items": {"type": "string", "minLength": 1},
            "minItems": 1,
            "description": "Inline @evidence refs or stable ids describing the artifact inputs.",
        },
        "generated_by": generated_by_schema(),
        "named_outputs": {
            "type": "object",
            "description": "Optional machine-readable outputs emitted by the derivation.",
            "additionalProperties": {
                "type": ["string", "number", "integer", "boolean", "array", "object", "null"]
            },
        },
        "notes": {"type": "string"},
    }
    return schema


def subject_schema() -> JsonObject:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["path", "kind"],
        "properties": {
            "path": {"type": "string", "minLength": 1},
            "kind": {
                "type": "string",
                "enum": ["artifact", "claim", "manifest", "verification", "other"],
            },
            "locator": {"type": "string", "minLength": 1},
        },
    }


def ledger_entry_schema() -> JsonObject:
    schema = schema_header(schema_name="ledger-entry", title=SCHEMA_FILES[2].title)
    schema["required"] = [
        "schema_name",
        "entry_id",
        "entry_kind",
        "subject",
        "evidence_refs",
        "recorded_at",
    ]
    schema["properties"] = {
        "schema_name": {"const": "scientific-evidence.ledger-entry"},
        "entry_id": {"type": "string", "minLength": 1},
        "entry_kind": {
            "type": "string",
            "enum": ["artifact", "claim", "figure", "table", "verification", "note"],
        },
        "subject": subject_schema(),
        "evidence_refs": {
            "type": "array",
            "items": {"type": "string", "minLength": 1},
            "minItems": 1,
        },
        "recorded_at": {"type": "string", "format": "date-time"},
        "payload": {
            "type": "object",
            "description": "Open payload for workflow-specific metadata.",
            "additionalProperties": True,
        },
        "notes": {"type": "string"},
    }
    return schema


def verification_result_schema() -> JsonObject:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["subject_path", "subject_kind", "raw_reference", "target", "status", "messages"],
        "properties": {
            "subject_path": {"type": "string", "minLength": 1},
            "subject_kind": {
                "type": "string",
                "enum": ["text-artifact", "manifest", "ledger-entry", "other"],
            },
            "raw_reference": {"type": "string", "minLength": 1},
            "target": {"type": "string", "minLength": 1},
            "locator": {"type": "string", "minLength": 1},
            "status": {
                "type": "string",
                "enum": ["resolved", "anchored", "dangling"],
            },
            "resolved_path": {"type": "string", "minLength": 1},
            "hash_checked": {"type": "boolean"},
            "messages": string_array_schema(),
        },
    }


def verification_report_schema() -> JsonObject:
    schema = schema_header(schema_name="verification-report", title=SCHEMA_FILES[3].title)
    schema["required"] = ["schema_name", "verified_at", "root_dir", "summary", "results"]
    schema["properties"] = {
        "schema_name": {"const": "scientific-evidence.verification-report"},
        "verified_at": {"type": "string", "format": "date-time"},
        "root_dir": {"type": "string", "minLength": 1},
        "summary": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "total_references",
                "resolved_references",
                "anchored_references",
                "dangling_references",
                "manifests_checked",
            ],
            "properties": {
                "total_references": {"type": "integer", "minimum": 0},
                "resolved_references": {"type": "integer", "minimum": 0},
                "anchored_references": {"type": "integer", "minimum": 0},
                "dangling_references": {"type": "integer", "minimum": 0},
                "manifests_checked": {"type": "integer", "minimum": 0},
            },
        },
        "results": {
            "type": "array",
            "items": verification_result_schema(),
        },
        "notes": {"type": "string"},
    }
    return schema


def schema_builders() -> dict[str, JsonObject]:
    return {
        "evidence-reference": evidence_reference_schema(),
        "artifact-manifest": artifact_manifest_schema(),
        "ledger-entry": ledger_entry_schema(),
        "verification-report": verification_report_schema(),
    }


def spec_markdown() -> str:
    return "\n".join(
        [
            f"# {SPEC_TITLE}",
            "",
            "This is the canonical generated spec bundle for the `scientific-evidence` skill.",
            "Use the skill script as the source of truth for schemas, layout, and wording.",
            "",
            "## Scope",
            "",
            "This contract is generic and file-format-agnostic.",
            "It supports inline evidence refs in text-like files, adjacent `.prov.json` sidecars for generated artifacts, and a central JSONL ledger.",
            "",
            "## Why this exists",
            "",
            "The point of `scientific-evidence` is to make an agent leave a machine-checkable trail for important numbers, figures, tables, and derived outputs.",
            "A paper or report should not depend on free-form prose alone when those claims can point back to real artifacts.",
            "Always use this contract during paper writing whenever the agent adds manuscript numbers, table values, figure inputs, or derived statistics.",
            "",
            "## Fixed directory structure",
            "",
            "```text",
            "scientific-evidence/",
            "  spec.md",
            "  bundle.manifest.json",
            "  ledger.jsonl",
            "  schemas/",
            "    evidence-reference.schema.json",
            "    artifact-manifest.schema.json",
            "    ledger-entry.schema.json",
            "    verification-report.schema.json",
            "```",
            "",
            "Agents should use this layout exactly. Do not invent alternate bundle directories.",
            "",
            "## Grammar",
            "",
            "```text",
            "EvidenceRef   := '@evidence[[' Target [ '#' Locator ] ']]'",
            "Target        := non-empty path | absolute path | URI | stable identifier",
            "Locator       := opaque fragment without closing ']]'",
            "Sidecar       := '<artifact>.prov.json'",
            "Ledger        := 'scientific-evidence/ledger.jsonl'",
            "```",
            "",
            "### Notes",
            "",
            "- `Target` stays generic. The verifier may classify it as `path`, `absolute-path`, `uri`, `id`, or `unknown`.",
            "- `Locator` is opaque unless a verifier adapter knows how to interpret it.",
            "- Text-like artifacts should use inline `@evidence[[...]]` refs. Binary or generated outputs should carry an adjacent `.prov.json` sidecar.",
            "",
            "### Minimal examples",
            "",
            "- `@evidence[[TARGET]]`",
            "- `@evidence[[TARGET#LOCATOR]]`",
            "- `figure.png.prov.json`",
            "",
            "## Verification behavior",
            "",
            "A verifier uses this bundle to inspect whether the agent left a connected evidence trail.",
            "The base verifier uses three reference states:",
            "",
            "- `resolved`: the target exists and the verifier can validate the locator semantics it understands.",
            "- `anchored`: the target exists, but only path/hash checks were possible or the locator remained opaque.",
            "- `dangling`: the target does not resolve, the sidecar chain breaks, or the reference is malformed.",
            "",
            "### Minimum verifier expectations",
            "",
            "1. Resolve every inline evidence ref to a target path, URI, or id when possible.",
            "2. Mark each ref as `resolved`, `anchored`, or `dangling`.",
            "3. For manifests, require every `derived_from` edge to resolve to at least `anchored`.",
            "4. When hashes are recorded, compare them. Missing hashes are allowed.",
            "5. Do not pretend to interpret unknown locator grammars. Keep them `anchored` unless an adapter proves more.",
            "",
            "### What verification should prove",
            "",
            "At a minimum, a downstream verifier should be able to:",
            "",
            "- scan text-like artifacts for `@evidence[[...]]` refs",
            "- read `.prov.json` sidecars for generated artifacts",
            "- resolve refs against real files or stable ids",
            "- classify each link as `resolved`, `anchored`, or `dangling`",
            "- fail when a generated artifact has no input trail or when a claimed source does not exist",
            "",
            "This does not prove every scientific claim automatically. It proves whether the agent left enough structure for later auditing and stricter replay.",
            "A core paper-writing policy is: do not hardcode result numbers inside plotting or calculation scripts when those values should come from real artifacts. Read from files and leave matching `@evidence[[...]]` refs near the read or derivation logic.",
            "",
            "## Schema set",
            "",
            "The generated bundle includes:",
            "",
            *[f"- `{item.relative_path}`" for item in SCHEMA_FILES],
            "- `spec.md`",
            "- `bundle.manifest.json`",
            "- `ledger.jsonl`",
            "",
            "### Schema intent",
            "",
            "- `evidence-reference`: parsed form of one inline `@evidence[[...]]` token.",
            "- `artifact-manifest`: canonical sidecar schema for `<artifact>.prov.json`.",
            "- `ledger-entry`: one JSONL row in `scientific-evidence/ledger.jsonl`.",
            "- `verification-report`: verifier output with per-reference results and summary counts.",
            "",
            "## Examples across file formats",
            "",
            "### Python",
            "",
            "```python",
            "# Read the metric from disk; do not hardcode 96.7 here.",
            "# @evidence[[results/metrics.json#mnist.accuracy_mean]]",
            "import json",
            "payload = json.loads(open(\"results/metrics.json\", encoding=\"utf-8\").read())",
            "value = payload[\"mnist\"][\"accuracy_mean\"]",
            "```",
            "",
            "### Bash",
            "",
            "```bash",
            "# @evidence[[results/run_summary.csv#row(dataset=mnist,variant=proposed).accuracy]]",
            "python scripts/summarize_results.py --input results/run_summary.csv",
            "```",
            "",
            "### LaTeX",
            "",
            "```tex",
            "Accuracy reached 96.7\\%. % @evidence[[generated/stats.json#mnist.accuracy_mean]]",
            "```",
            "",
            "### Markdown",
            "",
            "```md",
            "Main result: 96.7%. <!-- @evidence[[generated/stats.json#mnist.accuracy_mean]] -->",
            "```",
            "",
            "### R",
            "",
            "```r",
            "# @evidence[[results/summary.tsv#humanoid.edppo.mean]]",
            "summary <- read.delim(\"results/summary.tsv\")",
            "value <- subset(summary, method == \"ED-PPO\" & env == \"Humanoid\")$mean[[1]]",
            "```",
            "",
            "### YAML",
            "",
            "```yaml",
            "# @evidence[[generated/stats.json#mnist.accuracy_mean]]",
            "reported_metric: mnist_accuracy_mean",
            "```",
            "",
            "### JSON sidecar",
            "",
            "```json",
            "{",
            "  \"schema_name\": \"scientific-evidence.artifact-manifest\",",
            "  \"artifact\": {\"path\": \"figures/main.png\", \"kind\": \"figure\"},",
            "  \"derived_from\": [",
            "    \"@evidence[[generated/stats.json#mnist.accuracy_mean]]\",",
            "    \"@evidence[[scripts/build_figure.py]]\"",
            "  ]",
            "}",
            "```",
            "",
            "## Conventions",
            "",
            "- Prefer paths relative to the scientific-evidence root when practical.",
            "- Put workflow-specific detail in `payload` or `named_outputs` rather than mutating the base schema.",
            "- Generate this bundle with the skill script instead of hand-writing downstream copies.",
            "",
            f"_Generated from {SPEC_TITLE}._",
        ]
    )


def bundle_manifest(bundle_root: Path) -> JsonObject:
    schema_paths: JsonObject = {item.name: item.relative_path for item in SCHEMA_FILES}
    return {
        "schema_name": "scientific-evidence.schema-bundle",
        "generated_at": utc_now(),
        "bundle_root": str(bundle_root),
        "spec_markdown": "spec.md",
        "ledger_path": "ledger.jsonl",
        "schemas": schema_paths,
        "verification_statuses": ["resolved", "anchored", "dangling"],
    }


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def write_json(path: Path, payload: JsonObject) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_json(path: Path) -> dict[str, Any] | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return payload if isinstance(payload, dict) else None


def sha256_hex(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def classify_target_kind(target: str) -> str:
    if target.startswith(("http://", "https://", "file://")):
        return "uri"
    if Path(target).is_absolute():
        return "absolute-path"
    if "/" in target or target.startswith("."):
        return "path"
    if ":" in target:
        return "id"
    return "unknown"


def parse_evidence_ref(raw: str) -> ParsedEvidenceRef | None:
    match = EVIDENCE_REF_RE.fullmatch(raw)
    if match is None:
        return None
    inner = match.group("inner")
    target, locator = inner.split("#", 1) if "#" in inner else (inner, None)
    compact_target = target.strip()
    compact_locator = locator.strip() if locator is not None else None
    if not compact_target:
        return None
    return ParsedEvidenceRef(
        raw=raw,
        target=compact_target,
        locator=compact_locator or None,
        target_kind=classify_target_kind(compact_target),
    )


def iter_evidence_refs(text: str) -> list[ParsedEvidenceRef]:
    refs: list[ParsedEvidenceRef] = []
    for match in EVIDENCE_REF_RE.finditer(text):
        parsed = parse_evidence_ref(match.group(0))
        if parsed is not None:
            refs.append(parsed)
    return refs


def relative_or_absolute(path: Path, root_dir: Path) -> str:
    try:
        return str(path.relative_to(root_dir))
    except ValueError:
        return str(path)


def is_within_workspace(path: Path, *, root_dir: Path) -> bool:
    try:
        path.resolve().relative_to(root_dir.resolve())
    except ValueError:
        return False
    return True


def unsafe_workspace_target_message(target: str) -> str | None:
    if target.lower().startswith("file://"):
        return "file:// evidence targets are not allowed; use a workspace-relative path or remote URI."
    if Path(target).is_absolute():
        return "Absolute evidence paths are not allowed; use a workspace-relative path."
    return None


def resolve_workspace_relative_path(raw_path: str, *, root_dir: Path) -> tuple[Path | None, str | None]:
    message = unsafe_workspace_target_message(raw_path)
    if message is not None:
        return None, message
    resolved = (root_dir / raw_path).resolve()
    if not is_within_workspace(resolved, root_dir=root_dir):
        return None, "Path resolves outside the paper workspace."
    return resolved, None


def looks_text_file(path: Path) -> bool:
    if path.suffix in SCAN_EXTENSIONS:
        return True
    if path.name in {"Dockerfile", "Makefile"}:
        return True
    return False


def load_ledger_index(bundle_root: Path) -> dict[str, str]:
    ledger_path = bundle_root / "ledger.jsonl"
    if not ledger_path.is_file():
        return {}
    index: dict[str, str] = {}
    for line_number, line in enumerate(ledger_path.read_text(encoding="utf-8").splitlines(), start=1):
        raw = line.strip()
        if not raw:
            continue
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(payload, dict):
            continue
        entry_id = payload.get("entry_id")
        if isinstance(entry_id, str) and entry_id.strip():
            index[entry_id] = f"{SPEC_ROOT_DIRNAME}/ledger.jsonl:{line_number}"
    return index


def build_result(
    *,
    subject_path: str,
    subject_kind: str,
    parsed: ParsedEvidenceRef,
    resolution: Resolution,
) -> JsonObject:
    payload: JsonObject = {
        "subject_path": subject_path,
        "subject_kind": subject_kind,
        "raw_reference": parsed.raw,
        "target": parsed.target,
        "status": resolution.status,
        "messages": list(resolution.messages),
    }
    if parsed.locator is not None:
        payload["locator"] = parsed.locator
    if resolution.resolved_path is not None:
        payload["resolved_path"] = resolution.resolved_path
    if resolution.hash_checked:
        payload["hash_checked"] = True
    return payload


def resolve_reference(
    *,
    parsed: ParsedEvidenceRef,
    subject_file: Path,
    root_dir: Path,
    bundle_root: Path,
    ledger_index: dict[str, str],
) -> Resolution:
    unsafe_message = unsafe_workspace_target_message(parsed.target)
    if unsafe_message is not None:
        return Resolution(status="dangling", resolved_path=None, messages=(unsafe_message,))

    if parsed.target_kind == "uri":
        return Resolution(
            status="anchored",
            resolved_path=parsed.target,
            messages=("URI target left anchored; the base verifier does not fetch remote resources.",),
        )

    ledger_path = ledger_index.get(parsed.target)
    if ledger_path is not None:
        status = "resolved" if parsed.locator is None else "anchored"
        message = "Resolved against scientific-evidence/ledger.jsonl entry id."
        if parsed.locator is not None:
            message = "Ledger id exists, but locator semantics were not interpreted by the base verifier."
        return Resolution(status=status, resolved_path=ledger_path, messages=(message,))

    candidate_paths: list[Path] = []
    escaped_workspace = False
    target_path = Path(parsed.target)
    for candidate in (
        (subject_file.parent / target_path).resolve(),
        (root_dir / target_path).resolve(),
        (bundle_root / target_path).resolve(),
    ):
        if is_within_workspace(candidate, root_dir=root_dir):
            candidate_paths.append(candidate)
        else:
            escaped_workspace = True

    seen: set[Path] = set()
    unique_candidates: list[Path] = []
    for candidate in candidate_paths:
        if candidate in seen:
            continue
        seen.add(candidate)
        unique_candidates.append(candidate)

    if not unique_candidates and escaped_workspace:
        return Resolution(
            status="dangling",
            resolved_path=None,
            messages=("Evidence target resolves outside the paper workspace.",),
        )

    for candidate in unique_candidates:
        if not candidate.exists():
            continue
        if parsed.locator is None:
            return Resolution(
                status="resolved",
                resolved_path=relative_or_absolute(candidate, root_dir),
                messages=("Target path exists.",),
            )
        return Resolution(
            status="anchored",
            resolved_path=relative_or_absolute(candidate, root_dir),
            messages=("Target path exists, but locator semantics were not interpreted by the base verifier.",),
        )

    return Resolution(
        status="dangling",
        resolved_path=None,
        messages=("Target did not resolve to a file path, URI, or known ledger id.",),
    )


def verify_hash(
    *,
    subject_path: str,
    subject_kind: str,
    raw_reference: str,
    target: str,
    actual_path: Path,
    expected_sha256: str,
    root_dir: Path,
) -> JsonObject:
    if not actual_path.exists():
        return {
            "subject_path": subject_path,
            "subject_kind": subject_kind,
            "raw_reference": raw_reference,
            "target": target,
            "status": "dangling",
            "resolved_path": relative_or_absolute(actual_path, root_dir),
            "hash_checked": True,
            "messages": ["Hash target does not exist."],
        }
    actual_sha = sha256_hex(actual_path)
    if actual_sha != expected_sha256:
        return {
            "subject_path": subject_path,
            "subject_kind": subject_kind,
            "raw_reference": raw_reference,
            "target": target,
            "status": "dangling",
            "resolved_path": relative_or_absolute(actual_path, root_dir),
            "hash_checked": True,
            "messages": [
                "Recorded sha256 does not match file contents.",
                f"expected={expected_sha256}",
                f"actual={actual_sha}",
            ],
        }
    return {
        "subject_path": subject_path,
        "subject_kind": subject_kind,
        "raw_reference": raw_reference,
        "target": target,
        "status": "resolved",
        "resolved_path": relative_or_absolute(actual_path, root_dir),
        "hash_checked": True,
        "messages": ["Recorded sha256 matches file contents."],
    }


def is_excluded_artifact_path(path: Path, *, root_dir: Path) -> bool:
    """Return True for operational artifacts that should not enter evidence scans."""
    try:
        rel_parts = path.relative_to(root_dir).parts
    except ValueError:
        rel_parts = path.parts
    if any(rel_parts[: len(prefix)] == prefix for prefix in AGENT_SKILL_DIR_PREFIXES):
        return True
    if any(rel_parts[: len(prefix)] == prefix for prefix in ROOT_ARCHIVE_DIR_PREFIXES):
        return True
    if any(part in SKIP_DIR_NAMES for part in rel_parts):
        return True
    if len(rel_parts) == 1 and rel_parts[0] in ROOT_GENERATED_SCAN_ARTIFACT_NAMES:
        return True
    if path.name in GENERATED_SCAN_ARTIFACT_NAMES:
        return True
    if any(path.name.startswith(prefix) for prefix in GENERATED_SCAN_ARTIFACT_PREFIXES):
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


def paper_tex_dependency_paths(root_dir: Path) -> set[Path]:
    """Return existing local files recursively imported by paper.tex."""
    root = root_dir.resolve()
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


def scan_text_artifacts(root_dir: Path) -> list[Path]:
    artifacts: list[Path] = []
    imported_paths = paper_tex_dependency_paths(root_dir)
    for path in root_dir.rglob("*"):
        if not path.is_file():
            continue
        if path not in imported_paths and is_excluded_artifact_path(path, root_dir=root_dir):
            continue
        if path.name.endswith(".prov.json"):
            continue
        if path in imported_paths or looks_text_file(path):
            artifacts.append(path)
    artifacts.sort()
    return artifacts


def scan_sidecars(root_dir: Path) -> list[Path]:
    sidecars: list[Path] = []
    for path in root_dir.rglob("*.prov.json"):
        if is_excluded_artifact_path(path, root_dir=root_dir):
            continue
        if path.is_file():
            sidecars.append(path)
    sidecars.sort()
    return sidecars


def verify_text_artifacts(
    *,
    root_dir: Path,
    bundle_root: Path,
    ledger_index: dict[str, str],
) -> list[JsonObject]:
    results: list[JsonObject] = []
    for path in scan_text_artifacts(root_dir):
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        refs = iter_evidence_refs(text)
        if not refs:
            continue
        subject_path = relative_or_absolute(path, root_dir)
        for parsed in refs:
            resolution = resolve_reference(
                parsed=parsed,
                subject_file=path,
                root_dir=root_dir,
                bundle_root=bundle_root,
                ledger_index=ledger_index,
            )
            results.append(
                build_result(
                    subject_path=subject_path,
                    subject_kind="text-artifact",
                    parsed=parsed,
                    resolution=resolution,
                )
            )
    return results


def verify_manifest_artifact(
    *,
    sidecar_path: Path,
    manifest: dict[str, Any],
    root_dir: Path,
) -> list[JsonObject]:
    results: list[JsonObject] = []
    artifact = manifest.get("artifact")
    subject_path = relative_or_absolute(sidecar_path, root_dir)
    if not isinstance(artifact, dict):
        results.append(
            {
                "subject_path": subject_path,
                "subject_kind": "manifest",
                "raw_reference": "<artifact>",
                "target": "<missing-artifact>",
                "status": "dangling",
                "messages": ["Manifest is missing object field `artifact`."],
            }
        )
        return results

    artifact_path_raw = artifact.get("path")
    if not isinstance(artifact_path_raw, str) or not artifact_path_raw.strip():
        results.append(
            {
                "subject_path": subject_path,
                "subject_kind": "manifest",
                "raw_reference": "<artifact.path>",
                "target": "<missing-artifact-path>",
                "status": "dangling",
                "messages": ["Manifest artifact.path must be a non-empty string."],
            }
        )
        return results

    artifact_path, unsafe_message = resolve_workspace_relative_path(artifact_path_raw, root_dir=root_dir)
    if unsafe_message is not None or artifact_path is None:
        results.append(
            {
                "subject_path": subject_path,
                "subject_kind": "manifest",
                "raw_reference": "<artifact.path>",
                "target": artifact_path_raw,
                "status": "dangling",
                "messages": [unsafe_message or "Manifest artifact.path is not a valid workspace path."],
            }
        )
        return results

    if not artifact_path.exists():
        results.append(
            {
                "subject_path": subject_path,
                "subject_kind": "manifest",
                "raw_reference": "<artifact.path>",
                "target": artifact_path_raw,
                "status": "dangling",
                "messages": ["Manifest artifact.path does not exist."],
            }
        )
    else:
        results.append(
            {
                "subject_path": subject_path,
                "subject_kind": "manifest",
                "raw_reference": "<artifact.path>",
                "target": artifact_path_raw,
                "status": "resolved",
                "resolved_path": relative_or_absolute(artifact_path, root_dir),
                "messages": ["Manifest artifact.path exists."],
            }
        )

    expected_sha = artifact.get("sha256")
    if isinstance(expected_sha, str) and expected_sha.strip():
        results.append(
            verify_hash(
                subject_path=subject_path,
                subject_kind="manifest",
                raw_reference="<artifact.sha256>",
                target=artifact_path_raw,
                actual_path=artifact_path,
                expected_sha256=expected_sha,
                root_dir=root_dir,
            )
        )

    return results


def verify_manifests(
    *,
    root_dir: Path,
    bundle_root: Path,
    ledger_index: dict[str, str],
) -> tuple[list[JsonObject], int]:
    results: list[JsonObject] = []
    sidecars = scan_sidecars(root_dir)
    for sidecar_path in sidecars:
        subject_path = relative_or_absolute(sidecar_path, root_dir)
        manifest = read_json(sidecar_path)
        if manifest is None:
            results.append(
                {
                    "subject_path": subject_path,
                    "subject_kind": "manifest",
                    "raw_reference": "<manifest>",
                    "target": subject_path,
                    "status": "dangling",
                    "messages": ["Manifest is missing, invalid JSON, or not a JSON object."],
                }
            )
            continue

        schema_name = manifest.get("schema_name")
        if schema_name != "scientific-evidence.artifact-manifest":
            results.append(
                {
                    "subject_path": subject_path,
                    "subject_kind": "manifest",
                    "raw_reference": "<schema_name>",
                    "target": str(schema_name),
                    "status": "dangling",
                    "messages": [
                        "Manifest schema_name must equal `scientific-evidence.artifact-manifest`."
                    ],
                }
            )

        results.extend(verify_manifest_artifact(sidecar_path=sidecar_path, manifest=manifest, root_dir=root_dir))

        derived_from = manifest.get("derived_from")
        if not isinstance(derived_from, list) or not derived_from:
            results.append(
                {
                    "subject_path": subject_path,
                    "subject_kind": "manifest",
                    "raw_reference": "<derived_from>",
                    "target": "<missing-derived-from>",
                    "status": "dangling",
                    "messages": ["Manifest must declare at least one `derived_from` evidence ref."],
                }
            )
            continue

        for item in derived_from:
            if not isinstance(item, str):
                results.append(
                    {
                        "subject_path": subject_path,
                        "subject_kind": "manifest",
                        "raw_reference": "<non-string-derived-from>",
                        "target": repr(item),
                        "status": "dangling",
                        "messages": ["Manifest `derived_from` entries must be strings."],
                    }
                )
                continue
            parsed = parse_evidence_ref(item)
            if parsed is None:
                results.append(
                    {
                        "subject_path": subject_path,
                        "subject_kind": "manifest",
                        "raw_reference": item,
                        "target": item,
                        "status": "dangling",
                        "messages": ["Manifest `derived_from` entry is not a valid @evidence[[...]] token."],
                    }
                )
                continue
            resolution = resolve_reference(
                parsed=parsed,
                subject_file=sidecar_path,
                root_dir=root_dir,
                bundle_root=bundle_root,
                ledger_index=ledger_index,
            )
            results.append(
                build_result(
                    subject_path=subject_path,
                    subject_kind="manifest",
                    parsed=parsed,
                    resolution=resolution,
                )
            )

        generated_by = manifest.get("generated_by")
        if isinstance(generated_by, dict):
            script_path_raw = generated_by.get("script_path")
            if isinstance(script_path_raw, str) and script_path_raw.strip():
                script_path, unsafe_message = resolve_workspace_relative_path(script_path_raw, root_dir=root_dir)
                if unsafe_message is not None or script_path is None:
                    results.append(
                        {
                            "subject_path": subject_path,
                            "subject_kind": "manifest",
                            "raw_reference": "<generated_by.script_path>",
                            "target": script_path_raw,
                            "status": "dangling",
                            "messages": [unsafe_message or "generated_by.script_path is not a valid workspace path."],
                        }
                    )
                    continue
                if script_path.exists():
                    results.append(
                        {
                            "subject_path": subject_path,
                            "subject_kind": "manifest",
                            "raw_reference": "<generated_by.script_path>",
                            "target": script_path_raw,
                            "status": "resolved",
                            "resolved_path": relative_or_absolute(script_path, root_dir),
                            "messages": ["generated_by.script_path exists."],
                        }
                    )
                else:
                    results.append(
                        {
                            "subject_path": subject_path,
                            "subject_kind": "manifest",
                            "raw_reference": "<generated_by.script_path>",
                            "target": script_path_raw,
                            "status": "dangling",
                            "messages": ["generated_by.script_path does not exist."],
                        }
                    )
                script_sha = generated_by.get("script_sha256")
                if isinstance(script_sha, str) and script_sha.strip():
                    results.append(
                        verify_hash(
                            subject_path=subject_path,
                            subject_kind="manifest",
                            raw_reference="<generated_by.script_sha256>",
                            target=script_path_raw,
                            actual_path=script_path,
                            expected_sha256=script_sha,
                            root_dir=root_dir,
                        )
                    )
    return results, len(sidecars)


def verify_ledger(
    *,
    bundle_root: Path,
    root_dir: Path,
    ledger_index: dict[str, str],
) -> list[JsonObject]:
    results: list[JsonObject] = []
    ledger_path = bundle_root / "ledger.jsonl"
    if not ledger_path.is_file():
        return results

    subject_path = relative_or_absolute(ledger_path, root_dir)
    for line_number, line in enumerate(ledger_path.read_text(encoding="utf-8").splitlines(), start=1):
        raw = line.strip()
        if not raw:
            continue
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            results.append(
                {
                    "subject_path": f"{subject_path}:{line_number}",
                    "subject_kind": "ledger-entry",
                    "raw_reference": "<line>",
                    "target": raw,
                    "status": "dangling",
                    "messages": ["Ledger line is not valid JSON."],
                }
            )
            continue
        if not isinstance(payload, dict):
            results.append(
                {
                    "subject_path": f"{subject_path}:{line_number}",
                    "subject_kind": "ledger-entry",
                    "raw_reference": "<line>",
                    "target": raw,
                    "status": "dangling",
                    "messages": ["Ledger line must decode to a JSON object."],
                }
            )
            continue

        refs = payload.get("evidence_refs")
        if not isinstance(refs, list) or not refs:
            results.append(
                {
                    "subject_path": f"{subject_path}:{line_number}",
                    "subject_kind": "ledger-entry",
                    "raw_reference": "<evidence_refs>",
                    "target": "<missing-evidence-refs>",
                    "status": "dangling",
                    "messages": ["Ledger entry must provide non-empty `evidence_refs`."],
                }
            )
            continue

        for item in refs:
            if not isinstance(item, str):
                results.append(
                    {
                        "subject_path": f"{subject_path}:{line_number}",
                        "subject_kind": "ledger-entry",
                        "raw_reference": "<non-string-evidence-ref>",
                        "target": repr(item),
                        "status": "dangling",
                        "messages": ["Ledger evidence_refs entries must be strings."],
                    }
                )
                continue
            parsed = parse_evidence_ref(item)
            if parsed is None:
                results.append(
                    {
                        "subject_path": f"{subject_path}:{line_number}",
                        "subject_kind": "ledger-entry",
                        "raw_reference": item,
                        "target": item,
                        "status": "dangling",
                        "messages": ["Ledger evidence_refs entry is not a valid @evidence[[...]] token."],
                    }
                )
                continue
            resolution = resolve_reference(
                parsed=parsed,
                subject_file=ledger_path,
                root_dir=root_dir,
                bundle_root=bundle_root,
                ledger_index=ledger_index,
            )
            results.append(
                build_result(
                    subject_path=f"{subject_path}:{line_number}",
                    subject_kind="ledger-entry",
                    parsed=parsed,
                    resolution=resolution,
                )
            )
    return results


def command_list_schemas(_args: argparse.Namespace) -> int:
    for item in SCHEMA_FILES:
        print(f"{item.name}\t{item.relative_path}")
    return 0


def command_print_spec(_args: argparse.Namespace) -> int:
    print(spec_markdown())
    return 0


def command_emit_schema(args: argparse.Namespace) -> int:
    builders = schema_builders()
    payload = builders[args.name]
    if args.output:
        write_json(Path(args.output), payload)
    else:
        print(json.dumps(payload, indent=2, sort_keys=True))
    return 0


def command_emit_bundle(args: argparse.Namespace) -> int:
    root_dir = Path(args.root_dir).expanduser().resolve()
    bundle_root = root_dir / SPEC_ROOT_DIRNAME
    bundle_root.mkdir(parents=True, exist_ok=True)

    spec_path = bundle_root / "spec.md"
    write_text(spec_path, spec_markdown() + "\n")

    builders = schema_builders()
    for item in SCHEMA_FILES:
        write_json(bundle_root / item.relative_path, builders[item.name])

    ledger_path = bundle_root / "ledger.jsonl"
    ledger_path.touch(exist_ok=True)

    manifest_path = bundle_root / "bundle.manifest.json"
    write_json(manifest_path, bundle_manifest(bundle_root))

    print(f"Wrote spec bundle to {bundle_root}")
    print("- spec.md")
    for item in SCHEMA_FILES:
        print(f"- {item.relative_path}")
    print("- bundle.manifest.json")
    print("- ledger.jsonl")
    return 0


def command_verify(args: argparse.Namespace) -> int:
    root_dir = Path(args.root_dir).expanduser().resolve()
    bundle_root = root_dir / SPEC_ROOT_DIRNAME
    if not bundle_root.is_dir():
        raise SystemExit(
            f"Missing required bundle directory: {bundle_root}. Run emit-bundle first."
        )

    ledger_index = load_ledger_index(bundle_root)
    results: list[JsonObject] = []
    results.extend(verify_text_artifacts(root_dir=root_dir, bundle_root=bundle_root, ledger_index=ledger_index))
    manifest_results, manifest_count = verify_manifests(
        root_dir=root_dir,
        bundle_root=bundle_root,
        ledger_index=ledger_index,
    )
    results.extend(manifest_results)
    results.extend(verify_ledger(bundle_root=bundle_root, root_dir=root_dir, ledger_index=ledger_index))

    dangling_count = sum(1 for item in results if item.get("status") == "dangling")
    summary: JsonObject = {
        "total_references": len(results),
        "resolved_references": sum(1 for item in results if item.get("status") == "resolved"),
        "anchored_references": sum(1 for item in results if item.get("status") == "anchored"),
        "dangling_references": dangling_count,
        "manifests_checked": manifest_count,
    }
    report: JsonObject = {
        "schema_name": "scientific-evidence.verification-report",
        "verified_at": utc_now(),
        "root_dir": str(root_dir),
        "summary": summary,
        "results": cast(JsonValue, results),
    }

    output_path = Path(args.output).expanduser().resolve() if args.output else bundle_root / "verification-report.json"
    write_json(output_path, report)

    print(f"Verification report written to {output_path}")
    print(
        "Summary: "
        f"total={summary['total_references']} "
        f"resolved={summary['resolved_references']} "
        f"anchored={summary['anchored_references']} "
        f"dangling={summary['dangling_references']} "
        f"manifests={summary['manifests_checked']}"
    )
    return 1 if dangling_count > 0 else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="scientific_evidence",
        description=(
            "Emit and verify the canonical scientific-evidence bundle. "
            "This is a skill script, not an installed CLI. Run it via bash or python."
        ),
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser(
        "list-schemas",
        help="List canonical schema names and their fixed relative paths inside scientific-evidence/.",
    ).set_defaults(func=command_list_schemas)

    subparsers.add_parser(
        "print-spec",
        help="Print the canonical markdown spec to stdout.",
    ).set_defaults(func=command_print_spec)

    emit_schema = subparsers.add_parser(
        "emit-schema",
        help="Write one canonical JSON Schema to stdout or --output.",
    )
    emit_schema.add_argument(
        "--name",
        required=True,
        choices=[item.name for item in SCHEMA_FILES],
        help="Schema name to emit.",
    )
    emit_schema.add_argument(
        "--output",
        help="Optional output path. Defaults to stdout.",
    )
    emit_schema.set_defaults(func=command_emit_schema)

    emit_bundle = subparsers.add_parser(
        "emit-bundle",
        help="Write the fixed scientific-evidence/ bundle under --root-dir.",
    )
    emit_bundle.add_argument(
        "--root-dir",
        default=".",
        help="Root directory that will receive the fixed scientific-evidence/ bundle. Defaults to the current directory.",
    )
    emit_bundle.set_defaults(func=command_emit_bundle)

    verify = subparsers.add_parser(
        "verify",
        help="Scan text artifacts, sidecars, and ledger refs under --root-dir and emit a verification report.",
    )
    verify.add_argument(
        "--root-dir",
        default=".",
        help="Workspace root containing scientific-evidence/ and the artifacts to inspect. Defaults to the current directory.",
    )
    verify.add_argument(
        "--output",
        help="Optional explicit output path for the verification report. Defaults to scientific-evidence/verification-report.json.",
    )
    verify.set_defaults(func=command_verify)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
