package tech.qiantong.qknow.hermes.agent.swarm.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.swarm.dto.ContextSliceBO;
import tech.qiantong.qknow.hermes.agent.swarm.dto.HandoffFrame;
import tech.qiantong.qknow.hermes.agent.swarm.dto.HandoffStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swarm 动态上下文去中心化交接中枢 (SwarmDynamicHandoffHub)
 * 管理 transfer_to_agent 所有权转移协议，维护不可变交接历史栈
 * 施加 max_handoffs = 5 硬熔断门禁与环路特征哈希拦截 (A -> B -> A 瞬时拦截并报警)
 */
@Slf4j
@Component
public class SwarmDynamicHandoffHub {

    private final Map<String, HandoffStack> sessionStacks = new ConcurrentHashMap<>();

    /**
     * 接收去中心化交接请求并执行工程安全门禁校验
     */
    public HandoffDecisionBO transferToAgent(
            String sessionId,
            String sourceAgentId,
            String targetAgentId,
            ContextSliceBO contextSlice) {

        if (sessionId == null || sourceAgentId == null || targetAgentId == null) {
            throw new IllegalArgumentException("Session and agent identifiers must not be null");
        }

        HandoffStack currentStack = sessionStacks.computeIfAbsent(
                sessionId,
                id -> new HandoffStack(id, List.of())
        );

        synchronized (currentStack) {
            // 1. 硬熔断检查：深度拦截 (max_handoffs = 5)
            if (currentStack.isMaxDepthReached()) {
                log.error("[SwarmHandoffHub] 会话 {} 交接深度达上限 {}，触发强制熔断！",
                        sessionId, HandoffStack.MAX_HANDOFF_DEPTH);
                return HandoffDecisionBO.rejected(
                        "MAX_HANDOFF_DEPTH_EXCEEDED",
                        "交接深度已达 5 次硬上限，禁止无休止转移，已激活主管收敛。"
                );
            }

            // 2. 环路特征自检测：拦截即时乒乓振荡 (A -> B -> A)
            if (detectPingPongOscillation(currentStack, sourceAgentId, targetAgentId)) {
                log.error("[SwarmHandoffHub] 会话 {} 检测到 A->B->A 乒乓交接死循环！源: {}, 目标: {}",
                        sessionId, sourceAgentId, targetAgentId);
                return HandoffDecisionBO.rejected(
                        "SWARM_PING_PONG_CYCLE_DETECTED",
                        "检测到智能体之间乒乓振荡移交: " + sourceAgentId + " <-> " + targetAgentId
                );
            }

            // 3. 拓扑特征环路检测：检测复杂深层环路 (如 A -> B -> C -> A)
            if (detectTopologicalCycle(currentStack, targetAgentId)) {
                log.error("[SwarmHandoffHub] 会话 {} 检测到深层闭环移交，目标 Agent [{}] 已在栈中重复出现！",
                        sessionId, targetAgentId);
                return HandoffDecisionBO.rejected(
                        "SWARM_TOPOLOGICAL_CYCLE_DETECTED",
                        "智能体移交拓扑出现闭环循环，目标: " + targetAgentId
                );
            }

            // 4. 门禁通过：构建新交接帧并原子推入栈
            HandoffFrame newFrame = new HandoffFrame(
                    sourceAgentId,
                    targetAgentId,
                    contextSlice,
                    System.currentTimeMillis()
            );

            HandoffStack updatedStack = currentStack.push(newFrame);
            sessionStacks.put(sessionId, updatedStack);

            log.info("[SwarmHandoffHub] 会话 {} 成功交接: {} -> {} (当前深度: {})",
                    sessionId, sourceAgentId, targetAgentId, updatedStack.depth());

            return HandoffDecisionBO.authorized(updatedStack.depth(), targetAgentId);
        }
    }

    /**
     * 检测即时 A -> B -> A 乒乓反弹
     */
    private boolean detectPingPongOscillation(HandoffStack stack, String sourceAgentId, String targetAgentId) {
        List<HandoffFrame> frames = stack.frames();
        if (frames.isEmpty()) {
            return false;
        }
        HandoffFrame lastFrame = frames.get(frames.size() - 1);
        // 上一帧从 target 移交给 source，当前帧又从 source 移回 target
        return lastFrame.sourceAgentId().equals(targetAgentId) &&
                lastFrame.targetAgentId().equals(sourceAgentId);
    }

    /**
     * 检测多节点拓扑闭环 (目标节点是否已在栈内历史出现过)
     */
    private boolean detectTopologicalCycle(HandoffStack stack, String targetAgentId) {
        return stack.frames().stream()
                .anyMatch(f -> f.sourceAgentId().equals(targetAgentId));
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionStacks.remove(sessionId);
        }
    }

    public record HandoffDecisionBO(
            boolean authorized,
            int currentDepth,
            String targetAgentId,
            String rejectionCode,
            String rejectionReason
    ) {
        public static HandoffDecisionBO authorized(int depth, String targetAgentId) {
            return new HandoffDecisionBO(true, depth, targetAgentId, null, null);
        }

        public static HandoffDecisionBO rejected(String code, String reason) {
            return new HandoffDecisionBO(false, -1, null, code, reason);
        }
    }
}
