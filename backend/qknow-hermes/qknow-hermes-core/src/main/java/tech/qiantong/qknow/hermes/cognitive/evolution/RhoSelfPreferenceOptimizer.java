package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;

import java.util.List;
import java.util.Objects;

/**
 * RHO 回顾性自我偏好演进器（对应方向四 · Retrospective Harness Optimization）。
 * <p>
 * 无外部人类标注条件下，利用历史成对成功/失败执行轨迹在 {@code S^1023} 黎曼切空间更新超网策略原型质心，
 * 并将高惩罚失败规律沉积至双时态海马体 {@link HippocampalVertexTier#ERROR_BOOK} 防错节点。
 * </p>
 */
public class RhoSelfPreferenceOptimizer {

    private final HypersphericalSupernetRouter supernetRouter;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final BiTemporalHippocampalGraphEngine hippocampalGraphEngine;

    public RhoSelfPreferenceOptimizer(
            HypersphericalSupernetRouter supernetRouter,
            UnitSphereEmbeddingResolver embeddingResolver,
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine
    ) {
        this.supernetRouter = Objects.requireNonNull(supernetRouter, "HypersphericalSupernetRouter 不能为空");
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.hippocampalGraphEngine = hippocampalGraphEngine;
    }

    /**
     * 对一组回顾性自我偏好轨迹对执行无参数微调演化。
     */
    public RhoEvolutionReceipt evolveFromPreferencePairs(List<SelfPreferencePair> pairs, double learningRate) {
        if (pairs == null || pairs.isEmpty()) {
            return new RhoEvolutionReceipt(0, 0, 0, 0.0, System.currentTimeMillis());
        }
        long nowMs = System.currentTimeMillis();
        int updatedArms = 0;
        int recordedErrorBooks = 0;
        double sumMargin = 0.0;

        for (SelfPreferencePair pair : pairs) {
            if (pair == null || pair.chosenOperator() == null || pair.chosenEffort() == null) {
                continue;
            }
            double margin = Math.max(0.0, pair.chosenUtility() - pair.rejectedUtility());
            sumMargin += margin;

            float[] posVec = embeddingResolver.resolveUnit1024(pair.queryText());
            float[] negVec = (pair.rejectedFailureReason() != null && !pair.rejectedFailureReason().isBlank())
                    ? embeddingResolver.resolveUnit1024(pair.rejectedFailureReason())
                    : null;

            if (margin > 0.05 && supernetRouter.applyRiemannianPreferenceStep(
                    pair.chosenOperator(), pair.chosenEffort(), posVec, negVec, learningRate)) {
                updatedArms++;
            }

            if (hippocampalGraphEngine != null && pair.rejectedFailureReason() != null
                    && !pair.rejectedFailureReason().isBlank()) {
                String qId = "rho_q_" + Math.abs((pair.queryText() != null ? pair.queryText() : "q").hashCode());
                String errId = "rho_err_" + Math.abs(pair.rejectedFailureReason().hashCode());
                hippocampalGraphEngine.upsertVertex(new HippocampalVertex(
                        qId,
                        pair.queryText() != null ? pair.queryText() : "",
                        HippocampalVertexTier.PHRASE_ENTITY,
                        posVec
                ));
                float[] errVec = negVec != null ? negVec : posVec;
                hippocampalGraphEngine.upsertVertex(new HippocampalVertex(
                        errId,
                        "[RHO错题本] " + pair.rejectedFailureReason(),
                        HippocampalVertexTier.ERROR_BOOK,
                        errVec
                ));
                hippocampalGraphEngine.ingestFactEdge(
                        qId, errId, "RHO_FAILURE_WARNING", false, errVec,
                        nowMs, nowMs, "rho-self-preference"
                );
                recordedErrorBooks++;
            }
        }

        double avgMargin = pairs.isEmpty() ? 0.0 : sumMargin / pairs.size();
        return new RhoEvolutionReceipt(pairs.size(), updatedArms, recordedErrorBooks, avgMargin, nowMs);
    }
}
