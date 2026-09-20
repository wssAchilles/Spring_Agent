package tech.qiantong.qknow.ai.swarm.delegation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 多智能体自适应分层协同与意图委托网络中枢外观调度器
 * 编排超球面意图匹配、有界交接状态机守卫、思考流因果偏序继承与密码学存证凭单签发
 */
public class HierarchicalSwarmDelegationMetacenter {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalSwarmDelegationMetacenter.class);

    private final HypersphericalIntentMatcher intentMatcher;
    private final BoundedHandoverGuard handoverGuard;
    private final ThinkingContextPropagator contextPropagator;

    public HierarchicalSwarmDelegationMetacenter(
            HypersphericalIntentMatcher intentMatcher,
            BoundedHandoverGuard handoverGuard,
            ThinkingContextPropagator contextPropagator
    ) {
        this.intentMatcher = Objects.requireNonNull(intentMatcher, "intentMatcher 不能为空");
        this.handoverGuard = Objects.requireNonNull(handoverGuard, "handoverGuard 不能为空");
        this.contextPropagator = Objects.requireNonNull(contextPropagator, "contextPropagator 不能为空");
    }

    /**
     * 委托调度请求 (纯 Java 21 Record)
     */
    public record DelegationRequest(
            String sessionId,
            String sourceAgentId,
            String taskDescription,
            float[] queryVector1536,
            List<HypersphericalIntentMatcher.AgentCard> candidateAgents,
            String antecedentReasoningContent,
            String antecedentConclusion
    ) {}

    /**
     * 委托流转执行结果 (纯 Java 21 Record)
     */
    public record DelegationResult(
            boolean success,
            String dispatchedAgentId,
            int delegationDepth,
            double affinityScore,
            String formattedPromptForTarget,
            SwarmDelegationReceipt receipt,
            String failureReason
    ) {
        public static DelegationResult successful(
                String agentId, int depth, double affinity, String prompt, SwarmDelegationReceipt receipt) {
            return new DelegationResult(true, agentId, depth, affinity, prompt, receipt, null);
        }

        public static DelegationResult failed(String failureReason) {
            return new DelegationResult(false, null, -1, 0.0, null, null, failureReason);
        }
    }

    /**
     * 驱动多智能体分层自适应流转
     */
    public DelegationResult dispatchDelegation(DelegationRequest request) {
        long startMicros = System.currentTimeMillis() * 1000L;
        String receiptId = "REC-SWARM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        log.info("[SwarmMetacenter] 收到委托请求, 会话: {}, 发起方: {}", request.sessionId(), request.sourceAgentId());

        // 1. 防线一：阿里千问 1536 维超球面测地意图匹配
        HypersphericalIntentMatcher.IntentMatchResult matchResult =
                intentMatcher.matchTargetAgent(request.queryVector1536(), request.candidateAgents());

        if (!matchResult.delegated()) {
            log.warn("[SwarmMetacenter] 意图未达置信度门禁, 安全上浮: {}", matchResult.escalationReason());
            return DelegationResult.failed("INTENT_CONFIDENCE_GATE_REJECTED: " + matchResult.escalationReason());
        }

        String targetAgentId = matchResult.targetAgentId();
        double affinity = matchResult.affinityScore();

        // 2. 防线二：李雅普诺夫单调深度与死锁状态机守卫
        BoundedHandoverGuard.GuardDecision guardDecision =
                handoverGuard.evaluateHandover(request.sessionId(), request.sourceAgentId(), targetAgentId);

        if (!guardDecision.authorized()) {
            log.error("[SwarmMetacenter] 状态机守卫拦截交接: {} - {}", guardDecision.abortCode(), guardDecision.abortReason());
            return DelegationResult.failed(guardDecision.abortCode() + ": " + guardDecision.abortReason());
        }

        // 3. 防线三：思考流因果脚手架抽取与提示词切片格式化
        ThinkingContextPropagator.InheritedRationale rationale = contextPropagator.distillRationaleForHandoff(
                request.sourceAgentId(), "SourceSpecialist",
                request.antecedentReasoningContent(), request.antecedentConclusion());

        String formattedPrompt = contextPropagator.formatRationalePromptSection(List.of(rationale))
                + "\n[当前派发子任务]: " + request.taskDescription();

        // 4. 防线四：生成不可变委托存证凭单并实施 SHA-256 签名
        long endMicros = System.currentTimeMillis() * 1000L;
        String status = "DISPATCHED";
        String signature = SwarmDelegationReceipt.computeSignature(
                receiptId, request.sessionId(), request.sourceAgentId(),
                guardDecision.currentDepth(), affinity, startMicros, endMicros, status);

        SwarmDelegationReceipt receipt = new SwarmDelegationReceipt(
                receiptId,
                request.sessionId(),
                request.sourceAgentId(),
                guardDecision.currentDepth(),
                affinity,
                List.of(request.sourceAgentId(), targetAgentId),
                startMicros,
                endMicros,
                status,
                signature
        );

        log.info("[SwarmMetacenter] 委托流转成功, 目标: {}, 深度: {}, 凭单: {}",
                targetAgentId, guardDecision.currentDepth(), receiptId);

        return DelegationResult.successful(
                targetAgentId, guardDecision.currentDepth(), affinity, formattedPrompt, receipt);
    }
}
