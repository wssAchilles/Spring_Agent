package tech.qiantong.qknow.hermes.a2a.dsl;

import java.util.List;

/**
 * 编译期检测到拓扑依赖环路死锁时抛出的专用异常（定理 1.1）
 */
public class DslCyclicDependencyException extends RuntimeException {
    private final List<String> cycleNodes;

    public DslCyclicDependencyException(String message, List<String> cycleNodes) {
        super(message);
        this.cycleNodes = cycleNodes != null ? List.copyOf(cycleNodes) : List.of();
    }

    public List<String> getCycleNodes() {
        return cycleNodes;
    }
}
