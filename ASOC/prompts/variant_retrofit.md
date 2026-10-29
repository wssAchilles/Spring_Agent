# Retrofit Program for Final Evaluation

You are preparing a program for final evaluation in a scientific comparison study. The program was discovered by an automated search process focused on maximizing a score. You must now adapt it for rigorous, paper-ready evaluation.

This step applies only to legacy executable programs. Contracted `python_rpc_v1:handle`
candidates are copied unchanged by the host; their evaluator owns analysis output.
Do not change stopping conditions, seeds, or compute budgets. Preserve initialization,
search schedules, sample selection, scoring, and all existing output values.
Do not swallow program, evaluator, or CSV-writing errors. Report failures explicitly.

**Follow these steps in order:**

## Step 0: Understand the Task and Plan Evaluation Metrics

Start by reading `task.md` — this describes the task, but keep in mind it was written for the **search/investigation phase**, not for final evaluation. The goals and constraints it describes are about finding good programs, not about comprehensively evaluating them for a paper.

```
{{TASK_DESCRIPTION}}
```

Now, **before reading any code**, think about this task from a **paper-writing perspective**:
- What metrics would a reviewer at a top conference expect to see in a results table comparing ablations and baselines?
- What evaluation protocol is standard in this field?
- Are there metrics that were likely skipped during search (because they were expensive or irrelevant to score optimization) but are essential for a paper?
- Identify measurements already available during the existing execution. Respect all task constraints and evaluator ownership; do not add new evaluation steps or expose sealed data.

Write down your planned set of evaluation metrics. You will revisit this list after reading the program and evaluator code.

## Step 1: Read the Program and Evaluator, Then Finalize Metrics

Now read the current program and evaluator source below. Compare what they already measure against your planned metrics from Step 0.

- Identify gaps: metrics you planned that are not currently computed.
- Identify the existing stopping, sampling, seeding, and budget rules so instrumentation preserves them.
- Finalize your metric plan using values already computed by the program.

## Step 2: Implement the Changes

Modify the **training program** in `program_0/` (the main `program.py` or equivalent entrypoint) to:

1. **Add a `--results_dir` command-line argument** using `argparse`. This argument controls where the program writes its output files (`_results.json` and the `analysis/` CSV subdirectory). It must default to the directory containing the script itself (`os.path.dirname(os.path.abspath(__file__))`). All output file paths (`_results.json`, `analysis/*.csv`) must be relative to this directory, NOT hardcoded to the script's location.

   Example:
   ```python
   import argparse
   parser = argparse.ArgumentParser()
   parser.add_argument("--results_dir", type=str,
                       default=os.path.dirname(os.path.abspath(__file__)),
                       help="Directory to write _results.json and analysis/ CSVs")
   args = parser.parse_args()
   results_dir = args.results_dir
   os.makedirs(results_dir, exist_ok=True)
   ```

   Then use `results_dir` when writing outputs:
   ```python
   results_path = os.path.join(results_dir, "_results.json")
   analysis_dir = os.path.join(results_dir, "analysis")
   ```

   **This is critical** — the evaluator may run the same program multiple times in parallel, passing a different `--results_dir` each time so outputs don't collide. Without this argument, parallel runs would overwrite each other's results.

2. **Preserve the existing `--seed` interface and random-state behavior.** Do not add
   or move `random.seed`, `numpy.random.seed`, or `torch.manual_seed` calls. Do not
   change defaults, force a seed, or invent statistical replications. Record the
   actual seed if available; otherwise report its absence.

3. **Log training metrics per step/epoch during execution.** As the program trains, record values like loss, accuracy, or any relevant metric at each step or epoch. Store these in memory (e.g., a list of dicts) so they can be written to CSV after training completes.

3. **Write standardized CSV analysis files** after training finishes (and after `_results.json` is written). The CSVs go under an `analysis/` subdirectory relative to `--results_dir`.

## Required CSV Files

{{CSV_SCHEMAS}}

### Column Descriptions

**run_summary.csv** — One row summarizing the entire evaluation run:
- `evaluation_id`: unique identifier (use env var `EVALUATION_ID` if set, else generate one)
- `variant_id`: identifier for this variant (use env var `VARIANT_ID` if set, else `"control_best"`)
- `variant_kind`: one of `control`, `baseline`, `ablation` (use env var `VARIANT_KIND` if set, else `"control"`)
- `base_program`: which program this derives from (use env var `BASE_PROGRAM` if set, else `"best"`)
- `agent_name`: the agent that ran this (use env var `AGENT_NAME` if set, else `"unknown"`)
- `correct`: boolean — whether the program passed correctness checks
- `combined_score`: float — the primary score from evaluation
- `overall_pass`: boolean — true if correct and all metrics valid
- `primary_metric_name`: name of the primary metric (e.g., `"test_accuracy"`)
- `primary_metric_value`: value of the primary metric
- `program_dir`: path to the program directory
- `results_dir`: path to the results/output directory

**metric_values.csv** — One row per metric:
- `evaluation_id`, `variant_id`: same as in run_summary
- `metric_name`: name of the metric (e.g., `"test_accuracy"`, `"training_time"`)
- `metric_value`: numeric value
- `is_primary_metric`: boolean, true for the primary/main metric

**timeseries.csv** — One row per timestep per metric series, logged **during training**:
- `evaluation_id`, `variant_id`: same as above
- `series_name`: name of the time series (e.g., `"train_loss"`, `"train_accuracy"`, `"val_loss"`)
- `step`: integer step or epoch number
- `value`: finite numeric value at that step; omit the row when the value is unavailable (never write NaN or infinity). Encode boolean observations as numeric `1` or `0`, never Python `True`/`False` strings.

This is the key file that requires modifying the training loop. If the program has epochs, log per-epoch metrics. If it has steps, log per-step metrics. Log whatever metrics are naturally computed during training (loss, accuracy, etc.).

## Implementation Guidelines

1. **Add `--results_dir` argument** as described above. All output files must be written relative to this directory.

2. **Preserve seeding behavior** as described above, including defaults and call order.

3. **Modify the training loop** to accumulate timeseries data as training progresses. For example, after each epoch, append `{"series_name": "train_loss", "step": epoch, "value": loss_val}` to a list.

4. **After training completes** (and after `_results.json` is written), write all three CSV files:
   - `run_summary.csv` and `metric_values.csv` can be populated from `_results.json` / the program's own computed metrics
   - `timeseries.csv` is populated from the accumulated per-step/epoch data

5. Use only Python stdlib (`csv`, `json`, `os`, `pathlib`) for CSV writing — no pandas dependency.

6. Create the `analysis/` directory (under `--results_dir`) if it does not exist.

7. Read variant metadata from environment variables: `VARIANT_ID`, `VARIANT_KIND`, `BASE_PROGRAM`, `EVALUATION_ID`, `AGENT_NAME`.

8. **Preserve the complete experiment** — do not change what the program computes or how it solves the task. Keep early termination, partial evaluation, subsampling, search schedules, and resource limits unchanged; add only observational logging.

9. **Minimize performance overhead.** The logging and CSV writing must not significantly increase execution time. A small overhead is acceptable but avoid anything expensive in the hot loop (e.g., no file I/O per step — accumulate in memory and write once at the end). If the training loop is performance-critical, keep per-step logging to simple list appends only.

10. CSV-writing failures must remain observable. Do not catch and discard exceptions or report success after missing outputs.

11. **Log existing evaluation-time metrics identified in Step 0/1.** Use `metric_values.csv` and, where applicable, `timeseries.csv`. Do not add solver calls, extra training, candidate RPC calls, or new metric computations that change the execution budget. Report unavailable evidence rather than fabricating it.

## Host-Sealed Evaluator

Do not modify `evaluate.py`; it defines the host-owned scoring and evaluation protocol and will be restored after your run. Add paper-ready measurements only through program outputs and the three analysis CSVs.

## Task Description Updates

Keep `task.md` and its constraints unchanged. Document additional observational
outputs separately; do not rewrite the task or broaden its evaluation protocol.

**Your program and task-documentation changes will automatically propagate to all variant workspaces** (ablations, baselines, controls), so all variants will be evaluated under the same conditions.

## Extraction Manifest (for context)
```json
{{EXTRACTION_MANIFEST}}
```

## Current Evaluator Source (for understanding what metrics are produced and how the program is invoked)
```python
{{EVALUATOR_SOURCE}}
```

## Current Program Source (THIS is the file you need to modify)
```python
{{PROGRAM_SOURCE}}
```

{{REFERENCE_EVALUATOR_SOURCE}}

## Important
- Modify `program.py` (the training program), not the host-sealed `evaluate.py`.
- The program must still produce all its original outputs (`_results.json`, etc.)
- The CSV files are ADDITIONAL outputs written by the program after training
- The timeseries data must come from actual training — log real metrics as they are computed during the training loop
- Preserve stopping conditions, sampling, random-state behavior, and compute budgets
- Log available metrics; identify missing evidence without adding new experiments
