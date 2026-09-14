package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 阿里千问 1536 维超球面目标漂移度量器 (定理 1.2: 测地线偏角距离监控)
 */
@Component
public class GoalDriftDetector {

    private static final Logger log = LoggerFactory.getLogger(GoalDriftDetector.class);

    public enum DriftStatus {
        ALIGNED,          // 高度对齐 (余弦 >= 0.75)
        MODERATE_DRIFT,   // 中度偏离 (0.60 <= 余弦 < 0.75，需安全修补)
        SEVERE_DRIFT      // 严重漂移 (余弦 < 0.60，强制阻断)
    }

    public record IntentVector(
            String intentId,
            float[] embedding,
            String description
    ) {}

    public record DriftEvaluation(
            String rootIntentId,
            String currentActionId,
            double cosineSimilarity,
            double geodesicAngleRad,
            DriftStatus status,
            String diagnosticMessage
    ) {}

    /**
     * 在千问 1536 维超球面上计算余弦相似度与测地线偏角
     */
    public DriftEvaluation evaluateDrift(
            IntentVector rootIntent,
            IntentVector actionIntent
    ) {
        if (rootIntent == null || actionIntent == null) {
            return new DriftEvaluation(
                    "UNKNOWN", "UNKNOWN", 0.0, Math.PI / 2,
                    DriftStatus.SEVERE_DRIFT, "MISSING_INTENT_CONTEXT"
            );
        }

        double cosine = calculateCosineSimilarity(rootIntent.embedding(), actionIntent.embedding());
        // 超球面测地线夹角 theta = arccos(cosine)
        double clampedCosine = Math.max(-1.0, Math.min(1.0, cosine));
        double geodesicAngle = Math.acos(clampedCosine);

        DriftStatus status;
        String msg;
        if (cosine >= 0.75) {
            status = DriftStatus.ALIGNED;
            msg = "Action intent strictly aligned with root user instruction";
        } else if (cosine >= 0.60) {
            status = DriftStatus.MODERATE_DRIFT;
            msg = "Action intent shows moderate semantic drift, requires safe hyperplane projection";
        } else {
            status = DriftStatus.SEVERE_DRIFT;
            msg = "Action intent severely drifted away from root goal, potential goal drift attack";
        }

        return new DriftEvaluation(
                rootIntent.intentId(),
                actionIntent.intentId(),
                cosine,
                geodesicAngle,
                status,
                msg
        );
    }

    /**
     * 计算单位向量余弦点积
     */
    private double calculateCosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length != vecB.length) {
            return 0.5; // 兜底
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
