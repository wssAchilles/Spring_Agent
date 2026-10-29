# Required-worker failure identity

Copy these helpers into both self-contained evaluators. A required baseline crash is not a
candidate timeout. The exception retains its category before cancellation can change state.
`record_failure` must emit the bounded category (not merely append to a discarded local list).
Use search-only stderr; final diagnostics remain sealed. No result-file schema changes.

```python
class InvalidResetResponse(RuntimeError):
    pass


def require_reset_ack(response):
    if type(response) is not dict or set(response) != {"ok"} or response["ok"] is not True:
        raise InvalidResetResponse("invalid reset response")


class RequiredBaselineFailure(RuntimeError):
    def __init__(self, category):
        self.category = bounded_failure_name(category, "invalid_baseline_diagnostic")
        super().__init__("required baseline failed")


def bounded_failure_name(value, fallback):
    import re
    return value if isinstance(value, str) and re.fullmatch(r"[a-z][a-z0-9_]{0,79}", value) else fallback


def describe_episode_failure(error, operation):
    # A finally-block cleanup exception may wrap the original required-worker failure.
    context = error
    for _ in range(8):
        if isinstance(context, InvalidResetResponse):
            return {"component": "candidate", "category": "invalid_reset_response",
                    "operation": "reset"}
        if isinstance(context, RequiredBaselineFailure):
            return {"component": "baseline", "category": context.category,
                    "operation": "baseline_worker"}
        context = context.__context__
        if context is None:
            break
    return {"component": "candidate", "category": "timeout",
            "operation": bounded_failure_name(operation, "unknown")}



def fail_required_baseline(category, cancel_work, record_failure):
    failure = RequiredBaselineFailure(category)
    try:
        record_failure(failure.category)
    except Exception:
        failure.add_note("baseline diagnostic recording failed")
    try:
        cancel_work()
    except Exception:
        failure.add_note("baseline cleanup failed")
    raise failure from None


def collect_required_baseline(result, *, deadline, clock, cancel_work, record_failure):
    try:
        remaining = deadline - clock()
        if remaining <= 0:
            raise TimeoutError()
        value = result(timeout=remaining)
        if clock() > deadline:
            raise TimeoutError()
        return value
    except RequiredBaselineFailure:
        raise
    except (RuntimeError, TimeoutError) as error:
        category = "baseline_timeout" if isinstance(error, TimeoutError) else "baseline_result_failure"
        fail_required_baseline(category, cancel_work, record_failure)


def guard_required_baseline(call, failure_category, cancel_work, record_failure):
    def check():
        category = failure_category()
        if category is not None:
            fail_required_baseline(category, cancel_work, record_failure)
    def guarded(request):
        check()
        result = call(request)
        check()
        return result
    return guarded
```

Use `describe_episode_failure` only for the handled reset, timeout, and baseline exceptions.
Extend it with fixed categories for explicit protocol exceptions; never infer authority from text.
Capture candidate operation identity at the RPC failure, before entering cleanup. Keep cleanup
failure secondary, preserve the primary exception, and continue required resets where possible.
For baseline polling, retain the worker's exit status or signal as bounded numeric metadata;
`baseline_exit`, `baseline_signal`, `baseline_timeout`, and `baseline_result_failure` are distinct.
Check required workers during candidate work and at final result collection. An unexpected
programming error must propagate; adapters translate only expected worker errors to RuntimeError.
A callback failure cannot skip worker cancellation. Cleanup remains caller-owned and bounded.
