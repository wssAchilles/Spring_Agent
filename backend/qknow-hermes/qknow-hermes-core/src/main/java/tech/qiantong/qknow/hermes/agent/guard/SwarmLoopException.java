package tech.qiantong.qknow.hermes.agent.guard;

/**
 * 蜂群环路防死锁与振荡熔断专有异常
 */
public class SwarmLoopException extends RuntimeException {

    private final String errorCode;

    public SwarmLoopException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
