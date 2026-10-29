---
name: write-paper-standalone
description: Write a grounded LaTeX paper as one coherent standalone scientific study centered on a single defensible thesis rather than the run chronology.
---

# What the skill is about
Generate `paper.tex` and `claims_grounding.json` from grounded artifacts only, with a clean standalone-paper narrative.

# When to use the skill
- Final paper step when the default paper-writing skill keeps drifting into a search diary.
- Need a standalone scientific paper built around one central method, hypothesis, or comparison story.
- Need to hide run order and expose only the subset of baselines and ablations that support a coherent grounded manuscript.

# What to do
1. Read:
- `results_readme.md` — campaign-level overview of all tasks, variants, and where to find results. Use this to understand the full scope of the campaign beyond the primary task passed via `--task-dir`.
- `paper_data.json`
- `references.bib`
- `references_provenance.json`
- `contributions.md` (if provided) — optional base for framing the claimed contributions; not a grounding source
- `global_memory_summary.md` (if provided)
- Aggregated CSVs in `{{PAPER_AGGREGATED_DIR}}/` for fine-grained analysis (install and use pandas) — when available
- generated figures in `figures/`
- when `paper_data.json.paper_profile == "standalone"`, also follow `presentation_contract` as a visible-language contract
2. Use `templates/{{PAPER_FORMAT}}/paper.tex` to keep section order stable. The paper format is **{{PAPER_FORMAT}}**.
Inspect the selected template and any venue guidance before drafting so the author block, section structure, and framing expectations match the chosen format.
3. Before drafting, choose one central thesis that is strongly supported by grounded evidence. Do not choose the paper story by recency alone.
4. Group related implementations into a small number of named method families. Keep only the baseline, proposed method, and ablations needed to support the central thesis.
5. If the artifacts do not support one coherent paper story, fail clearly instead of narrating the full search process.
6. Write required sections:
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
7. Generate comparative figures with Python + matplotlib by constructing analysis code yourself during the run:
- inline `python - <<'PY' ... PY`, or
- ad-hoc scripts in the paper workspace (for reproducibility).
8. Prefer comparison figures over chronology figures. Use progression-style plots only when they directly support the main scientific claim.
9. When multiple benchmarks, datasets, or settings are included, organize the visible paper around a shared hypothesis and condition-specific adaptations or limits, not around campaign chronology.
10. If `paper_strategy.json` includes `figure_specs`, implement those figures first and keep every generated figure within the declared readability budgets.
11. Use a logically consistent color scheme across all figures in the paper. Reuse the same colors for the same methods, baselines, conditions, or semantic roles whenever they recur, and avoid remapping meanings between figures without a clear reason.
12. When a `figure_specs[*]` entry sets `preferred_generator` to `generate-concept-figure`, your job is to wire it in, not to render it. Do not invoke `autoillustrate` or the `generate-concept-figure` pipeline. Reference `./figures/<figure_id>.png` in the manuscript, create a lightweight placeholder PNG and `{}` prompt-log stub so the draft compiles, and register `generator: generate-concept-figure` plus `prompt_log_path: figures/<figure_id>.prompts.json` in `figure_manifest.json`. The orchestrator replaces those placeholders synchronously after the draft stage. Any reusable placeholder script must skip generation when `.concept_figure_complete` exists so repair rounds cannot overwrite the rendered asset.
13. Do not satisfy a required overview-style `diagram` figure with a hand-built matplotlib/TikZ schematic unless the concept-figure workflow failed or was impossible; if that fallback happens, record the reason in `./memory.md`, `{{GLOBAL_MEMORY_MD_FILE}}`, and the matching `figure_manifest.json` entry.
14. Write `figure_manifest.json` for every figure referenced in `paper.tex`. Use `../validate-paper/templates/figure_manifest.schema.json` as the canonical contract. Legacy flat rows and richer nested rows are both acceptable if they validate against that schema.
15. Record generator provenance in `figure_manifest.json` when possible: `generator`, `prompt_log_path`, and `fallback_reason` when a preferred generator was skipped.
When evidence-audit feedback requires changing an orchestrator-owned concept figure, turn the correction into a complete documented fallback: export both `./figures/<figure_id>.png` and `./figures/<figure_id>.pdf`, set `generator` to the actual fallback generator (`matplotlib`, `tikz`, `native_latex`, or `manual`), set `attempted_generator: generate-concept-figure`, retain the original `prompt_log_path`, and set a non-empty `fallback_reason` describing why the orchestrator render was replaced. Use exactly those canonical artifact paths, and do not create or modify `./figures/<figure_id>.png.prov.json`; the orchestrator issues request-bound provenance after accepting the repair. Do not patch only the PNG while leaving `generator: generate-concept-figure`; that falsely claims ownership and allows figure preparation to restore the old render. Do not alter `paper_strategy.json` solely to bypass concept-figure orchestration, and do not create, delete, or modify `.concept_figure_complete`; the orchestrator owns that marker.
16. Before choosing figure widths, inspect the selected paper template or style files to understand the practical column width, full-width span, and text footprint for the current paper format.
17. Record final-layout hints in `figure_manifest.json` when possible: `placement`, `intended_latex_width_fraction`, and `estimated_min_rendered_font_pt`.
18. Keep figures readable by default: max 4 total panels, max 3 panels per row, max 6 categories per panel, max 4 series per panel, readable fonts, and abbreviated labels instead of tiny text unless the strategy explicitly allows a denser layout.
19. Choose each LaTeX figure width from the actual generated figure dimensions, aspect ratio, the inspected template geometry, and intended paper footprint. If a figure becomes unreadable at column width, regenerate/simplify it or place it full-width instead of shrinking the same dense figure further.
20. If `validation_feedback.json` or `validation_feedback.md` is present, treat it as a repair pass: fix every listed hard error first, rerun validation, and keep iterating until the hard errors are gone or the grounded inputs are insufficient.
21. Do not create visible section or subsection titles centered on `milestones`, `progression`, `timeline`, `phase`, `stage`, `round`, `Experiment A`, `Experiment B`, `first success`, or similar chronology-shaped labels unless the central claim is explicitly about dynamics.
22. Do not use phrases like `first valid result`, `subsequent improvements`, `next-best`, `later variants`, or `we then tried` in the visible paper unless that wording is strictly necessary inside a brief protocol description.
23. Keep visible paper text free of internal pipeline terms such as `eligible`, `correct and eligible`, `correctness rate`, `worker_01`, `worker_02`, `combined_score`, or `rubric`.
24. If you need a thresholded rate, name it with one reader-facing meaning per figure, such as target attainment rate, successful-run fraction, or non-collapse rate. Do not reuse one label for different task-specific thresholds.
25. When `paper_data.json.ablation_studies` is non-empty, prefer its variant summaries, replicate statistics, and explicit comparisons over inferring ablations from `all_programs` alone.
26. Use only numbers present in `paper_data.json`.
27. Use only citation keys present in `references.bib`.
28. Use `global_memory_summary.md` only as private qualitative framing; do not use it for quantitative claims or `source_ids`.
29. If `paper_strategy.md` and `paper_strategy.json` are present, treat them as the framing contract for the final draft; fail clearly if they contradict grounded evidence.
30. Keep visible paper text free of run-diary wording such as discovery order, iteration order, or agent-loop narration.
31. Write claim mapping JSON:
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
32. Run validator:
```bash
python scripts/validate_claims.py \
  --paper-data paper_data.json \
  --references references_provenance.json \
  --bib references.bib \
  --claims claims_grounding.json \
  --paper-dir .
```
This validator also checks that `references.bib` and `references_provenance.json` stay in sync, and traces every `@evidence[[...]]` link under `--paper-dir` against the `scientific-evidence/` bundle via the `scientific-evidence` skill verifier. Every link must classify as `resolved` or `anchored`; any `dangling` link is a hard error you must fix before finalization. If paper text carries `@evidence[[...]]` tokens but the bundle is missing, generate it first with `bash ../scientific-evidence/scripts/scientific_evidence.sh emit-bundle --root-dir .`.
33. During repair passes, also rerun the full `validate-paper` workflow after each fix round so the next round starts from fresh feedback, not stale assumptions.
34. When venue guidance is present, make the title, abstract, introduction, and conclusion explicitly reader-facing for that venue's scientific questions whenever the grounded evidence supports that framing.
35. If `./paper_authors.tex` exists, treat it as the canonical author source. Keep `\input{paper_authors.tex}` inside `\author{...}` when possible; otherwise copy the exact file contents without edits.
36. Keep the template's watermark block (`% --- watermark (auto-inserted) ---` … `% --- end watermark ---`) in `paper.tex` unchanged; the orchestrator regenerates it before every host compile.

# Outputs
- `paper.tex`
- `claims_grounding.json`
- `figure_manifest.json` when figures are generated or required by strategy
- Optional extra figures in `figures/` (grounded only, no cross-agent aggregate plots).
