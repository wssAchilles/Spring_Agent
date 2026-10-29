---
name: verify-rubrics
description: Verify whether a program fulfills a set of rubrics.
---

# What the skill is about
Check whether the current candidate satisfies task rubrics, not only raw score.

# When to use the skill
- After running `evaluate-program`.
- Before concluding an iteration is successful.

# Placeholder usage
In host-driven verification, the harness pre-creates the program-level
`rubric_status.json`. Preserve that scaffold and fill only each rubric's
`pass` and `evidence` fields plus `next_recommendation`; the host computes
identity, metrics, aggregate `overall_pass`, and the workspace mirror.

In a standalone flow where no scaffold exists, resolve the directory containing
this `SKILL.md` and run its `scripts/create_rubric_status_placeholder.py` helper
with `<working_dir> --program-index IDX --variant-id ID`. Do not assume the
helper is under the current working directory.
- `<working_dir>`: the agent's current working directory (idea directory).
- `--program-index IDX`: when provided, also creates `program_<IDX>/rubric_status.json`.
- `--attempt-id` / `--idea-id`: pre-fill identifiers (fall back to `active_idea.json` and `ATTEMPT_ID` / `IDEA_ID` env vars).

The script writes a placeholder with `pass: false` and empty `evidence` for
every rubric.  Fill in the actual pass/fail status and evidence after
evaluating the program.

# What to do
1. Reuse the host scaffold, or create it with the skill-relative helper only when absent.
2. Verify implementation evidence in `{{RESULTS_DIR}}/program_<idx>/src/**` (and `program_spec.toml` when relevant).
3. Verify metric and correctness evidence in:
- `{{RESULTS_DIR}}/program_<idx>/metrics.json`
- `{{RESULTS_DIR}}/program_<idx>/correct.json`
4. Evaluate every checklist item with PASS/FAIL plus one evidence line.
5. In host-driven verification, update only `pass`, `evidence`, and
`next_recommendation` in the existing
`{{RESULTS_DIR}}/program_<idx>/rubric_status.json`; do not write the root mirror
or change host-owned top-level fields. In standalone verification, write the
complete status to both the program artifact and
`{{RESULTS_DIR}}/rubric_status.json` latest pointer with fields:
- `variant_id`
- `attempt_id` (from `{{ATTEMPT_ID}}`)
- `idea_id` (from `{{IDEA_ID}}`)
- `overall_pass` (true iff all rubric items pass)
- `rubrics` (array with `id`, `pass`, `evidence`)
- `metrics` (include `combined_score`, `correct`)
- `next_recommendation`
6. For each failed rubric, propose one concrete next code change.
7. If `overall_pass` is `true`, stop the attempt immediately; do not edit/evaluate further programs.
8. Single-attempt evaluation cap: never continue past `program_{{MAX_PROGRAM_EVALS_PER_ATTEMPT}}`.


# Example output
```text
Rubric check: program_{{MAX_PROGRAM_EVALS_PER_ATTEMPT}}
Parsed rubrics from active_rubrics.json:
- uses_restarts: Uses reheating and restarts
- constraint_optimization: Uses a type of constraint optimization
- score_threshold: Sum of radii for 26 circles exceeds 2.6

Checklist results:
- uses_restarts: PASS (reheat schedule + multi-restart loop present)
- constraint_optimization: PASS (penalty objective enforces non-overlap)
- score_threshold: FAIL (combined_score=2.54)

Standalone `program_{{MAX_PROGRAM_EVALS_PER_ATTEMPT}}/rubric_status.json` example:
{"variant_id":"example_variant","attempt_id":"{{ATTEMPT_ID}}","idea_id":"{{IDEA_ID}}","overall_pass":false,"rubrics":[{"id":"uses_restarts","pass":true,"evidence":"..."},{"id":"constraint_optimization","pass":true,"evidence":"..."},{"id":"score_threshold","pass":false,"evidence":"combined_score=2.54"}],"metrics":{"combined_score":2.54,"correct":true},"next_recommendation":"increase late-stage overlap penalty and add restart diversity"}
Standalone `./rubric_status.json`: same JSON as `program_{{MAX_PROGRAM_EVALS_PER_ATTEMPT}}/rubric_status.json` (latest evaluated program). In host-driven verification, the host creates this mirror instead.
Next edit: increase late-stage overlap penalty and add restart diversity.
```
