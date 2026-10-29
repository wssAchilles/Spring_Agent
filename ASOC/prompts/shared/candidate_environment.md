# Native candidate subprocess environment

For the native smoke subprocess only, construct a fresh environment from explicit runtime names.
Do not copy the evaluator environment and then filter reserved prefixes: even referring to the
reserved harness namespace in executable evaluator code fails the capability check. Never forward
credentials, broker socket/authentication variables, task/resource paths, or evaluator-only flags.
The trusted broker proxy still runs in-process after the existing two-signal check; do not apply
this subprocess recipe to it or modify its environment.

Adapt this recipe inside each self-contained evaluator. Select the candidate interpreter from
`TASK_PYTHON`, falling back to `sys.executable` only when unset. Pass it and arguments as a list
with `shell=False`, retaining the existing isolation, kill/reap, and timeout controls. Use only
explicit additional runtime names required by the declared task; never prefix-wide inheritance.
Preserve assigned device masks, including an empty CPU-only mask, without probing other devices.
Do not depend on user-home caches or inherited Python import overrides.

```python
def candidate_subprocess_settings(parent_environment):
    import sys

    runtime_names = (
        "PATH", "LD_LIBRARY_PATH", "LANG", "LC_ALL", "TZ",
        "CUDA_VISIBLE_DEVICES", "NVIDIA_VISIBLE_DEVICES", "NVIDIA_DRIVER_CAPABILITIES",
        "OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS", "NUMEXPR_NUM_THREADS",
        "XLA_PYTHON_CLIENT_PREALLOCATE", "XLA_PYTHON_CLIENT_MEM_FRACTION",
        "XLA_PYTHON_CLIENT_ALLOCATOR",
    )
    environment = {
        name: parent_environment[name]
        for name in runtime_names
        if name in parent_environment
    }
    environment.update(
        HOME="/nonexistent",
        PYTHONNOUSERSITE="1",
        PYTHONDONTWRITEBYTECODE="1",
        PYTHONUNBUFFERED="1",
    )
    return parent_environment.get("TASK_PYTHON", sys.executable), environment
```
