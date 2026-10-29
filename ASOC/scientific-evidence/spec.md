# scientific-evidence

This is the canonical generated spec bundle for the `scientific-evidence` skill.
Use the skill script as the source of truth for schemas, layout, and wording.

## Scope

This contract is generic and file-format-agnostic.
It supports inline evidence refs in text-like files, adjacent `.prov.json` sidecars for generated artifacts, and a central JSONL ledger.

## Why this exists

The point of `scientific-evidence` is to make an agent leave a machine-checkable trail for important numbers, figures, tables, and derived outputs.
A paper or report should not depend on free-form prose alone when those claims can point back to real artifacts.
Always use this contract during paper writing whenever the agent adds manuscript numbers, table values, figure inputs, or derived statistics.

## Fixed directory structure

```text
scientific-evidence/
  spec.md
  bundle.manifest.json
  ledger.jsonl
  schemas/
    evidence-reference.schema.json
    artifact-manifest.schema.json
    ledger-entry.schema.json
    verification-report.schema.json
```

Agents should use this layout exactly. Do not invent alternate bundle directories.

## Grammar

```text
EvidenceRef   := '@evidence[[' Target [ '#' Locator ] ']]'
Target        := non-empty path | absolute path | URI | stable identifier
Locator       := opaque fragment without closing ']]'
Sidecar       := '<artifact>.prov.json'
Ledger        := 'scientific-evidence/ledger.jsonl'
```

### Notes

- `Target` stays generic. The verifier may classify it as `path`, `absolute-path`, `uri`, `id`, or `unknown`.
- `Locator` is opaque unless a verifier adapter knows how to interpret it.
- Text-like artifacts should use inline `@evidence[[...]]` refs. Binary or generated outputs should carry an adjacent `.prov.json` sidecar.

### Minimal examples

- `@evidence[[TARGET]]`
- `@evidence[[TARGET#LOCATOR]]`
- `figure.png.prov.json`

## Verification behavior

A verifier uses this bundle to inspect whether the agent left a connected evidence trail.
The base verifier uses three reference states:

- `resolved`: the target exists and the verifier can validate the locator semantics it understands.
- `anchored`: the target exists, but only path/hash checks were possible or the locator remained opaque.
- `dangling`: the target does not resolve, the sidecar chain breaks, or the reference is malformed.

### Minimum verifier expectations

1. Resolve every inline evidence ref to a target path, URI, or id when possible.
2. Mark each ref as `resolved`, `anchored`, or `dangling`.
3. For manifests, require every `derived_from` edge to resolve to at least `anchored`.
4. When hashes are recorded, compare them. Missing hashes are allowed.
5. Do not pretend to interpret unknown locator grammars. Keep them `anchored` unless an adapter proves more.

### What verification should prove

At a minimum, a downstream verifier should be able to:

- scan text-like artifacts for `@evidence[[...]]` refs
- read `.prov.json` sidecars for generated artifacts
- resolve refs against real files or stable ids
- classify each link as `resolved`, `anchored`, or `dangling`
- fail when a generated artifact has no input trail or when a claimed source does not exist

This does not prove every scientific claim automatically. It proves whether the agent left enough structure for later auditing and stricter replay.
A core paper-writing policy is: do not hardcode result numbers inside plotting or calculation scripts when those values should come from real artifacts. Read from files and leave matching `@evidence[[...]]` refs near the read or derivation logic.

## Schema set

The generated bundle includes:

- `schemas/evidence-reference.schema.json`
- `schemas/artifact-manifest.schema.json`
- `schemas/ledger-entry.schema.json`
- `schemas/verification-report.schema.json`
- `spec.md`
- `bundle.manifest.json`
- `ledger.jsonl`

### Schema intent

- `evidence-reference`: parsed form of one inline `@evidence[[...]]` token.
- `artifact-manifest`: canonical sidecar schema for `<artifact>.prov.json`.
- `ledger-entry`: one JSONL row in `scientific-evidence/ledger.jsonl`.
- `verification-report`: verifier output with per-reference results and summary counts.

## Examples across file formats

### Python

```python
# Read the metric from disk; do not hardcode 96.7 here.
# @evidence[[results/metrics.json#mnist.accuracy_mean]]
import json
payload = json.loads(open("results/metrics.json", encoding="utf-8").read())
value = payload["mnist"]["accuracy_mean"]
```

### Bash

```bash
# @evidence[[results/run_summary.csv#row(dataset=mnist,variant=proposed).accuracy]]
python scripts/summarize_results.py --input results/run_summary.csv
```

### LaTeX

```tex
Accuracy reached 96.7\%. % @evidence[[generated/stats.json#mnist.accuracy_mean]]
```

### Markdown

```md
Main result: 96.7%. <!-- @evidence[[generated/stats.json#mnist.accuracy_mean]] -->
```

### R

```r
# @evidence[[results/summary.tsv#humanoid.edppo.mean]]
summary <- read.delim("results/summary.tsv")
value <- subset(summary, method == "ED-PPO" & env == "Humanoid")$mean[[1]]
```

### YAML

```yaml
# @evidence[[generated/stats.json#mnist.accuracy_mean]]
reported_metric: mnist_accuracy_mean
```

### JSON sidecar

```json
{
  "schema_name": "scientific-evidence.artifact-manifest",
  "artifact": {"path": "figures/main.png", "kind": "figure"},
  "derived_from": [
    "@evidence[[generated/stats.json#mnist.accuracy_mean]]",
    "@evidence[[scripts/build_figure.py]]"
  ]
}
```

## Conventions

- Prefer paths relative to the scientific-evidence root when practical.
- Put workflow-specific detail in `payload` or `named_outputs` rather than mutating the base schema.
- Generate this bundle with the skill script instead of hand-writing downstream copies.

_Generated from scientific-evidence._
