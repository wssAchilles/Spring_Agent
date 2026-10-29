You are preparing the extracted best program for post-hoc analysis.

Goal:
- ensure `program_0` can produce a reusable checkpoint without changing the task objective
- ensure `_results.json` records the checkpoint path and any metadata needed for later analysis
- keep correctness and the main task metric intact

Constraints:
- make the smallest edits that achieve checkpoint saving
- do not change evaluation semantics or scoring logic
- keep training/evaluation behavior as close as possible to the current best program
- the official evaluator will be rerun once after your edits

Task:
```md
{{TASK_MD}}
```

Evaluator:
```python
{{EVALUATOR_SOURCE}}
```

Program:
```python
{{PROGRAM_SOURCE}}
```

Existing results:
```json
{{RESULTS_JSON}}
```
