package tech.qiantong.qknow.ai.belief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 端到端跨层级信念对齐与自反博弈调度统筹总控中枢
 */
@Component
public class HierarchicalBeliefCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalBeliefCoordinator.class);

    private final HierarchicalBayesianIntentInferer intentInferer;
    private final CrossHierarchicalBeliefAligner beliefAligner;
    private final ReflectiveGameEngine gameEngine;
    private final BeliefConsensusBarrier consensusBarrier;

    public HierarchicalBeliefCoordinator(
            HierarchicalBayesianIntentInferer intentInferer,
            CrossHierarchicalBeliefAligner beliefAligner,
            ReflectiveGameEngine gameEngine,
            BeliefConsensusBarrier consensusBarrier
    ) {
        this.intentInferer = intentInferer;
        this.beliefAligner = beliefAligner;
        this.gameEngine = gameEngine;
        this.consensusBarrier = consensusBarrier;
    }

    /**
     * 全流程闭环协同与不可变凭单签发
     */
    public BeliefAlignmentReceipt coordinateBeliefAndAction(
            String coordinationRoundId,
            String macroAgentId,
            String microAgentId,
            List<float[]> observationEmbeddings,
            Map<String, Double> macroBelief,
            Map<String, Double> microBelief,
            int requestedCognitiveLevel,
            List<String> candidateActions,
            List<String> opponentActions,
            Map<String, Map<String, Double>> payoffMatrix
    ) {
        // 1. 边界参数完备性防御
        if (coordinationRoundId == null || macroAgentId == null || microAgentId == null) {
            throw new IllegalArgumentException("轮次 ID 与智能体 ID 不能为空");
        }
        if (observationEmbeddings == null || observationEmbeddings.isEmpty()) {
            throw new IllegalArgumentException("观测证据向量不能为空");
        }
        if (macroBelief == null || microBelief == null) {
            throw new IllegalArgumentException("宏观信念与微观信念均不能为空");
        }

        long startTime = System.currentTimeMillis();
        String receiptId = "BLF-REC-" + UUID.randomUUID().toString().substring(0, 8);

        // 2. 分层贝叶斯意图推断 (定理 1.1)
        HierarchicalBayesianIntentInferer.InferenceResult intentResult =
                intentInferer.inferIntent(observationEmbeddings, null);

        // 3. 跨层级信念对称散度度量与几何平均对齐 (定理 1.3)
        CrossHierarchicalBeliefAligner.AlignmentResult alignResult =
                beliefAligner.alignBeliefs(macroBelief, microBelief);

        // 4. 信念共识控制屏障判定 (定理 1.3)
        BeliefConsensusBarrier.BarrierCheckResult barrierResult =
                consensusBarrier.checkBarrier(alignResult.jeffreysDivergence());

        // 5. 自反博弈决策求解 (定理 1.2)
        ReflectiveGameEngine.GameDecision gameDecision =
                gameEngine.solveReflectiveDecision(requestedCognitiveLevel, candidateActions, opponentActions, payoffMatrix);

        // 6. 控制屏障硬拦截与动作修补
        String finalAction = consensusBarrier.filterAction(
                gameDecision.selectedAction(), barrierResult.barrierTriggered());

        boolean alignmentSuccessful = !barrierResult.barrierTriggered();

        // 7. 构建并签发不可变存证凭单
        BeliefAlignmentReceipt receipt = BeliefAlignmentReceipt.create(
                receiptId,
                coordinationRoundId,
                macroAgentId,
                microAgentId,
                intentResult.bestIntent(),
                intentResult.confidence(),
                intentResult.entropy(),
                alignResult.jeffreysDivergence(),
                gameDecision.cognitiveLevel(),
                finalAction,
                barrierResult.barrierTriggered(),
                alignmentSuccessful,
                System.currentTimeMillis()
        );

        long costMs = System.currentTimeMillis() - startTime;
        log.info("跨层级信念对齐与博弈协同完成: receiptId={}, action={}, barrier={}, costMs={}",
                receiptId, finalAction, barrierResult.barrierTriggered(), costMs);

        return receipt;
    }

    public HierarchicalBayesianIntentInferer getIntentInferer() {
        return intentInferer;
    }

    public CrossHierarchicalBeliefAligner getBeliefAligner() {
        return beliefAligner;
    }

    public ReflectiveGameEngine getGameEngine() {
        return gameEngine;
    }

    public BeliefConsensusBarrier getConsensusBarrier() {
        return consensusBarrier;
    }
}
