---
name: write-paper
description: Write a concise grounded LaTeX paper from evaluation artifacts with strict section structure and claim-to-source mapping.
---

# What the skill is about
Generate `paper.tex` and `claims_grounding.json` from grounded artifacts only.

# When to use the skill
- Final experiment summary step.
- Need concise publication-style output with reproducible claims.

# What to do
1. Read:
- `results_readme.md` — campaign-level overview of all tasks, variants, and where to find results. Use this to understand the full scope of the campaign beyond the primary task passed via `--task-dir`.
- `paper_data.json` — pre-computed per-variant statistics and grounding sources
- `references.bib`
- `references_provenance.json`
- `contributions.md` (if provided) — optional base for framing the claimed contributions; not a grounding source
- `global_memory_summary.md` (if provided)
- Aggregated CSVs in `{{PAPER_AGGREGATED_DIR}}/` for fine-grained analysis (install and use pandas) — when available
- generated figures in `figures/`
2. Use `templates/{{PAPER_FORMAT}}/paper.tex` to keep section order stable. The paper format is **{{PAPER_FORMAT}}**.

# Template Formats
Templates are organized under `templates/<format>/`. Use the one matching the requested paper format.
- Use the selected format's `paper.tex` plus any accompanying style files in the same template directory.
- Inspect the selected template and style files before drafting so section order, author block, bibliography style, and page-footprint assumptions match the chosen format.
- When `VENUE.md` is present, treat it as the source of venue-specific framing and submission expectations.
3. Write required sections:
- abstract
- introduction
- related work
- method
- results
- conclusion
- references
The Supplementary Information section is mandatory.
Embed it in the same `paper.tex`, after the references, in this order: `\clearpage`, bibliography, `\clearpage`, `\appendix`, and `\section{Supplementary Information}`.
Replace the template scaffold with substantive grounded content, including at least one meaningful subsection, table, or figure supported by the available artifacts. Suitable material includes extended methods, reproducibility details, additional ablations, robustness or failure analyses, and extra grounded tables or figures. Never move evidence essential to the central claim out of the main paper.
Route `appendix`-ranked posthoc analyses to Supplementary Information by default. Do not promote or drop them merely because of the main-body page budget.
4. Generate comparative figures with Python + matplotlib by constructing analysis code yourself during the run:
- inline `python - <<'PY' ... PY`, or
- ad-hoc scripts in the paper workspace (for reproducibility).
5. Prefer at least two additional comparative plots when data supports it.
6. Include relevant comparison figures in Results; generate progression views only when they add grounded analytical value.
7. Use a logically consistent color scheme across all figures in the paper. Reuse the same colors for the same methods, baselines, conditions, or semantic roles whenever they recur, and avoid remapping meanings between figures without a clear reason.
8. Write `figure_manifest.json` for every figure referenced in `paper.tex`. Use `../validate-paper/templates/figure_manifest.schema.json` as the canonical contract. Legacy flat rows and richer nested rows are both acceptable if they validate against that schema.
9. Before choosing figure widths, inspect the selected paper template or style files to understand the practical column width, full-width span, and text footprint for the current paper format.
10. Record final-layout hints in `figure_manifest.json` when possible: `placement`, `intended_latex_width_fraction`, and `estimated_min_rendered_font_pt`.
11. When a `paper_strategy.json` figure spec sets `preferred_generator` to `generate-concept-figure`, your job is to wire it in, not to render it. Do not invoke `autoillustrate` or the `generate-concept-figure` pipeline. Reference `./figures/<figure_id>.png` in the manuscript, create a lightweight placeholder PNG and `{}` prompt-log stub so the draft compiles, and register `generator: generate-concept-figure` plus `prompt_log_path: figures/<figure_id>.prompts.json` in `figure_manifest.json`. The orchestrator replaces those placeholders synchronously after the draft stage. Any reusable placeholder script must skip generation when `.concept_figure_complete` exists so repair rounds cannot overwrite the rendered asset.
12. Do not satisfy a required overview-style `diagram` figure with a hand-built matplotlib/TikZ schematic unless the concept-figure workflow failed or was impossible; if that fallback happens, record the reason in `./memory.md`, `{{GLOBAL_MEMORY_MD_FILE}}`, and the matching `figure_manifest.json` entry.
13. Record generator provenance in `figure_manifest.json` when possible: `generator`, `prompt_log_path`, and `fallback_reason` when a preferred generator was skipped.
When evidence-audit feedback requires changing an orchestrator-owned concept figure, turn the correction into a complete documented fallback: export both `./figures/<figure_id>.png` and `./figures/<figure_id>.pdf`, set `generator` to the actual fallback generator (`matplotlib`, `tikz`, `native_latex`, or `manual`), set `attempted_generator: generate-concept-figure`, retain the original `prompt_log_path`, and set a non-empty `fallback_reason` describing why the orchestrator render was replaced. Use exactly those canonical artifact paths, and do not create or modify `./figures/<figure_id>.png.prov.json`; the orchestrator issues request-bound provenance after accepting the repair. Do not patch only the PNG while leaving `generator: generate-concept-figure`; that falsely claims ownership and allows figure preparation to restore the old render. Do not alter `paper_strategy.json` solely to bypass concept-figure orchestration, and do not create, delete, or modify `.concept_figure_complete`; the orchestrator owns that marker.
14. Choose each LaTeX figure width from the actual generated figure dimensions, aspect ratio, the inspected template geometry, and the intended paper footprint. If a figure becomes unreadable at column width, regenerate/simplify it or place it full-width instead of shrinking the same dense figure further.
15. If `validation_feedback.json` or `validation_feedback.md` is present, treat it as a repair pass: fix every listed hard error first, rerun validation, and keep iterating until the hard errors are gone or the grounded inputs are insufficient.
16. Compare methods/ideas using grounded data:
- `ablation_studies` in `paper_data.json` for pre-computed variant summaries, replicate statistics, and comparisons
- `variant_metadata` (when present) for variant titles, kinds, and hypotheses
- Aggregated CSVs for detailed per-replication analysis and training curves
- Variant source code (via `./results/variants/<variant_id>/task_bundle/program_*/src/` when present, or via program `program_spec.toml` / legacy `program.py`)
17. When `paper_data.json.ablation_studies` is non-empty, prefer its variant summaries, replicate statistics, and explicit comparisons over inferring ablations from `all_programs` alone.
18. Use only numbers present in `paper_data.json`.
19. Use only citation keys present in `references.bib`.
20. Use `global_memory_summary.md` only for qualitative framing (motivation, pivots, failure narrative); do not use it for quantitative claims or `source_ids`.
21. Write claim mapping JSON:
```json
{
  "claims": [
    {
      "claim": "text",
      "source_ids": ["result.best_correct_score"],
      "citation_keys": ["SomeKey2024"]
    }
  ]
}
```
22. Run validator:
```bash
python scripts/validate_claims.py \
  --paper-data paper_data.json \
  --references references_provenance.json \
  --bib references.bib \
  --claims claims_grounding.json \
  --paper-dir .
```
This validator also checks that `references.bib` and `references_provenance.json` stay in sync, and traces every `@evidence[[...]]` link under `--paper-dir` against the `scientific-evidence/` bundle via the `scientific-evidence` skill verifier. Every link must classify as `resolved` or `anchored`; any `dangling` link is a hard error you must fix before finalization. If paper text carries `@evidence[[...]]` tokens but the bundle is missing, generate it first with `bash ../scientific-evidence/scripts/scientific_evidence.sh emit-bundle --root-dir .`.
23. During repair passes, also rerun the full `validate-paper` workflow after each fix round so the next round starts from fresh feedback, not stale assumptions.
24. Preserve the author names and affiliation block required by the selected template and venue guidance; do not replace them with anonymous or generic placeholder metadata when the chosen format or venue requires real author information.
25. If `./paper_authors.tex` exists, treat it as the canonical author source. Keep `\input{paper_authors.tex}` inside `\author{...}` when possible; otherwise copy the exact file contents without edits.
26. Keep the template's watermark block (`% --- watermark (auto-inserted) ---` … `% --- end watermark ---`) in `paper.tex` unchanged; the orchestrator regenerates it before every host compile.

# Outputs
- `paper.tex`
- `claims_grounding.json`
- `figure_manifest.json` when figures are generated
- Optional extra figures in `figures/` (grounded only, no cross-agent aggregate plots).
