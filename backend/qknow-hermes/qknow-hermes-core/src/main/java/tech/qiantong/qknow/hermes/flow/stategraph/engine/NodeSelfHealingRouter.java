package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * 节点局部自愈路由器 (NodeSelfHealingRouter)
 * 提供非致命异常精准甄别、Decorrelated Full Jitter 指数退避重试与 Fallback 旁路降级保护
 */
@Slf4j
@Component
public class NodeSelfHealingRouter {

    private final int defaultMaxRetries;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final Map<String, StateGraphNode> nodeRegistry = new ConcurrentHashMap<>();

    public NodeSelfHealingRouter() {
        this(3, 100, 3000);
    }

    public NodeSelfHealingRouter(int defaultMaxRetries, long baseBackoffMs, long maxBackoffMs) {
        this.defaultMaxRetries = Math.max(defaultMaxRetries, 0);
        this.baseBackoffMs = Math.max(baseBackoffMs, 1);
        this.maxBackoffMs = Math.max(maxBackoffMs, baseBackoffMs);
    }

    public void registerNode(StateGraphNode node) {
        if (node != null && node.getNodeUuid() != null) {
            nodeRegistry.put(node.getNodeUuid(), node);
        }
    }

    /**
     * 携带自愈机制执行节点
     *
     * @param node 当前节点定义
     * @param context 状态图上下文
     * @return 节点执行结果
     */
    public NodeRunResultBO executeWithHealing(StateGraphNode node, StateGraphContext context) {
        registerNode(node);
        int maxRetries = node.getMaxLocalRetries() >= 0 ? node.getMaxLocalRetries() : defaultMaxRetries;
        int attempt = 0;

        while (true) {
            try {
                if (node.getExecutionHandler() != null) {
                    NodeRunResultBO result = node.getExecutionHandler().apply(node, context);
                    if (attempt > 0) {
                        log.info("[SelfHealing] 节点 {} 经历 {} 次重试后成功自愈", node.getNodeUuid(), attempt);
                        context.getSelfHealedOccurred().set(true);
                    }
                    return result;
                } else {
                    // 无 handler 时提供默认空完成
                    NodeRunResultBO defaultResult = new NodeRunResultBO();
                    defaultResult.setNodeUuid(node.getNodeUuid());
                    defaultResult.setStatus(1);
                    return defaultResult;
                }
            } catch (Throwable t) {
                log.warn("[SelfHealing] 节点 {} 第 {} 次执行异常: {}",
                        node.getNodeUuid(), attempt + 1, t.getMessage());

                // 1. 甄别是否为致命不可恢复异常
                if (isFatalError(t)) {
                    log.error("[SelfHealing] 节点 {} 遭遇致命异常 ({}), 禁止重试, 转向 Fallback 或快速失败",
                            node.getNodeUuid(), t.getClass().getSimpleName());
                    return routeToFallbackOrError(node, context, t);
                }

                // 2. 检查局部重试配额与全局重试预算池
                if (attempt >= maxRetries || !context.tryConsumeRetryBudget()) {
                    log.warn("[SelfHealing] 节点 {} 重试配额耗尽 (当前尝试: {}, 局部上限: {}), 转向 Fallback 旁路",
                            node.getNodeUuid(), attempt, maxRetries);
                    return routeToFallbackOrError(node, context, t);
                }

                // 3. 执行 Decorrelated Full Jitter 指数退避
                attempt++;
                long sleepMs = computeFullJitterBackoff(attempt);
                log.debug("[SelfHealing] 节点 {} 准备第 {} 次重试, 退避等待 {} ms", node.getNodeUuid(), attempt, sleepMs);
                try {
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return routeToFallbackOrError(node, context, ie);
                }
            }
        }
    }

    /**
     * 路由至 Fallback 旁路节点或返回错误结果
     */
    private NodeRunResultBO routeToFallbackOrError(StateGraphNode node, StateGraphContext context, Throwable cause) {
        String fallbackUuid = node.getFallbackNodeUuid();
        if (fallbackUuid != null && !fallbackUuid.isBlank()) {
            StateGraphNode fallbackNode = nodeRegistry.get(fallbackUuid);
            if (fallbackNode != null) {
                log.info("[SelfHealing] 节点 {} 激活 Fallback 旁路节点 {}", node.getNodeUuid(), fallbackUuid);
                context.getSelfHealedOccurred().set(true);
                return executeWithHealing(fallbackNode, context);
            }
        }

        NodeRunResultBO errorResult = new NodeRunResultBO();
        errorResult.setNodeUuid(node.getNodeUuid());
        errorResult.setStatus(2); // 失败
        errorResult.setErrorMessage(cause != null ? cause.getMessage() : "Unknown execution error");
        return errorResult;
    }

    /**
     * 异常类型甄别：区分致命异常与瞬时抖动异常
     */
    private boolean isFatalError(Throwable t) {
        if (t == null) return false;
        if (t instanceof SecurityException) return true;
        if (t instanceof IllegalArgumentException) return true;
        if (t instanceof IllegalStateException) return true;
        if (t instanceof NullPointerException) return true;

        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("forbidden") || lower.contains("403")) {
                return true;
            }
        }

        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return isFatalError(cause);
        }
        return false;
    }

    /**
     * 计算 Decorrelated Full Jitter 指数退避时延
     */
    private long computeFullJitterBackoff(int attempt) {
        long exponentialCap = Math.min(maxBackoffMs, baseBackoffMs * (1L << Math.min(attempt, 10)));
        return ThreadLocalRandom.current().nextLong(0, exponentialCap + 1);
    }
}
