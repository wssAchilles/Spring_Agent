package tech.qiantong.qknow.hermes.tool.resilience;

import org.springframework.ai.tool.ToolCallback;

public class ToolResilienceDecorator {

    private final ToolCallback delegate;
    private final long timeout;
    private final int maxRetries;
    private final ToolCircuitBreaker circuitBreaker;
    private final String fallbackMessage;

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxRetries, ToolCircuitBreaker circuitBreaker) {
        this(delegate, timeout, maxRetries, circuitBreaker, "工具调用失败，请稍后重试");
    }

    public ToolResilienceDecorator(ToolCallback delegate, long timeout, int maxRetries,
                                     ToolCircuitBreaker circuitBreaker, String fallbackMessage) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.circuitBreaker = circuitBreaker;
        this.fallbackMessage = fallbackMessage;
    }

    public String call(String input) {
        if (circuitBreaker != null && !circuitBreaker.allowCall()) {
            return fallbackMessage;
        }

        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                String result = delegate.call(input);
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
                return result;
            } catch (Exception e) {
                attempts++;
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                if (circuitBreaker != null && circuitBreaker.getState() == ToolCircuitBreaker.State.OPEN) {
                    return fallbackMessage;
                }
            }
        }

        return fallbackMessage;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public ToolCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
