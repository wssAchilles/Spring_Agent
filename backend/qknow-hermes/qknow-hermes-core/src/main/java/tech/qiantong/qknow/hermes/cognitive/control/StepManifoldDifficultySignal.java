package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 多候选流形散度与跳深认知难度信号（Phase 10.1 MS-SHSCR，定理 1 不可变输入载体）。
 * <p>
 * 用于打破单向量 {@code D_S ≡ 0} 坍缩与 {@code MEDIUM} 带宽不可达死区，融合真实 Top-K 检索向量、
 * 桥接多跳深度、PA-TSG 分叉歧义与 Supernet 先验路由信号。
 * </p>
 */
public record StepManifoldDifficultySignal(
        CognitiveStepPhase phase,
        List<float[]> topKCandidateVectors,
        double chordDriftSq,
        double specRiskScore,
        int hopDepth,
        int priorRejectCount,
        boolean bridgeForkAmbiguity,
        double bridgeTop2Margin,
        DeepSeekReasoningEffort supernetPriorEffort,
        boolean isolatedContext
) {
    public StepManifoldDifficultySignal {
        phase = Objects.requireNonNullElse(phase, CognitiveStepPhase.EXPLORE_RETRIEVE);
        List<float[]> cleaned = new ArrayList<>();
        if (topKCandidateVectors != null) {
            for (float[] vec : topKCandidateVectors) {
                if (vec != null && vec.length > 0) {
                    cleaned.add(vec.clone());
                }
            }
        }
        topKCandidateVectors = List.copyOf(cleaned);
        chordDriftSq = Double.isNaN(chordDriftSq) ? 0.0 : Math.max(0.0, chordDriftSq);
        specRiskScore = Double.isNaN(specRiskScore) ? 0.0 : Math.max(0.0, Math.min(1.0, specRiskScore));
        hopDepth = Math.max(0, hopDepth);
        priorRejectCount = Math.max(0, priorRejectCount);
        bridgeTop2Margin = Double.isNaN(bridgeTop2Margin) ? 1.0 : Math.max(0.0, Math.min(2.0, bridgeTop2Margin));
        supernetPriorEffort = Objects.requireNonNullElse(supernetPriorEffort, DeepSeekReasoningEffort.LOW);
    }

    public StepManifoldDifficultySignal withChordDriftSq(double nextDriftSq) {
        return new StepManifoldDifficultySignal(
                phase, topKCandidateVectors, nextDriftSq, specRiskScore,
                hopDepth, priorRejectCount, bridgeForkAmbiguity, bridgeTop2Margin,
                supernetPriorEffort, isolatedContext
        );
    }
}
