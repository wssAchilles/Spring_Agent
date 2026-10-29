---
name: create-idea
description: Create one next-step idea bundle and matching rubric bundle for iterative optimization. Use during idea generation runs (for example via scripts/idea.sh) when `idea_bundle.json` and `target_rubrics.json` must be produced.
---

# What the skill is about
Produce one concrete, testable next experiment idea with machine-readable rubrics.


# Output creation
Write both required JSON files directly with the available file-writing tools.
Do not depend on command execution. When command execution is explicitly available,
this optional helper can create starter files:
```bash
# the script path is relative to the skill file
python ./scripts/create_idea_placeholder.py <working_dir> [--cycle-index N]
```
- `<working_dir>`: the agent's current working directory (idea output directory).
- `--cycle-index N`: cycle index for the `idea_id` prefix (falls back to `CYCLE_INDEX` env var, then defaults to 1).

The optional script creates `idea_bundle.json` and `target_rubrics.json` with TODO
placeholders for every required field.  Replace each TODO value with the
actual content.


# What to do
1. Read task context:
- `{{TASK_MD_PATH}}`
- `{{TASK_GOAL}}`
- `{{TASK_RUBRICS}}`
2. Use cycle and history context:
- `cycle_index={{CYCLE_INDEX}}`
- recent ideas and attempts from the prompt context
- best-known manifest from the prompt context
3. Write exactly these files directly (or use the optional placeholder helper when execution is allowed):
- `{{IDEA_BUNDLE_FILE}}`
- `{{TARGET_RUBRICS_FILE}}`
4. `{{IDEA_BUNDLE_FILE}}` must be a JSON object with keys:
- `idea_id` (string)
- `title` (string)
- `hypothesis` (string)
- `implementation_plan` (non-empty array of short strings)
- `target_rubrics` (non-empty array of `{id, description, type}`)
- `notes_for_agent` (string)
5. Rubric rules:
- include at least one `metric` rubric
- include at least one `implementation` rubric
- use stable snake_case rubric ids
- keep rubric descriptions concrete and measurable
6. Ensure `{{TARGET_RUBRICS_FILE}}` is exactly equal to `idea_bundle.json.target_rubrics`.
7. Apply the active harness rules exactly:

{{HARNESS_RUBRIC_REQUIREMENTS}}

When a rubric includes `verification`, `verification` must be a JSON object, never a prose string.
For harness implementation rubrics, use exactly this shape:

```json
{
  "verification": {
    "type": "source_contains",
    "patterns": ["specific executable snippet"]
  }
}
```

8. Before finishing, reread both files and confirm every implementation rubric satisfies the active harness rules.
9. Do not modify files outside the required output files.
