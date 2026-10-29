---
name: draft-task
description: Create a complete runnable task scaffold from task.md.
---

# What the skill is about
Create a complete task-local benchmark and portable offline evaluator runtime from `task.md`.

# When to use the skill
- Task bootstrap step before orchestration.
- `capture.py`, either evaluator, `task_contract.json`, or `program_0/src/program.py` is missing.

# What to do
1. Read task intent from:
- `{{TASK_MD_PATH}}`
- `{{TASK_GOAL}}`
- `{{TASK_RUBRICS}}`
- `{{TASK_DESIGN_PATH}}`

Treat the accepted design as high-level scientific guidance and reviewed scaffold architecture,
not an exhaustive low-level specification. You may refine low-level implementation details needed
for a runnable benchmark while preserving its scientific intent. Schema, CLI, result-file contracts,
candidate isolation, no leakage, and bounded RPC remain strict. The same is true for
search/final separation, offline runtime, and same-path smoke behavior; these may not be refined away.
2. Create missing files only:
- `{{DRAFT_CAPTURE_PATH}}`
- `{{DRAFT_EVALUATE_PATH}}`
- `{{DRAFT_FINAL_EVALUATE_PATH}}`
- `{{DRAFT_TASK_CONTRACT_PATH}}`
- `{{DRAFT_ENTRYPOINT_PATH}}`
3. Keep existing files untouched.
4. Ensure:
- Do not create or edit `{{DRAFT_PROGRAM_SPEC_PATH}}`; the wrapper owns and materializes the canonical v1 specification.
- `evaluate.py` supports `--program_path` as the candidate entrypoint file and `--results_dir` as the output directory.
- The candidate entrypoint defines `handle(request) -> response` over finite JSON-compatible values; the contract interface is exactly `python_rpc_v1:handle` so production evaluation can substitute the harness-owned isolation proxy. In brokered production search and final modes (`AGENTIC_CANDIDATE_SOCKET` is set), require the fixed `/broker-code/candidate_proxy.py` path before the active evaluator loads that trusted proxy and calls `handle` in-process; the variable alone does not make another path trusted. Never execute or re-isolate the proxy. Native smoke mode keeps untrusted candidate code in a killable isolated subprocess.
- The evaluator and isolated candidate share no filesystem. Never pass evaluator-created paths, basenames, or file handles through RPC; do not use `EXCHANGE_DIR` or `AGENTIC_CANDIDATE_EXCHANGE`. Send finite inline JSON, encoding binary chunks as base64 with explicit dtype, shape, and encoding metadata. Each complete request and response must remain below the 64 MiB wire limit; for larger logical values, use a bounded chunk protocol backed only by candidate-owned in-memory state and include every chunk call in `max_candidate_rpc_calls`.
- RPC payloads contain only candidate-visible inputs permitted by the task feedback policy; never evaluator-private labels or targets, held-out answers, baseline outputs, scoring metadata, correctness/metrics, sealed feedback, or host/resource paths.
- Every independent case-seed run is one candidate episode and requires exactly one reset.
  `search_reset_sequence` and `final_reset_sequence` enumerate the complete production
  broker-visible reset transcript; `search_smoke_reset_sequence` and `final_smoke_reset_sequence`
  enumerate the complete draft-smoke broker-visible reset transcript. These arrays contain one seed
  per episode, so repeated seed values are required when one statistical seed is evaluated against
  multiple cases. Reset transcripts are global to one evaluator invocation across all proxy calls,
  socket connections, RPC clients, and candidate process restarts. Reconnecting does not reset the
  transcript. Before each episode, evaluators send `{"op": "reset", "seed": seed}`; all domain
  methods dispatch through `handle` operations.
- Start with a minimal vertical slice through evaluator loading, candidate RPC, metrics, and
  correctness. Rubric decisions must use evaluator-owned inputs or computation sufficient for a
  credible best-shot operational test;
  candidate-reported internal state remains untrusted and must not contribute to rubric decisions.
  Do not attempt exhaustive causal proof of hidden mechanisms. Account for setup, baselines,
  candidate training, rollouts, and cleanup within each
  deadline, and close dependencies for offline install on the declared platform before expanding
  the workload.
- Prefer the standard scaffold and smallest sufficient dependency set. Create an additional support
  file, including a lock or manifest, only when the task genuinely requires it and no standard
  scaffold file can carry the requirement. Every such file must use a concrete task-relative path
  and be declared in `provenance.artifacts` with its actual SHA-256. Never invent another
  dependency-lock filename or format; `requirements.txt` is the standard pinned direct-dependency
  file.
- `capture.py` is a harness-required standard compatibility artifact and must remain a safe regular
  file defining a compatible `OutputCapture`. An evaluator does not need to import or use it; do not
  delete it, fail it as unnecessary, or add artificial usage or extra result files solely to
  demonstrate a reference.
- `evaluate.py` writes `metrics.json` with numeric `combined_score`.
- `evaluate.py` writes `correct.json` with boolean `correct`.
- Each evaluator writes exactly the metric names declared for that plane in `task_contract.json`,
  without convenience aliases, diagnostics, or undeclared values. Each `correct.json` contains
  exactly one boolean key: `correct`. Normally, each results directory contains only `metrics.json`
  and `correct.json`. Only when `AGENTIC_TASK_QUALIFICATION=1`, also write `qualification.json`
  containing exactly `schema_version` and `feasible`, with the exact JSON shape
  `{"schema_version": 1, "feasible": <boolean>}`. `feasible` describes completion of the ordinary,
  non-smoke production workload within declared operational constraints; it is independent of
  scientific competitiveness and must not replace or weaken `correct`. `correct` retains the task's
  scientific success semantics. Do not emit `qualification.json` for any other flag value.
- One evaluator invocation must internally aggregate all declared episodes and write one exact
  scalar `metrics.json` object for its plane. Use the deterministic aggregation hierarchy and every
  aggregation weight declared before evaluation among applicable decisions or checkpoints, seeds,
  cases, families, and the plane score. Aggregate only hierarchy levels that exist; one episode and
  one declared metric use identity aggregation. `combined_score` is finite and higher is better for
  search. Final evaluation writes exactly its declared finite scalar metrics. Retain every failed or
  missing episode as its declared worst-case contribution; never drop it, retry it to erase the
  failure, or renormalize the remaining evidence.
- Draft smoke and scaffold qualification validate execution, protocol, finite metrics, and output
  shape, so the baseline's `correct` may be either `true` or `false`. Preserve its scientific meaning:
  it must not be changed merely to make smoke pass. Optimization promotion and publication still
  require `correct: true` under the task's declared scientific gates.
  On an evaluator exception, write a bounded exception type and message to stderr before exiting non-zero;
  never put diagnostics or traceback data in the results directory.
- Do not create a task-local promotion, freezing, or publication controller. The harness owns
  ranking, freezing, sealed final execution, and publication eligibility. Declare policy in the
  contract, never as task-local orchestration or extra evaluator output fields.
- `evaluate.py` is a development-only search evaluator; final data and metrics never contribute to search-time `combined_score` or agent feedback.
- `evaluate_final.py` is self-contained and uses the same CLI and output files, but only the sealed final resource root. It must remain runnable without `evaluate.py` or search resources and must not import or depend on them.
- `task_contract.json` uses the exact schema named in the drafting prompt, binds the canonical `task.md` digest, and declares distinct evaluators, non-overlapping resource roots and data roles, plane-specific metrics and resource ceilings, pinned upstream revisions/licenses/entrypoints/patches, dependency and artifact provenance, every search proxy's preserved/omitted axes, the candidate method/interface/edit boundary, feedback policy, seeds, baselines, promotion rule, and publication evidence. Use empty `upstream` and dependency arrays for a self-contained task that requires no external source or pip package; never invent provenance to populate them. Use `package_version: null` for a real upstream repository or asset that is not a package. In `protocol`, write search and final seeds as disjoint arrays of unique non-negative integers; `search_reset_sequence` and `final_reset_sequence` as production integer-seed arrays whose distinct seed sets equal the corresponding statistical seed sets; `search_smoke_reset_sequence` and `final_smoke_reset_sequence` as non-empty draft-smoke arrays containing only the corresponding production sequence's first seed; `baselines` as a non-empty unique string array; `promotion_rule` as a string; and every proxy's `name`, `preserves`, and `omits` as strings with unique proxy names. Reset-sequence arrays may repeat seeds and must fit the corresponding plane's `max_candidate_rpc_calls`; do not substitute descriptive objects or arrays for scalar fields.
- Contract paths are concrete POSIX relative paths without globs. Use an editable root such as `program_0/src`; never write `program_*/src`, `program_0/src/*.py`, or another wildcard.
- Use the exact harness paths `task.md`, `evaluate.py`, `evaluate_final.py`, `resources/search`, and `resources/final` in their corresponding contract fields.
- Baseline behavior is deterministic and lightweight.
- Third-party direct dependencies are pinned in `requirements.txt` and mirrored exactly in `task_contract.json` provenance dependencies. Only list pip-installable packages there; Python, CUDA, drivers, and container images are environment constraints. The wrapper owns the canonical task-local `.venv`.
- Draft-time dependency installation uses only a temporary venv under `/tmp`, never the agent home or task directory.
- Search and final benchmark data live under non-overlapping `resources/search/` and `resources/final/` roots and are resolved from `TASK_SOURCE_DIR`, not `HOME`.
- After creating, editing, or deleting any dependency or contract-declared file, synchronize
  `task_contract.json` dependency declarations, artifact paths, and affected SHA-256 values with
  the final scaffold, then revalidate the contract.
- Each compute plane's `wall_clock_seconds` includes broker and container startup and is at least 120 seconds.
- Each compute plane declares a positive `max_candidate_rpc_calls` covering the complete production RPC transcript across every declared primary seed and permitted replay; derive it from the evaluator protocol and keep it below 10000000.
- For GPU planes, `gpu_count` is the plane's aggregate allocation; `evaluator_gpu_count` and
  `candidate_gpu_count` are isolated role allocations. Each container sees only its allocation as
  local logical devices starting at zero. An evaluator must not inspect, address, reserve, or monitor
  the peer container's GPUs; the harness owns any cross-container GPU telemetry and allocation
  attestation. Smoke may reduce workload size only; it must not change accelerator selection, device
  discovery, topology validation, imports, mounts, or broker paths. Passing reduced smoke does not
  attest completion of the full production workload.
- Both evaluator smoke checks succeed with an empty `HOME` and without network access after drafting.
- With exact `AGENTIC_TASK_DRAFT_SMOKE=1`, search executes exactly
  `protocol.search_smoke_reset_sequence` and final executes exactly
  `protocol.final_smoke_reset_sequence`, while exercising the same evaluator and candidate-RPC paths
  within 120 seconds. Normal production search executes exactly `protocol.search_reset_sequence` and
  normal production final executes exactly `protocol.final_reset_sequence`; production never
  selects the reduced profile. After any declared sequence, at most one trailing deterministic
  replay of its first seed is allowed.
5. Do not modify files outside the required outputs, their standard task-local runtime support
(`requirements.txt` and benchmark data under `resources/`), and genuinely required support files
declared and hashed in `task_contract.json`, except temporary work under `/tmp`. These instructions
are defense in depth; the harness independently derives the publication set, validates contract
closure, and owns publication safety.
