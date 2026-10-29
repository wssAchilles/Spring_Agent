# Variant Selection: Review and Filter Proposals

You are reviewing proposed ablation and baseline variants for a scientific comparison study. Your job is to critically evaluate each proposal and decide which ones should be implemented.

## Task Description
{{TASK_MD}}

## Program Diff (best vs reference/seed)
```diff
{{PROGRAM_DIFF}}
```

## Proposed Candidates
```json
{{CANDIDATES_JSON}}
```

## Your Task

Evaluate each candidate and write `variant_selection.json` in the current working directory with your decisions.

## Output Schema

```json
{
  "decisions": [
    {
      "variant_id": "the_variant_id",
      "accepted": true or false,
      "reason": "Clear explanation of why this variant is accepted or rejected"
    }
  ]
}
```

## Acceptance Criteria (ALL must be met)

1. **Directly relevant** — The variant helps explain why the best method works or how it compares to alternatives
2. **Single-change scope** — Only one component/mechanism is modified
3. **Feasible in isolation** — A coding agent can implement it in one workspace without external resources
4. **Evaluable** — The existing evaluator can measure the variant's performance
5. **Paper-useful** — The comparison would be meaningful in a results table or ablation section

## Rejection Criteria (ANY triggers rejection)

1. **Quota filler** — The variant exists only to pad the comparison, not to test a specific hypothesis
2. **Tuning sweep** — The variant is just a hyperparameter change (e.g., learning rate 0.1 vs 0.01)
3. **Speculative improvement** — The variant tries to improve on the best method rather than understanding it
4. **Multi-change** — The variant modifies more than one independent component
5. **Generic baseline** — A standard method (e.g., "random baseline") with no task-specific rationale
6. **External dependency** — Requires new datasets, services, libraries, or large infrastructure changes
7. **Not an innovation** — (Ablations only) The ablated component already exists in the reference/seed program and is NOT an innovation introduced by the best program. Ablations must target components that are new in the best program relative to the initial provided program.

## Important

- **Zero accepted is valid.** If none of the proposals meet the criteria, reject all of them.
- Be rigorous. A smaller set of high-quality comparisons is better than many weak ones.
- Focus on variants that would survive peer review scrutiny.
