package tech.qiantong.qknow.ai.code.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 沙箱代码执行结果 (不可变记录类)
 */
public record CodeExecutionResult(
        ExecutionStatus status,
        int exitCode,
        String stdout,
        String stderr,
        long executionTimeMs,
        long memoryBytes,
        int retryCount,
        List<String> generatedFiles,
        Map<String, Object> variables
) {

    public static CodeExecutionResult success(String stdout, long executionTimeMs, List<String> files) {
        return new CodeExecutionResult(
                ExecutionStatus.SUCCESS, 0, stdout != null ? stdout : "", "",
                executionTimeMs, 0L, 0,
                files != null ? files : Collections.emptyList(),
                Collections.emptyMap()
        );
    }

    public static CodeExecutionResult securityViolation(String reason) {
        return new CodeExecutionResult(
                ExecutionStatus.SECURITY_VIOLATION, -1, "",
                reason != null ? reason : "Security violation detected.",
                0L, 0L, 0,
                Collections.emptyList(), Collections.emptyMap()
        );
    }

    public static CodeExecutionResult timeout(long timeoutMs) {
        return new CodeExecutionResult(
                ExecutionStatus.TIMEOUT, -9, "",
                "Execution timed out after " + timeoutMs + "ms. Process tree forcibly killed.",
                timeoutMs, 0L, 0,
                Collections.emptyList(), Collections.emptyMap()
        );
    }

    public static CodeExecutionResult runtimeError(int exitCode, String stdout, String stderr, long executionTimeMs) {
        return new CodeExecutionResult(
                ExecutionStatus.RUNTIME_ERROR, exitCode,
                stdout != null ? stdout : "",
                stderr != null ? stderr : "",
                executionTimeMs, 0L, 0,
                Collections.emptyList(), Collections.emptyMap()
        );
    }

    public static CodeExecutionResult internalError(String message, long executionTimeMs) {
        return new CodeExecutionResult(
                ExecutionStatus.INTERNAL_ERROR, -1, "",
                message != null ? message : "Internal sandbox host error.",
                executionTimeMs, 0L, 0,
                Collections.emptyList(), Collections.emptyMap()
        );
    }

    public boolean isSuccessful() {
        return this.status == ExecutionStatus.SUCCESS && this.exitCode == 0;
    }
}
