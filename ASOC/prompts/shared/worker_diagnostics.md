### Preserve causal worker diagnostics

Use this task-agnostic recipe for evaluator-owned baseline workers and every nested
subprocess layer. Launch with `stderr=subprocess.PIPE`, immediately attach
`BoundedWorkerStderr(process.stderr)`, and keep stdout under its existing protocol.
Do not wait before starting capture: a full stderr pipe can deadlock the worker.
The caller owns deadlines, process-group termination, wait/reap, and cleanup reserves.
After termination/wait, call `close()` even on failure. It drains through EOF, or stops
within a bounded reserve if an inherited writer still holds the pipe. Reserve one
second for this reader teardown; closing the pipe does not prove descendants stopped.
Never use blanket `DEVNULL`, global redirects, or subprocess monkey-patches.

The snapshot retains at most 4096 bytes of complete UTF-8 diagnostic lines, plus
observed-byte/truncation/capture-error metadata. Oversized and incomplete lines are
dropped whole so a sliced credential never becomes an apparently safe suffix.
It is diagnostic evidence, never trusted failure classification or stop authority.
An empty snapshot means the cause is unknown; do not invent a timeout.

On **search drafting/qualification failure only**, forward the snapshot's complete `stderr`
to evaluator stderr for the existing host redaction and bounded repair-evidence path:
require `PLANE == "search"` and either `AGENTIC_TASK_QUALIFICATION == "1"` or
`AGENTIC_TASK_DRAFT_SMOKE == "1"` in the evaluator environment. These existing flags
select diagnostic visibility, never success or stop authority. Ordinary optimization
search receives fixed categories only, so private worker text cannot enter candidate feedback.
Also record bounded component/category/operation and capture metadata. Do not emit
success excerpts. In sealed final evaluation emit fixed categories only, never raw
stderr, exception messages, hidden inputs, labels, or scores. Preserve exact result
schemas. Existing host sanitization remains mandatory; this collector does not redact.

At worker entry points that currently swallow exceptions, call
`report_worker_exception(error)` into the captured worker pipe in search repair mode
before exiting nonzero; final and ordinary search modes use a fixed failure category
instead. Nested worker parents must forward captured
failure stderr at each layer. Save the original failing component, category, and
operation **before** cleanup; record cleanup errors separately without replacing it.

```python
import collections
import os
import selectors
import sys
import threading


class BoundedWorkerStderr:
    LIMIT = 4096

    def __init__(self, stream):
        self.stream = stream
        self.fd = stream.fileno()
        os.set_blocking(self.fd, False)
        self.lines = collections.deque()
        self.pending = bytearray()
        self.dropping_line = False
        self.observed_bytes = 0
        self.retained_bytes = 0
        self.truncated = False
        self.capture_error = None
        self.stop = threading.Event()
        self.result = None
        self.reader = threading.Thread(target=self._drain, name="worker-stderr-reader")
        self.reader.start()

    def _accept(self, chunk):
        self.observed_bytes += len(chunk)
        for segment in chunk.splitlines(keepends=True):
            # Only LF ends a diagnostic line. splitlines also splits CR; accumulate it.
            complete = segment.endswith(b"\n")
            if not self.dropping_line:
                if len(self.pending) + len(segment) > self.LIMIT:
                    self.pending.clear()
                    self.dropping_line = True
                    self.truncated = True
                else:
                    self.pending.extend(segment)
            if complete:
                if not self.dropping_line:
                    line = bytes(self.pending).decode("utf-8", errors="replace")
                    size = len(line.encode("utf-8"))
                    if size <= self.LIMIT:
                        while self.retained_bytes + size > self.LIMIT:
                            self.retained_bytes -= len(self.lines.popleft().encode("utf-8"))
                            self.truncated = True
                        self.lines.append(line)
                        self.retained_bytes += size
                    else:
                        self.truncated = True
                self.pending.clear()
                self.dropping_line = False

    def _drain(self):
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(self.fd, selectors.EVENT_READ)
                while not self.stop.is_set():
                    if not selector.select(timeout=.05):
                        continue
                    try:
                        chunk = os.read(self.fd, 8192)
                    except BlockingIOError:
                        continue
                    if not chunk:
                        return
                    self._accept(chunk)
                self.capture_error = "reader_stopped_before_eof"
                self.truncated = True
        except (OSError, ValueError):
            self.capture_error = "stderr_capture_failed"
            self.truncated = True
        finally:
            if self.pending or self.dropping_line:
                self.truncated = True
            self.pending.clear()
            self.stream.close()

    def close(self):
        if self.result is not None:
            return dict(self.result)
        self.reader.join(timeout=.2)
        if self.reader.is_alive():
            self.stop.set()
            self.reader.join(timeout=.2)
        if self.reader.is_alive():
            # Do not close a stream concurrently with its owner or claim cleanup passed.
            raise RuntimeError("stderr reader cleanup unresolved")
        self.result = {
            "stderr": "".join(self.lines),
            "observed_bytes": self.observed_bytes,
            "retained_bytes": self.retained_bytes,
            "truncated": self.truncated,
            "capture_error": self.capture_error,
        }
        return dict(self.result)


def report_worker_exception(error):
    # Search-only entry point; the host sanitizes this untrusted diagnostic later.
    name = type(error).__name__
    if not name.isascii() or not name.isidentifier() or len(name) > 80:
        name = "WorkerException"
    try:
        message = str(error)
    except Exception:
        message = "<exception message unavailable>"
    # Drop an oversized message whole; never retain a potentially split credential.
    if len(message) > 2048 or len(message.encode("utf-8", errors="replace")) > 2048:
        message = "<exception message omitted: too large>"
    print(f"worker_exception type={name}\n{message}", file=sys.stderr, flush=True)
```
