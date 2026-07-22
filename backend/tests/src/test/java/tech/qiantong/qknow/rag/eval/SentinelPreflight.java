package tech.qiantong.qknow.rag.eval;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class SentinelPreflight {

    private final ExecutorService executor;
    private final long timeoutNanos;

    SentinelPreflight(ExecutorService executor, Duration timeout) {
        this.executor = Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeoutNanos = timeout.toNanos();
    }

    Result run(List<NamedCheck> checks) {
        for (NamedCheck check : List.copyOf(checks)) {
            Future<Boolean> task = executor.submit(check.action());
            try {
                if (Boolean.TRUE.equals(task.get(timeoutNanos, TimeUnit.NANOSECONDS))) {
                    continue;
                }
            } catch (InterruptedException e) {
                task.cancel(true);
                Thread.currentThread().interrupt();
                return Result.failed(check.name());
            } catch (ExecutionException | TimeoutException e) {
                task.cancel(true);
                return Result.failed(check.name());
            }
            task.cancel(true);
            return Result.failed(check.name());
        }
        return Result.passed();
    }

    record NamedCheck(String name, Callable<Boolean> action) {
        NamedCheck {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("check name must not be blank");
            }
            Objects.requireNonNull(action, "action");
        }
    }

    record Result(boolean valid, String failedCheck, RagBenchmarkReport.MetricErrorCode errorCode) {
        private static Result passed() {
            return new Result(true, null, null);
        }

        private static Result failed(String check) {
            return new Result(false, check, RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED);
        }
    }
}
