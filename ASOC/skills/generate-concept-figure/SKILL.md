---
name: generate-concept-figure
description: Generate one reader-facing conceptual overview figure for paper generation as an editable draw.io diagram via the pinned Autoillustrate npm release, with a deterministic layout and an optional image-model restyle.
---

# Who runs this (important)
During **paper generation the orchestrator owns this pipeline**: `src/scripts/paper.sh` and `paper_edit.sh` run the installed `autoillustrate` CLI synchronously as a post-draft step (`run_concept_figure_step`), derive every argument from `paper_strategy.json`, forward the selected outer agent, and promote the result to `./figures/<figure_id>.png`. The phase agents always run in a dedicated one-shot Docker container, even when the outer paper workflow is native; this limits them to the paper directory, selected-agent auth, and selected provider credentials. The paper-writing agent must **not** invoke this pipeline itself. The rest of this document describes the same CLI for standalone/manual use.

# What the skill is about
Generate one reader-facing conceptual overview figure for the paper when a high-level glance-value visual will clarify the method, comparison story, or evidence structure. The figure is produced by the Autoillustrate pipeline (the `autoillustrate` npm release pinned in the agent image): each panel is a collision-checked, deterministically laid-out draw.io diagram, the panels are composed into a single editable `.drawio` file, and the composite is optionally restyled into a camera-ready PNG. The deliverable is a real `.drawio` source plus its export — only the final restyle pass touches a generative model.

# When to use the skill
- Final paper generation.
- The paper needs an early conceptual figure that is easier to scan than a dense quantitative plot.
- The grounded artifacts support a clean overview figure without inventing unsupported mechanisms or claims.
- `paper_strategy.json` requests a required overview-style `diagram` figure or the paper prompt calls for an overview figure on page 1 or 2.
- The figure is a methods/architecture/training-loop schematic with 1–3 structurally distinct panels. (For pure quantitative plots use matplotlib/seaborn instead.)

# Prerequisites
The pipeline shells out to several tools. Confirm them before claiming a hard failure:
- **`drawio` desktop CLI** on `PATH` (headless export). Without it Phases 2/4 cannot export.
- **`autoillustrate` and `headless`** on `PATH`, plus the selected agent CLI (`claude`, `codex`, `cursor`, `gemini`, `opencode`, or `pi`).
- For environment-authenticated OpenCode phase agents, set `PAPER_CONCEPT_FIGURE_AGENT_PROVIDER` to exactly one of `openai`, `gemini`, or `anthropic`; only that provider credential is forwarded.
- **`OPENAI_API_KEY`** — the default VLM backend (`gpt-5.5`) for the Phase 3 per-panel inspection and the Phase 6 verification, and the image model (`gpt-image-2`) for the Phase 5 restyle. Without it those VLM calls and the restyle cannot run; you can still ship the deterministic Phase 4 export with `--skip-enhance`.
- **`GEMINI_API_KEY`** — optional fallback. When OpenAI is unavailable, the paper orchestrator selects `nanobanana-pro`, `gemini-3.1-pro`, and `gemini-single` for enhancement and audits.
- **A Python venv** with the toolkit deps. The container image bakes one and exposes its interpreter as `$VENV_PY`.

Before deciding a key is missing, check the active environment directly. OpenAI is the default; Gemini is the automatic fallback:
```bash
python - <<'PY'
import os
for k in ("OPENAI_API_KEY", "GEMINI_API_KEY"):
    print(f"{k}: {'available' if os.getenv(k) else 'missing'}")
PY
```
Do not claim a key is unavailable when the rendered paper prompt says it is expected to be available unless your direct check still shows it missing. Without an enhancement key, `--skip-enhance` still ships the deterministic Phase 4 export.

# What to do
1. Infer the central mechanism, comparison, or evidence story from grounded artifacts only. Decide whether one cohesive panel suffices or the content naturally splits into 2–3 structurally independent panels (e.g. *architecture · update rule · training loop*). Prefer one overview figure, not a full illustration suite.

2. Confirm the installed CLI and selected agent are available:
```bash
command -v autoillustrate
autoillustrate doctor --agent "${AGENT:-claude}"
```

3. In the project container, use the baked `$VENV_PY`. Native installations may let Autoillustrate create its cached venv or set `VENV_PY` explicitly.

4. Run the pipeline against the manuscript. Resolve the strategy's exact figure id, pass `--output ./figures/<figure_id>.png`, forward the selected `--agent`, and provide a concise grounded prompt. Add `--skip-enhance` when no enhancement backend is available.

**This pipeline can take ~1h** (it dispatches several selected-agent jobs and may run a VLM restyle). Run it **synchronously and block on it** — the caller must own the pipeline's full lifetime. Do **not** launch it as a background task that outlives the caller's turn (`run_in_background: true`, a trailing `&`, or a fire-and-forget agent step): when that turn ends the pipeline is orphaned and reaped mid-run, leaving the figure unrendered. In `paper.sh` and `paper_edit.sh` this is exactly what `run_concept_figure_step` does — a single synchronous invocation in the agent container, wrapped with `timeout` or Homebrew's `gtimeout` so a genuine hang is bounded. Native systems without either command skip this non-gating render step instead of launching an unbounded process. The pipeline writes `./figures/<figure_id>.png` (and `<figure_id>.prompts.json`) only on success; confirm those files exist before treating the figure as done.
```bash
autoillustrate ./paper.tex \
  --output ./figures/<figure_id>.png \
  --agent "${AGENT:-claude}" \
  --prompt "Methods diagram that will be the main figure of this scientific research paper. <one or two sentences grounding the panels in the actual method>"
```
The orchestrator creates `runs/<YYYY-MM-DD-HHMMSS>/` in the working directory and writes the deliverables to `runs/<ts>/figures/`:
- `full_figure.drawio` — editable draw.io source
- `full_figure.png` — deterministic export (no model involved; always safe)
- `full_figure_enhanced.png` — publication-grade restyle (only when Phase 5 ran)
- `figure.json` — the validated 1–3 panel plan that drove the figure

With `--output` set, the pipeline also copies the best available figure (the enhanced export when Phase 5 ran, otherwise the deterministic export) to `./figures/<figure_id>.png` and drops the plan beside it as `./figures/<figure_id>.prompts.json`. The pipeline does not emit a PDF; the editable `full_figure.drawio` stays in the run workdir.

5. Keep the visual style scientific and simple: clean diagrammatic composition, exact labels, no decorative clip-art, no unsupported architecture details, datasets, or numeric claims. When the paper contains multiple figures, keep colors logically consistent — reuse the same color for the same method/baseline/condition across figures.

6. Confirm the promoted deliverable. With `--output` (step 4) the pipeline already wrote `./figures/<figure_id>.png` and `./figures/<figure_id>.prompts.json` — verify both exist. If you ran without `--output`, promote manually from the run workdir (enhanced export preferred, deterministic export as fallback):
```bash
RUN_FIGS="$(ls -td runs/*/figures | head -1)"
SRC_PNG="$RUN_FIGS/full_figure_enhanced.png"; [[ -f "$SRC_PNG" ]] || SRC_PNG="$RUN_FIGS/full_figure.png"
cp "$SRC_PNG" "./figures/<figure_id>.png"
cp "$RUN_FIGS/figure.json" "./figures/<figure_id>.prompts.json"   # the prompt/plan log
```
The editable `full_figure.drawio` stays in the run workdir so the figure can be re-edited later. The pipeline produces no PDF; the manifest does not require one for this figure.

7. When the strategy or prompt asks for a required overview/`diagram` figure, do not silently replace this workflow with a hand-drawn or purely matplotlib/TikZ schematic. Use this skill first unless it is impossible.

8. If a prerequisite is missing (for example, no `drawio` CLI) or the pipeline fails, log an explicit skip/failure reason in both `./memory.md` and `./global_memory/memory.md` before falling back to another figure implementation. Apply the same fallback logging rule if the generated figure is too poor to use.

9. If you fall back to a non-drawio overview figure, record that fallback reason in the matching `figure_manifest.json` note so the run leaves a clear breadcrumb.

10. If the final figure is used in `paper.tex`, add a matching `figure_manifest.json` entry that records `generator: generate-concept-figure`, `prompt_log_path` (the copied `./figures/<figure_id>.prompts.json`), and any `fallback_reason`. If the figure is not used, do not force it into the manuscript.

# Outputs
- `./figures/<figure_id>.png` (promoted from `full_figure_enhanced.png`, or `full_figure.png` when enhancement was skipped)
- `./figures/<figure_id>.prompts.json` (the run's `figure.json` plan, used as `prompt_log_path`)
- Editable source preserved at `runs/<ts>/figures/full_figure.drawio`
- Legacy explicit-path form remains valid: `./figures/concept_figure.png` and `./figures/concept_figure.prompts.json`
