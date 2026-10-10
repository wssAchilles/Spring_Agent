package tech.qiantong.qknow.hermes.flow.hitl.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.MultiAgentHandoffCoordinator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一等公民多智能体交接节点执行器 (MultiAgentHandoffNodeExecutor)
 * 将三级门禁 Handoff 状态机无缝嵌入工作流拓扑节点，支持智能体专家分工与权责接力。
 */
public class MultiAgentHandoffNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentHandoffNodeExecutor.class);

    private final MultiAgentHandoffCoordinator coordinator;

    public MultiAgentHandoffNodeExecutor(MultiAgentHandoffCoordinator coordinator) {
        this.coordinator = coordinator != null ? coordinator : new MultiAgentHandoffCoordinator();
    }

    public MultiAgentHandoffNodeExecutor() {
        this(new MultiAgentHandoffCoordinator());
    }

    public record HandoffExecutionResult(
            boolean isSuccessful,
            String delegatedAgentId,
            int hopCount,
            String handoffTicketId,
            String fallbackAgentId,
            String rejectionReason
    ) {}

    /**
     * 在工作流拓扑节点中执行智能体交接
     *
     * @param sourceAgent 当前执行的源智能体
     * @param targetAgent 目标接替智能体
     * @param currentHops 当前累计跃点数
     * @param history     历史交接链路
     * @param payload     交接业务上下文负载
     * @return 节点执行结果
     */
    public HandoffExecutionResult executeHandoff(
            String sourceAgent,
            String targetAgent,
            int currentHops,
            List<String> history,
            Map<String, Object> payload
    ) {
        log.info("[HandoffNode] 拓扑节点触发交接申请: source={}, target={}, hops={}", sourceAgent, targetAgent, currentHops);

        String sessionTraceId = "trace_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MultiAgentHandoffCoordinator.HandoffTicket initialTicket = coordinator.initiateHandoff(
                sessionTraceId, sourceAgent, sourceAgent, payload
        );

        MultiAgentHandoffCoordinator.HandoffDecision decision = coordinator.evaluateAndTransfer(
                initialTicket, targetAgent, payload
        );

        if (decision.permitted() && decision.nextTicket() != null) {
            return new HandoffExecutionResult(
                    true,
                    decision.nextTicket().targetAgentId(),
                    decision.nextTicket().currentHop(),
                    decision.nextTicket().ticketId(),
                    null,
                    null
            );
        } else {
            String fallback = decision.nextTicket() != null ? decision.nextTicket().targetAgentId() : MultiAgentHandoffCoordinator.SUPERVISOR_AGENT_ID;
            log.warn("[HandoffNode] 交接被门禁拦截，升迁至兜底 Supervisor: source={}, target={}, reason={}",
                    sourceAgent, targetAgent, decision.errorMessage());
            return new HandoffExecutionResult(
                    false,
                    null,
                    currentHops,
                    null,
                    fallback,
                    decision.errorMessage()
            );
        }
    }

    public MultiAgentHandoffCoordinator getCoordinator() {
        return coordinator;
    }
}
