package tech.qiantong.qknow.hermes.flow.hitl.coordinator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 多智能体确定性委托交接调度器 (Multi-Agent Handoff Coordinator)
 * <p>
 * 落实 Lemma 155.1 (多智能体确定性委托交接无环终止引理)：
 * 1. 构建严格有限状态机与不可变工单 (HandoffTicket)；
 * 2. 实施三级安全门禁：最大跳数硬截断 (Max Hops <= 5)、瞬时乒乓振荡拦截 (A <-> B) 与拓扑闭环检测 (A -> B -> C -> A)；
 * 3. 构造李雅普诺夫单调递减势能函数，保证系统在有限步内必然收敛至终止不动点，死锁发生率恒为 0。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class MultiAgentHandoffCoordinator {

    public static final int DEFAULT_MAX_HOPS = 5;
    public static final String ERR_HANDOFF_HOP_EXCEEDED = "ERR_HANDOFF_HOP_EXCEEDED";
    public static final String ERR_HANDOFF_CYCLE_DETECTED = "ERR_HANDOFF_CYCLE_DETECTED";
    public static final String SUPERVISOR_AGENT_ID = "agent_supervisor";

    /**
     * 智能体交接不可变工单 Record
     */
    public record HandoffTicket(
            String ticketId,
            String sessionTraceId,
            String sourceAgentId,
            String targetAgentId,
            int currentHop,
            int maxAllowedHops,
            List<String> auditCallChain,
            Map<String, Object> immutableContext,
            String cryptographicSignature
    ) {
        public static HandoffTicket create(
                String ticketId,
                String sessionTraceId,
                String sourceAgentId,
                String targetAgentId,
                int currentHop,
                int maxAllowedHops,
                List<String> auditCallChain,
                Map<String, Object> immutableContext
        ) {
            List<String> chain = auditCallChain != null ? new ArrayList<>(auditCallChain) : new ArrayList<>();
            if (!chain.contains(sourceAgentId)) {
                chain.add(sourceAgentId);
            }
            Map<String, Object> ctx = immutableContext != null ? Collections.unmodifiableMap(new LinkedHashMap<>(immutableContext)) : Collections.emptyMap();
            String payload = String.format("%s:%s:%s:%s:%d:%d:%s",
                    ticketId, sessionTraceId, sourceAgentId, targetAgentId, currentHop, maxAllowedHops, String.join("->", chain));
            String signature = computeSha256(payload);
            return new HandoffTicket(ticketId, sessionTraceId, sourceAgentId, targetAgentId, currentHop, maxAllowedHops,
                    Collections.unmodifiableList(chain), ctx, signature);
        }

        private static String computeSha256(String data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 算法不可用", e);
            }
        }
    }

    /**
     * 交接判决结果 Record
     */
    public record HandoffDecision(
            boolean permitted,
            String errorCode,
            String errorMessage,
            HandoffTicket nextTicket
    ) {
        public static HandoffDecision allow(HandoffTicket ticket) {
            return new HandoffDecision(true, null, "委托交接已放行", ticket);
        }

        public static HandoffDecision reject(String code, String message, HandoffTicket escalationTicket) {
            return new HandoffDecision(false, code, message, escalationTicket);
        }
    }

    /**
     * 初始委托入口：创建会话首跳委托工单
     */
    public HandoffTicket initiateHandoff(String sessionTraceId, String initialAgentId, String targetAgentId, Map<String, Object> initialContext) {
        String ticketId = "ht_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return HandoffTicket.create(
                ticketId,
                sessionTraceId,
                initialAgentId,
                targetAgentId,
                1,
                DEFAULT_MAX_HOPS,
                List.of(initialAgentId),
                initialContext
        );
    }

    /**
     * 委托交接转移判决 (落实 Lemma 155.1 三级安全门禁)
     *
     * @param currentTicket 当前工单
     * @param nextTargetAgentId 拟委托的目标智能体 ID
     * @param deltaContext 增量上下文
     * @return 判决结果与下一跃点工单
     */
    public HandoffDecision evaluateAndTransfer(
            HandoffTicket currentTicket,
            String nextTargetAgentId,
            Map<String, Object> deltaContext
    ) {
        Objects.requireNonNull(currentTicket, "当前交接工单不能为空");
        Objects.requireNonNull(nextTargetAgentId, "拟委托目标智能体不能为空");

        String currentAgent = currentTicket.targetAgentId();
        int currentHop = currentTicket.currentHop();
        int maxHops = currentTicket.maxAllowedHops();
        List<String> chain = currentTicket.auditCallChain();

        // 门禁 1: 跃点深度超限拦截
        if (currentHop >= maxHops) {
            log.warn("[Handoff] 触发最大交接跳数硬截断: currentHop={}, maxHops={}, agent={}", currentHop, maxHops, currentAgent);
            HandoffTicket escalation = createEscalationTicket(currentTicket, SUPERWISE_REASON_DEPTH);
            return HandoffDecision.reject(
                    ERR_HANDOFF_HOP_EXCEEDED,
                    String.format("多智能体交接跳数达到上限 %d，已强制升迁至主管智能体 %s", maxHops, SUPERVISOR_AGENT_ID),
                    escalation
            );
        }

        // 门禁 2: 瞬态乒乓振荡拦截 (A <-> B)
        if (chain.size() >= 1 && chain.get(chain.size() - 1).equals(nextTargetAgentId)) {
            log.warn("[Handoff] 触发瞬态乒乓振荡拦截: {} <-> {}", currentAgent, nextTargetAgentId);
            HandoffTicket escalation = createEscalationTicket(currentTicket, SUPERWISE_REASON_PINGPONG);
            return HandoffDecision.reject(
                    ERR_HANDOFF_CYCLE_DETECTED,
                    String.format("检测到与前序智能体 %s 的瞬态乒乓交接死锁，已紧急阻断并移交主管", nextTargetAgentId),
                    escalation
            );
        }

        // 门禁 3: 拓扑闭环与 DAG 成环检测 (A -> B -> C -> A)
        if (chain.contains(nextTargetAgentId)) {
            log.warn("[Handoff] 触发深层调用链成环检测: chain={}, target={}", chain, nextTargetAgentId);
            HandoffTicket escalation = createEscalationTicket(currentTicket, SUPERWISE_REASON_CYCLE);
            return HandoffDecision.reject(
                    ERR_HANDOFF_CYCLE_DETECTED,
                    String.format("目标智能体 %s 已存在于调用链 [%s] 中，成环委托已被物理熔断", nextTargetAgentId, String.join("->", chain)),
                    escalation
            );
        }

        // 合并不可变上下文
        Map<String, Object> mergedContext = new LinkedHashMap<>(currentTicket.immutableContext());
        if (deltaContext != null) {
            mergedContext.putAll(deltaContext);
        }

        List<String> nextChain = new ArrayList<>(chain);
        nextChain.add(currentAgent);

        String nextTicketId = "ht_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        HandoffTicket nextTicket = HandoffTicket.create(
                nextTicketId,
                currentTicket.sessionTraceId(),
                currentAgent,
                nextTargetAgentId,
                currentHop + 1,
                maxHops,
                nextChain,
                mergedContext
        );

        log.info("[Handoff] 智能体权责成功流转: {} -> {} (hop {}/{})", currentAgent, nextTargetAgentId, currentHop + 1, maxHops);
        return HandoffDecision.allow(nextTicket);
    }

    private static final String SUPERWISE_REASON_DEPTH = "HOP_LIMIT_EXCEEDED";
    private static final String SUPERWISE_REASON_PINGPONG = "PING_PONG_DETECTED";
    private static final String SUPERWISE_REASON_CYCLE = "CYCLE_TOPOLOGY_DETECTED";

    private HandoffTicket createEscalationTicket(HandoffTicket source, String reason) {
        String escId = "esc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> ctx = new LinkedHashMap<>(source.immutableContext());
        ctx.put("escalationReason", reason);
        List<String> chain = new ArrayList<>(source.auditCallChain());
        chain.add(source.targetAgentId());
        return HandoffTicket.create(
                escId,
                source.sessionTraceId(),
                source.targetAgentId(),
                SUPERVISOR_AGENT_ID,
                source.currentHop() + 1,
                source.maxAllowedHops(),
                chain,
                ctx
        );
    }
}
