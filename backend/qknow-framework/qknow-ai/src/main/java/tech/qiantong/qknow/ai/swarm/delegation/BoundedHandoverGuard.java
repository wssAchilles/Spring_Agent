package tech.qiantong.qknow.ai.swarm.delegation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于离散李雅普诺夫单调能量衰减函数的有界交接状态机守卫
 * 硬编码最大委托深度 D_max <= 4，毫秒级双向乒乓与拓扑环路拦截
 */
public class BoundedHandoverGuard {

    private static final Logger log = LoggerFactory.getLogger(BoundedHandoverGuard.class);

    public static final int D_MAX = 4; // 定理 1 硬编码最大委托深度上限
    private final Map<String, DelegationCallStack> activeStacks = new ConcurrentHashMap<>();

    /**
     * 评估并核准跨智能体委托交接转移
     */
    public GuardDecision evaluateHandover(String sessionId, String sourceAgentId, String targetAgentId) {
        if (sessionId == null || sourceAgentId == null || targetAgentId == null) {
            throw new IllegalArgumentException("会话与智能体标识均不可为空");
        }

        DelegationCallStack stack = activeStacks.computeIfAbsent(
                sessionId,
                id -> new DelegationCallStack(id, List.of())
        );

        synchronized (stack) {
            // 1. 李雅普诺夫单调递减深度硬熔断
            if (stack.depth() >= D_MAX) {
                log.error("[HandoverGuard] 会话 {} 达到硬编码最大深度 D_max = {}，触发强制收敛熔断！", sessionId, D_MAX);
                return GuardDecision.aborted("MAX_DELEGATION_DEPTH_EXCEEDED",
                        "委托深度已达 " + D_MAX + " 层上限，强制上浮至主控协调者执行收敛。");
            }

            // 2. 双向即时乒乓反弹检测 (A -> B -> A)
            if (isPingPongOscillation(stack, sourceAgentId, targetAgentId)) {
                log.error("[HandoverGuard] 会话 {} 拦截到即时乒乓振荡！{} <-> {}", sessionId, sourceAgentId, targetAgentId);
                return GuardDecision.aborted("SWARM_PING_PONG_DETECTED",
                        "检测到智能体乒乓移交死锁: " + sourceAgentId + " <-> " + targetAgentId);
            }

            // 3. 多节点拓扑闭环检测 (A -> B -> C -> A)
            if (isTopologicalCycle(stack, targetAgentId)) {
                log.error("[HandoverGuard] 会话 {} 拦截到多节点拓扑闭环！目标 Agent {} 已在调用栈中出现", sessionId, targetAgentId);
                return GuardDecision.aborted("SWARM_TOPOLOGICAL_CYCLE_DETECTED",
                        "检测到拓扑闭环，目标智能体已作为前序发起者出现: " + targetAgentId);
            }

            // 4. 安全通过：压入不可变调用栈帧
            DelegationFrame frame = new DelegationFrame(sourceAgentId, targetAgentId, System.currentTimeMillis());
            DelegationCallStack updatedStack = stack.push(frame);
            activeStacks.put(sessionId, updatedStack);

            log.info("[HandoverGuard] 会话 {} 授权移交: {} -> {} (当前深度: {})",
                    sessionId, sourceAgentId, targetAgentId, updatedStack.depth());
            return GuardDecision.permitted(updatedStack.depth(), targetAgentId);
        }
    }

    private boolean isPingPongOscillation(DelegationCallStack stack, String sourceAgentId, String targetAgentId) {
        if (stack.frames().isEmpty()) {
            return false;
        }
        DelegationFrame last = stack.frames().get(stack.frames().size() - 1);
        return last.sourceAgentId().equals(targetAgentId) && last.targetAgentId().equals(sourceAgentId);
    }

    private boolean isTopologicalCycle(DelegationCallStack stack, String targetAgentId) {
        return stack.frames().stream().anyMatch(f -> f.sourceAgentId().equals(targetAgentId));
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            activeStacks.remove(sessionId);
        }
    }

    public record DelegationFrame(String sourceAgentId, String targetAgentId, long timestampMillis) {}

    public record DelegationCallStack(String sessionId, List<DelegationFrame> frames) {
        public DelegationCallStack(String sessionId, List<DelegationFrame> frames) {
            this.sessionId = sessionId;
            this.frames = frames != null ? Collections.unmodifiableList(new ArrayList<>(frames)) : List.of();
        }
        public int depth() {
            return frames.size();
        }
        public DelegationCallStack push(DelegationFrame frame) {
            List<DelegationFrame> next = new ArrayList<>(frames);
            next.add(frame);
            return new DelegationCallStack(sessionId, next);
        }
    }

    public record GuardDecision(boolean authorized, int currentDepth, String targetAgentId, String abortCode, String abortReason) {
        public static GuardDecision permitted(int depth, String targetAgent) {
            return new GuardDecision(true, depth, targetAgent, null, null);
        }
        public static GuardDecision aborted(String code, String reason) {
            return new GuardDecision(false, -1, null, code, reason);
        }
    }
}
