package tech.qiantong.qknow.hermes.flow.hitl.coordinator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多智能体权责移交网格协同网络中枢接口与生产实现 (MultiAgentHandoffMeshCoordinator)
 * 基于李雅普诺夫单调递减势能定理 (Lemma 1) 与拓扑有向无环图 (DAG) 约束，消除死锁与乒乓震荡。
 *
 * @author Achilles
 * @since 2026-10-13
 */
public interface MultiAgentHandoffMeshCoordinator {

    String SUPERVISOR_AGENT_ID = "agent_supervisor";
    int DEFAULT_MAX_HOPS = 5;

    /**
     * 网格移交裁决结果 (纯 Java 21 Record)
     */
    record MeshHandoffDecision(
            boolean permitted,
            String errorCode,
            String explanation,
            HandoffTicketRecord nextTicket
    ) implements Serializable {

        public static MeshHandoffDecision allow(HandoffTicketRecord nextTicket) {
            return new MeshHandoffDecision(true, null, "网格移交放行", nextTicket);
        }

        public static MeshHandoffDecision rejectAndEscalate(String code, String message, HandoffTicketRecord supervisorTicket) {
            return new MeshHandoffDecision(false, code, message, supervisorTicket);
        }
    }

    /**
     * 初始发起带全局目标根约束的首次移交工单
     */
    HandoffTicketRecord initiateRootHandoff(
            String sessionTraceId,
            String rootGoal,
            String sourceAgentId,
            String targetAgentId,
            Map<String, Object> initialContext
    );

    /**
     * 在网格中评估并推进跃点移交 (强制执行李雅普诺夫势能单调递减与拓扑防环门禁)
     */
    MeshHandoffDecision evaluateMeshTransfer(
            HandoffTicketRecord currentTicket,
            String nextTargetAgentId,
            Map<String, Object> deltaContext
    );

    /**
     * 默认工业级实现
     */
    @Component
    class DefaultMultiAgentHandoffMeshCoordinator implements MultiAgentHandoffMeshCoordinator {

        private static final Logger log = LoggerFactory.getLogger(DefaultMultiAgentHandoffMeshCoordinator.class);

        private final Map<String, HandoffTicketRecord> activeTickets = new ConcurrentHashMap<>();

        @Override
        public HandoffTicketRecord initiateRootHandoff(
                String sessionTraceId,
                String rootGoal,
                String sourceAgentId,
                String targetAgentId,
                Map<String, Object> initialContext
        ) {
            String ticketId = "ticket-root-" + UUID.randomUUID().toString().substring(0, 8);
            HandoffTicketRecord ticket = HandoffTicketRecord.create(
                    ticketId,
                    sessionTraceId,
                    rootGoal,
                    sourceAgentId,
                    targetAgentId,
                    1,
                    DEFAULT_MAX_HOPS,
                    List.of(sourceAgentId),
                    initialContext
            );
            activeTickets.put(ticketId, ticket);
            log.info("【多智能体网格】初始化根移交工单成功: ticketId={}, traceId={}, {} -> {}",
                    ticketId, sessionTraceId, sourceAgentId, targetAgentId);
            return ticket;
        }

        @Override
        public MeshHandoffDecision evaluateMeshTransfer(
                HandoffTicketRecord currentTicket,
                String nextTargetAgentId,
                Map<String, Object> deltaContext
        ) {
            Objects.requireNonNull(currentTicket, "当前工单不能为空");
            Objects.requireNonNull(nextTargetAgentId, "目标智能体标识不能为空");

            // 1. 密码学完整性常量时间验签
            if (!currentTicket.verifySignature()) {
                log.warn("【多智能体网格】工单签名被篡改或时序侧信道不匹配: ticketId={}", currentTicket.ticketId());
                HandoffTicketRecord supervisorTicket = createSupervisorTicket(currentTicket, "ERR_SIGNATURE_TAMPERED");
                return MeshHandoffDecision.rejectAndEscalate(
                        "ERR_SIGNATURE_TAMPERED",
                        "工单密码学验签失败，疑似被篡改，强制升迁至主管智能体",
                        supervisorTicket
                );
            }

            int nextHop = currentTicket.currentHop() + 1;
            List<String> currentChain = currentTicket.auditCallChain();

            // 2. 门禁一：跃点深度硬截断 (Lyapunov Potential Lower-Bound)
            if (nextHop > currentTicket.maxAllowedHops()) {
                log.warn("【多智能体网格】移交跃点超过硬上限: nextHop={}, maxAllowed={}", nextHop, currentTicket.maxAllowedHops());
                HandoffTicketRecord supervisorTicket = createSupervisorTicket(currentTicket, "ERR_MAX_HOPS_EXCEEDED");
                return MeshHandoffDecision.rejectAndEscalate(
                        "ERR_MAX_HOPS_EXCEEDED",
                        "移交跳数超过阈值 (" + currentTicket.maxAllowedHops() + ")，强制收敛至主管智能体",
                        supervisorTicket
                );
            }

            // 3. 门禁二：瞬态二元乒乓震荡拦截 (A <-> B: 目标智能体等于直接来源智能体)
            if (nextTargetAgentId.equalsIgnoreCase(currentTicket.sourceAgentId())) {
                log.warn("【多智能体网格】检测到直接二元乒乓震荡移交: {} <-> {}", currentTicket.targetAgentId(), nextTargetAgentId);
                HandoffTicketRecord supervisorTicket = createSupervisorTicket(currentTicket, "ERR_PING_PONG_CYCLE");
                return MeshHandoffDecision.rejectAndEscalate(
                        "ERR_PING_PONG_CYCLE",
                        "检测到智能体之间直接乒乓振荡，打断循环并升迁至主管智能体",
                        supervisorTicket
                );
            }

            // 4. 门禁三：历史调用链成环拦截 (A -> B -> C -> A)
            if (currentChain.contains(nextTargetAgentId) || currentTicket.targetAgentId().equalsIgnoreCase(nextTargetAgentId)) {
                log.warn("【多智能体网格】检测到 DAG 调用链成环: chain={}, target={}", currentChain, nextTargetAgentId);
                HandoffTicketRecord supervisorTicket = createSupervisorTicket(currentTicket, "ERR_TOPOLOGICAL_LOOP");
                return MeshHandoffDecision.rejectAndEscalate(
                        "ERR_TOPOLOGICAL_LOOP",
                        "目标智能体已存在于历史调用链中，构成闭环死锁，强制升迁至主管智能体",
                        supervisorTicket
                );
            }

            // 5. 合并上下文 (保留全局根目标与历史因果快照)
            Map<String, Object> mergedContext = new LinkedHashMap<>(currentTicket.immutableContext());
            if (deltaContext != null && !deltaContext.isEmpty()) {
                mergedContext.putAll(deltaContext);
            }

            // 6. 生成不可变下一跳工单
            List<String> nextChain = new ArrayList<>(currentChain);
            nextChain.add(currentTicket.targetAgentId());

            String nextTicketId = "ticket-mesh-" + UUID.randomUUID().toString().substring(0, 8);
            HandoffTicketRecord nextTicket = HandoffTicketRecord.create(
                    nextTicketId,
                    currentTicket.sessionTraceId(),
                    currentTicket.rootTaskGoal(),
                    currentTicket.targetAgentId(),
                    nextTargetAgentId,
                    nextHop,
                    currentTicket.maxAllowedHops(),
                    nextChain,
                    mergedContext
            );

            activeTickets.put(nextTicketId, nextTicket);
            log.info("【多智能体网格】移交判定通过: ticketId={}, hop={}/{}, {} -> {}",
                    nextTicketId, nextHop, currentTicket.maxAllowedHops(), currentTicket.targetAgentId(), nextTargetAgentId);

            return MeshHandoffDecision.allow(nextTicket);
        }

        private HandoffTicketRecord createSupervisorTicket(HandoffTicketRecord currentTicket, String escalationReason) {
            String supervisorTicketId = "ticket-esc-" + UUID.randomUUID().toString().substring(0, 8);
            List<String> chain = new ArrayList<>(currentTicket.auditCallChain());
            chain.add(currentTicket.targetAgentId());

            Map<String, Object> context = new LinkedHashMap<>(currentTicket.immutableContext());
            context.put("escalationReason", escalationReason);
            context.put("escalationTriggeredEpochMs", System.currentTimeMillis());

            return HandoffTicketRecord.create(
                    supervisorTicketId,
                    currentTicket.sessionTraceId(),
                    currentTicket.rootTaskGoal(),
                    currentTicket.targetAgentId(),
                    SUPERVISOR_AGENT_ID,
                    currentTicket.currentHop() + 1,
                    currentTicket.maxAllowedHops(),
                    chain,
                    context
            );
        }
    }
}
