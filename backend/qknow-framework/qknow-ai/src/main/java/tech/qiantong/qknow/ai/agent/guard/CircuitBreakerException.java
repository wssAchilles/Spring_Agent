package tech.qiantong.qknow.ai.agent.guard;

/**
 * Agent 断路器异常，用于在发生死循环或超时时熔断执行
 */
public class CircuitBreakerException extends RuntimeException {
    public CircuitBreakerException(String message) {
        super(message);
    }
    
    public CircuitBreakerException(String message, Throwable cause) {
        super(message, cause);
    }
}
