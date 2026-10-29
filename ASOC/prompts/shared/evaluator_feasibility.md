# Productive work and matched controls

Preflight search and final together before freezing a workload: count every scored case, seed,
diagnostic episode, replay, required control, and reset. Calibrate successful productive baseline
work, excluding startup, imports, reset, cancellation, sleeping, and output writing. Check any
canonical minimum baseline-time requirement using that measured work, never a timeout penalty.
Choose workload settings only within the canonical task's explicit calibration delegation. A
search setting that fits its ceiling but cannot meet final's productive-work floor is infeasible;
do not inflate reported runtime, remove controls, or silently reduce a frozen workload. Reduced
smoke establishes neither the floor nor production final feasibility. Keep final outcomes sealed;
joint budget estimates and permitted baseline calibration do not authorize sealed-final tuning.

Reserve a matched work allowance for candidate and each required control before candidate work.
Preflight baseline paths early. Methods may run sequentially within the resource cap, but may not
consume a later method's reserved work or cleanup. Apply `reserved_method_deadline` before each
method and enforce it using bounded work/transport; merely checking the clock after unbounded
work is insufficient. Count baseline and candidate cleanup separately, even when both fail.
The example uses equal per-method allowances; any task-specific alternative must be predeclared
and scientifically matched. The global scheduler's reset-only reserve is not a productive-work
budget: run this joint feasibility check as well as that scheduler's reset reservation check.

```python
def finite_budget_seconds(value):
    import math
    if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
        raise ValueError("invalid time allowance")
    return value


def positive_work_seconds(value):
    if finite_budget_seconds(value) == 0:
        raise ValueError("work allowance must be positive")
    return value


def require_joint_workload_budget(*, search, final):
    required = {}
    for plane, profile in (("search", search), ("final", final)):
        episodes, methods = profile["episode_count"], profile["method_count"]
        if any(type(count) is not int or count <= 0 for count in (episodes, methods)):
            raise ValueError("invalid episode or method count")
        times = {name: finite_budget_seconds(profile[name]) for name in (
            "wall_seconds", "startup_seconds", "reset_seconds", "cleanup_seconds", "finish_seconds"
        )}
        work = positive_work_seconds(profile["work_seconds"])
        needed = (times["startup_seconds"] + times["finish_seconds"]
                  + episodes * (times["reset_seconds"] + methods * (work + times["cleanup_seconds"])))
        finite_budget_seconds(needed)
        if needed > times["wall_seconds"]:
            raise ValueError(f"{plane} productive work exceeds the declared wall budget")
        required[plane] = needed
    return required


def require_productive_baseline(measured_seconds, *, minimum_seconds):
    try:
        measured = positive_work_seconds(measured_seconds)
        minimum = positive_work_seconds(minimum_seconds)
    except ValueError:
        raise ValueError("invalid productive baseline measurement") from None
    if measured < minimum:
        raise ValueError("productive baseline is shorter than its canonical minimum")


def reserved_method_deadline(*, now, episode_deadline, methods_remaining, work_seconds, cleanup_seconds):
    for value in (now, episode_deadline, cleanup_seconds):
        finite_budget_seconds(value)
    work = positive_work_seconds(work_seconds)
    if type(methods_remaining) is not int or methods_remaining < 0:
        raise ValueError("invalid remaining method count")
    needed = (methods_remaining + 1) * (work + cleanup_seconds)
    if now + needed > episode_deadline:
        raise TimeoutError("insufficient matched method reservations")
    return now + work
```

Required baseline failure leaves the predeclared finite penalty metrics untouched, including
any canonical worst-case runtime metric. Do not replace that penalty with short elapsed failure
time. `evaluation_evidence.json` instead records actual evaluator-measured `duration_seconds`,
the retained score penalty, and `correct: false`. Neither duration certifies productive baseline
completion. Keep missing/failed cases in the original fixed denominator and mark operational
incompletion separately; cancellation must not convert failure into a successful short run.

# Feedback dependence

Apply this section only when the canonical task requires feedback dependence/adaptivity and
allows the declared intervention. Tasks without that requirement need no adaptivity gate; do
not add one or reject a valid observation-independent method. A required check with no authorized
intervention needs a design correction within canonical scope, not an undeclared repair probe.

Different suggestions over time, changing histories, or changing state hashes do not demonstrate
adaptivity. Use a predeclared matched intervention: identical initial candidate state, seed/RNG,
metadata, legal query inputs, and history length, changing only allowed completed feedback.
Require an attributable legal downstream response change, not superiority over random/fresh
policies or an extra exploitation threshold. Performance eligibility remains the canonical rule.
Choose task-appropriate contrasts: changing values without changing their ranks may leave a valid
rank-based policy unchanged. Freeze the contrast set and its aggregation; one insensitive pair
must not become an undeclared method-class restriction.
Declare the intervention branches and their resets/RPCs before freezing the protocol; do not add
undeclared probes during repair. Preserve finite penalties if a branch fails. Keep all branch
feedback within its plane and never expose evaluator-private targets or baseline outputs.

```python
def feedback_intervention(replay, *, seed, history_a, history_b):
    import json
    def canonical(value):
        return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)
    encoded_a, encoded_b = canonical(history_a), canonical(history_b)
    if len(history_a) != len(history_b) or json.loads(encoded_a) == json.loads(encoded_b):
        raise ValueError("intervention requires equal-length distinct feedback histories")
    # replay owns the declared fresh-state branch and validates suggestion legality.
    reference = json.loads(canonical(replay(seed=seed, history=json.loads(encoded_a))))
    intervened = json.loads(canonical(replay(seed=seed, history=json.loads(encoded_b))))
    return reference != intervened
```

Compare decoded numeric values, not JSON spellings (`1` and `1.0`, or signed zero, are the same
action). Apply task-specific semantic normalization before comparison when equivalent legal
actions have other representations.
The caller must verify matched inputs, state/RNG, and legality; this comparator cannot establish
those properties from a candidate self-report. Test with an observation-independent varying
sequence (must fail) and a feedback-dependent policy (can pass without better performance).
