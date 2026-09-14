package tech.qiantong.qknow.ai.worldmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多智能体世界模型与意图协商总控中枢 (Multi-Agent World Model Coordinator)
 * 贯穿多方意图协商、前向潜态预测、控制屏障硬拦截与 SHA-256 存证凭据签发
 */
@Component
public class MultiAgentWorldModelCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentWorldModelCoordinator.class);

    private final WorldModelPredictor worldModelPredictor;
    private final IntentNegotiationEngine intentNegotiationEngine;
    private final ControlBarrierGovernor controlBarrierGovernor;

    @Autowired
    public MultiAgentWorldModelCoordinator(
            WorldModelPredictor worldModelPredictor,
            IntentNegotiationEngine intentNegotiationEngine,
            ControlBarrierGovernor controlBarrierGovernor
    ) {
        this.worldModelPredictor = worldModelPredictor;
        this.intentNegotiationEngine = intentNegotiationEngine;
        this.controlBarrierGovernor = controlBarrierGovernor;
    }

    /**
     * 协调调度上下文请求
     */
    public record WorldModelSessionRequest(
            String sessionId,
            WorldModelPredictor.LatentState currentState,
            List<IntentNegotiationEngine.IntentProposal> proposals,
            List<WorldModelPredictor.AgentAction> candidateActions
    ) {}

    /**
     * 端到端执行：意图协商 -> CBF 安全审查 -> 世界模型状态前向推演 -> 签发存证收据
     */
    public WorldModelAuditReceipt processSession(WorldModelSessionRequest request) {
        long start = System.currentTimeMillis();
        String sessionId = request.sessionId() != null ? request.sessionId() : "SESS-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 意图协商博弈求解 (NBS)
        IntentNegotiationEngine.NegotiationOutcome outcome = intentNegotiationEngine.negotiateIntents(request.proposals());

        // 2. 离散控制屏障函数 (CBF) 运行时审查与修补
        ControlBarrierGovernor.BarrierAuditResult barrierResult = controlBarrierGovernor.auditAndFilter(
                request.currentState(),
                request.candidateActions()
        );

        // 3. 联合嵌入预测世界模型前向推演
        WorldModelPredictor.LatentState nextState = worldModelPredictor.predictNextState(
                request.currentState() != null ? request.currentState() : worldModelPredictor.createInitialState(42L),
                barrierResult.sanitizedActions()
        );

        long duration = System.currentTimeMillis() - start;

        // 4. 签发不可变密码学存证收据
        return WorldModelAuditReceipt.createReceipt(
                sessionId,
                outcome.agreedAllocation(),
                nextState.stateHash(),
                barrierResult.safetyMargin(),
                barrierResult.blockedActionIds(),
                duration
        );
    }

    public WorldModelPredictor getWorldModelPredictor() {
        return worldModelPredictor;
    }

    public IntentNegotiationEngine getIntentNegotiationEngine() {
        return intentNegotiationEngine;
    }

    public ControlBarrierGovernor getControlBarrierGovernor() {
        return controlBarrierGovernor;
    }
}
