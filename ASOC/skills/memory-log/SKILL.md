---
name: memory-log
description: Maintain a continuous, append-only `./memory.md` log of discoveries, evaluations, failures, and next hypotheses during agent runs.
---

# What the skill is about
Keep durable run memory so idea evolution and program evidence are never lost between steps.

# When to use the skill
- At run start to initialize memory.
- After every meaningful step (`idea`, `edit`, `evaluate`, `verify`, `paper`, `draft`).
- Immediately after every `evaluate-program -> verify-rubrics` pair.

# What to do
1. Initialize `./memory.md` if missing with these section headers:
- `# Memory Log`
- `## Context`
- `## Idea Discoveries`
- `## Program Evaluations`
- `## Failures / Regressions`
- `## Next Hypotheses`
2. Also initialize global files if missing:
- `{{GLOBAL_MEMORY_MD_FILE}}`
- `{{GLOBAL_MEMORY_JSONL_FILE}}`
3. Never delete or rewrite prior entries; append only.
4. For each appended entry, include:
- timestamp
- step type
- change/discovery summary
- concrete evidence
- next action
5. After every `evaluate-program -> verify-rubrics` pair, append a `Program Evaluations` entry that includes:
- `program_<idx>`
- `combined_score`
- `correct`
- rubric pass/fail summary
- `next_recommendation`
6. For regressions or failed rubrics, append one root-cause hypothesis under `Failures / Regressions`.
7. Before exit, append final status (`rubric_pass`, `cap_reached`, or task-specific stop reason) and next hypothesis under `Next Hypotheses`.
8. When command execution is unavailable, use read/edit/write tools to append directly
to the required markdown and JSONL files. Preserve all existing content. When command
execution is available, prefer the helper for deterministic dual-write:
- script: detect the active agent config directory first, then use `<that-dir>/skills/memory-log/scripts/append_memory.py`
- command template:
```bash
MEMORY_HELPER="$(find ./.claude ./.codex ./.cursor ./.opencode ./.gemini ./.pi -path '*/skills/memory-log/scripts/append_memory.py' -print -quit 2>/dev/null)"
python "${MEMORY_HELPER}" \
  --global-memory-md "{{GLOBAL_MEMORY_MD_FILE}}" \
  --global-memory-jsonl "{{GLOBAL_MEMORY_JSONL_FILE}}" \
  --event-json '{"step":"evaluate","scope":"attempt","summary":"...","evidence":"...","next_action":"...","attempt_id":"{{ATTEMPT_ID}}","idea_id":"{{IDEA_ID}}","program_id":"program_2","metrics":{"combined_score":2.54,"correct":true}}'
```
9. JSONL event schema (required keys):
- `timestamp` (auto-filled if omitted)
- `step` (`idea|edit|evaluate|verify|paper|draft|final|conclusions_skip`)
- `scope` (`idea|attempt|paper|campaign`)
- `summary`
- `evidence`
- `next_action`
- optional: `idea_id`, `attempt_id`, `program_id`, `metrics`, `rubrics`, `tags`

10. Update distilled conclusions (`conclusions.md`).
**Purpose.** `conclusions.md` is a *forward-looking guide* for the next worker (or your future self in a later cycle). Its job is to help readers (a) focus their effort on directions you've found promising, and (b) avoid spending compute on directions you've already shown to be unpromising. Each sentence should either point at a lead worth pursuing or warn against a dead end, framed so a researcher who has not read your `memory.md` can still act on it. Avoid chronology, restate-of-results, and post-mortem narration — those belong in `memory.md`. Conclusions are the synthesis layer that makes other workers' next cycles more efficient.

**When.** After every memory-append in the cycle (idea / edit / evaluate / verify / paper / final), evaluate whether `conclusions.md` should be updated.

**Update gate (the only valid triggers).**
- (a) cycle **contradicts** an existing sentence → rewrite that sentence.
- (b) cycle opens a **new category** not yet covered → add a sentence.
- (c) cycle **flips a Do/Don't** recommendation → flip it.
- (d) cycle **meaningfully refines** an existing sentence — narrows the conditions under which a rule holds, broadens its applicability to a config it wasn't known to apply to, sharpens a tentative claim into a confident one, or adds a qualifier the prior phrasing didn't cover → edit the sentence to incorporate the refinement. Simple re-confirmation in the same regime is **not** sufficient; you must identify the refinement dimension (narrow / broaden / sharpen / qualify) in `--gate-reason`.
- otherwise → **keep**, do not invoke the helper.

**Format constraints.** Single paragraph. ≤300 words. No blank lines (`\n\n`). Avoid detailed numerical figures — use qualitative thresholds (e.g. "low LR", "small batch") instead of precise floats like `LR=0.205`.

**Update mechanism.** When execution is unavailable, apply the same validation rules
and update the conclusions files directly with read/edit/write tools. When execution
is available, use this helper invocation:
```bash
python "${MEMORY_HELPER}" --write-conclusions \
  --conclusions-md "{{CONCLUSIONS_MD_FILE}}" \
  --conclusions-history-jsonl "{{CONCLUSIONS_HISTORY_FILE}}" \
  --cycle-index {{CYCLE_INDEX}} \
  --worker-index {{WORKER_INDEX}} \
  --gate-trigger <a|b|c|d> \
  --gate-reason "<one sentence; for (d) name the dimension>" \
  --conclusions-text "<the full new paragraph>"
```

**Pre-flight self-check.** Before invoking the helper, count words (`len(paragraph.split())`) and confirm ≤300, and that the paragraph contains no blank line. Most validation rejections can be avoided here.

**Retry budget — exactly 3 attempts per cycle.** On non-zero exit, read the stderr error, shorten the paragraph (drop the least-decisive sentence, merge two related sentences, or remove qualifiers), and retry. After **3 total attempts** with no success, the update is **discarded** for this cycle: do not retry further, do not write the rejected text anywhere, and append a single memory event with `step="conclusions_skip"` whose `summary` names the last validation error. Underlying findings are not lost — they are already durable in `memory.md`.

**Always log the gate decision.** Inside the regular memory append for the cycle, include `gate=<updated|kept|skipped>; trigger=<a|b|c|d|none>; reason=<one sentence>` in the entry's `summary` or `tags`. Use `skipped` only after the 3-attempt budget is exhausted.

# Example evaluation entry
```markdown
### 2026-02-13T16:58:00Z | step=evaluate
- program: program_3
- combined_score: 2.54
- correct: true
- rubrics: uses_restarts=pass, constraint_optimization=pass, score_threshold=fail
- evidence: late-stage overlap still visible in stdout trace
- next_recommendation: raise late-stage overlap penalty and diversify restart seeds
```
