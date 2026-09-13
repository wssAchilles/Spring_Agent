package tech.qiantong.qknow.hermes.tool.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.*;

/**
 * 工业级工具弹性包装器：超时中断、滑动窗口熔断、Head-Tail 智能截断与错误自愈结构化回传
 */
@Slf4j
public class ToolResilienceDecorator implements ToolCallback {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-resilience-worker");
        t.setDaemon(true);
        return t;
    });

    private final ToolCallback delegate;
    private final long timeout;
    private final int maxOutputLength;
    private final int maxRetries;
    private final ToolCircuitBreaker circuitBreaker;
    private final String fallbackMessage;

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxRetries, ToolCircuitBreaker circuitBreaker) {
        this(delegate, timeout, 16000, maxRetries, circuitBreaker, "工具调用失败，请稍后重试");
    }

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxRetries,
                                  ToolCircuitBreaker circuitBreaker, String fallbackMessage) {
        this(delegate, timeout, 16000, maxRetries, circuitBreaker, fallbackMessage);
    }

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxOutputLength,
                                  int maxRetries, ToolCircuitBreaker circuitBreaker) {
        this(delegate, timeout, maxOutputLength, maxRetries, circuitBreaker, null);
    }

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxOutputLength,
                                  int maxRetries, ToolCircuitBreaker circuitBreaker, String fallbackMessage) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.maxOutputLength = maxOutputLength > 0 ? maxOutputLength : 16000;
        this.maxRetries = Math.max(maxRetries, 1);
        this.circuitBreaker = circuitBreaker;
        this.fallbackMessage = fallbackMessage;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate != null ? delegate.getToolDefinition() : null;
    }

    @Override
    public String call(String input) {
        if (circuitBreaker != null && !circuitBreaker.allowCall()) {
            log.warn("[ToolResilience] 工具熔断器处于开启状态，直接短路阻断调用: tool={}", getToolName());
            return fallbackMessage != null ? fallbackMessage : "{\"status\":\"error\",\"code\":\"CIRCUIT_OPEN\",\"message\":\"工具熔断保护开启中，请稍后重试\"}";
        }

        int attempts = 0;
        String lastError = null;

        while (attempts < maxRetries) {
            attempts++;
            Future<String> future = null;
            try {
                future = EXECUTOR.submit(() -> delegate.call(input));
                String rawResult = future.get(timeout, TimeUnit.MILLISECONDS);
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
                return truncateOutput(rawResult);
            } catch (TimeoutException te) {
                if (future != null) {
                    future.cancel(true);
                }
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                log.warn("[ToolResilience] 工具调用超时: tool={}, timeoutMs={}", getToolName(), timeout);
                return String.format("{\"status\":\"error\",\"code\":\"TIMEOUT_ERROR\",\"message\":\"工具调用超时（超过 %d 毫秒），请减少查询范围或稍后重试。\"}", timeout);
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                lastError = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                log.warn("[ToolResilience] 工具调用抛出执行异常: tool={}, error={}", getToolName(), lastError);
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                if (circuitBreaker != null && circuitBreaker.getState() == ToolCircuitBreaker.State.OPEN) {
                    return fallbackMessage != null ? fallbackMessage : "{\"status\":\"error\",\"code\":\"CIRCUIT_OPEN\",\"message\":\"工具熔断保护开启中，请稍后重试\"}";
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[ToolResilience] 工具调用线程被中断: tool={}", getToolName());
                return "{\"status\":\"error\",\"code\":\"INTERRUPTED\",\"message\":\"工具调用被系统中断\"}";
            } catch (Exception e) {
                lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("[ToolResilience] 工具调用发生未知异常: tool={}, error={}", getToolName(), lastError);
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
            }
        }

        // 若调用方配置了非默认的兜底文本，则返回兜底文本；否则返回包含自愈指导的结构化 JSON
        if (fallbackMessage != null) {
            return fallbackMessage;
        }

        return String.format("{\"status\":\"error\",\"code\":\"EXECUTION_ERROR\",\"message\":\"工具执行异常: %s。请检查输入参数并自我修正后再试。\"}", lastError != null ? lastError : "未知执行错误");
    }

    private String truncateOutput(String result) {
        if (result == null || result.length() <= maxOutputLength) {
            return result;
        }
        int headLen = (int) (maxOutputLength * 0.6);
        int tailLen = (int) (maxOutputLength * 0.4);
        if (headLen + tailLen >= result.length()) {
            return result;
        }
        String head = result.substring(0, headLen);
        String tail = result.substring(result.length() - tailLen);
        int omitted = result.length() - headLen - tailLen;
        String notice = String.format("\n\n... [系统保护提示：工具返回值过长已省略 %d 字符，保留头部与尾部关键信息] ...\n\n", omitted);
        return head + notice + tail;
    }

    private String getToolName() {
        return delegate != null && delegate.getToolDefinition() != null ? delegate.getToolDefinition().name() : "unknown";
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public ToolCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
