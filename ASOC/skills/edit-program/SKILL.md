---
name: edit-program
description: Edit a program for next experimentation.
---

# What the skill is about
Create the next program variant safely for iterative optimization experiments.

# When to use the skill
- After reviewing the latest evaluation results (or at the very beginning).
- When you want to test a new idea without overwriting previous attempts.

# What to do
1. Find the next unused program index `<idx>` from `{{RESULTS_DIR}}/program_<idx>`.
2. Clone the full previous program project (`program_<idx-1>`) into the new `program_<idx>` with the trusted clone utility:
   - `${TASK_PYTHON:-$WRAPPER_PYTHON} -I -m src.investigation.program_clone --from "{{RESULTS_DIR}}/program_<idx-1>" --to "{{RESULTS_DIR}}/program_<idx>"`
   - The utility creates the destination and applies the `program_spec.toml` copy rules without carrying evaluator artifacts.
   - For every source after `program_0`, the utility refuses to clone until canonical evaluation and mirrored rubric artifacts prove the previous iteration is complete.
   - Do not use `cp`, `cp -r`, `cp -a`, `rsync`, or any other recursive copy. If the trusted clone fails, stop the attempt and report the error; do not fall back to another clone method.
4. Edit code under `{{RESULTS_DIR}}/program_<idx>/src/**` (and update `program_spec.toml` if you change entrypoint or language).
5. Keep all older `program_<idx>` directories unchanged.
6. Never edit `{{TASK_DIR}}/evaluate.py` or `{{TASK_DIR}}/capture.py`.
7. Before creating `program_<idx>`, ensure previous evaluated `program_<idx-1>` already has:
- `metrics.json`
- `correct.json`
- `program_<idx-1>/rubric_status.json`
- `{{RESULTS_DIR}}/rubric_status.json` mirrored to that latest evaluated artifact
8. If latest rubric status has `overall_pass: true`, stop; do not create another program directory.
9. Single-attempt evaluation cap: never create/evaluate beyond `program_{{MAX_PROGRAM_EVALS_PER_ATTEMPT}}`.

# Example usage
```bash
${TASK_PYTHON:-$WRAPPER_PYTHON} -I -m src.investigation.program_clone --from "{{RESULTS_DIR}}/program_2" --to "{{RESULTS_DIR}}/program_3"
# edit {{RESULTS_DIR}}/program_3/src/** (and {{RESULTS_DIR}}/program_3/program_spec.toml if needed)
```
