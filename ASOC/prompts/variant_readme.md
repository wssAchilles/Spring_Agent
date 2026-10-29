You are writing concise summaries for a variants experiment README that will guide a paper-writing agent.

## Task Description
{{TASK_MD}}

## Variant Specifications
{{VARIANTS_JSON}}

## Instructions

Write a JSON file `variant_summaries.json` to your working directory with this exact structure:

```json
{
  "task_summary": "One sentence describing what this task optimizes and how it is evaluated.",
  "variant_summaries": {
    "<variant_id>": "2-3 sentence summary of what this variant IS and tests."
  }
}
```

Rules:
- **task_summary**: One sentence. State the optimization objective and primary evaluation metric.
- **variant_summaries**: One entry per variant_id listed above. 2-3 sentences each.
  - For **control** variants: describe what the control represents (the best-performing program from the main campaign).
  - For **baseline** variants: describe what alternative approach or algorithm is being compared and why it is a meaningful comparison point.
  - For **ablation** variants: describe which specific component is removed or modified and what hypothesis this tests about the method's design.
- Be specific to this task — avoid generic filler language.
- Do **NOT** mention results, scores, or performance outcomes — the paper agent will compute those from the data.
- Include **ALL** variant_ids from the specifications above.
