package tech.qiantong.qknow.hermes.a2a.dsl.exception;

import java.util.List;

/**
 * 门禁二：工作流 DSL 检测到无界环路或死锁异常
 */
public class UnboundedCycleException extends RuntimeException {
    private final List<String> cycleNodes;

    public UnboundedCycleException(String message, List<String> cycleNodes) {
        super(message);
        this.cycleNodes = cycleNodes != null ? List.copyOf(cycleNodes) : List.of();
    }

    public List<String> getCycleNodes() {
        return cycleNodes;
    }
}
