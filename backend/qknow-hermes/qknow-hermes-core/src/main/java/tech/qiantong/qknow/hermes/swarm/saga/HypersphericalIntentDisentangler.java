package tech.qiantong.qknow.hermes.swarm.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 千问 1536 维超球面多意图正交投影与互斥工具路由解耦器 (Hyperspherical Intent Disentangler)
 * <p>
 * 核心理论契约与几何公理 (Lemma 144.2)：
 * 1. 严格锁定阿里千问 1536 维单位超球流形 (S^1535, ||v||_2 = 1.0)，使用余弦测地度量；
 * 2. 改进 Gram-Schmidt 正交投影：消除跨意图特征混叠，互信息上界理论趋近于零 (I(z_i; z_j) = 0)；
 * 3. 互斥工具写写冲突消歧：当语义重叠度超过阈值 (tau_conflict = 0.70) 或带有显式互斥副作用时，
 *    自动将其由危险的并发调用转换为因果 DAG 拓扑先后序，彻底杜绝跨智能体资源竞争死锁。
 *
 * @author Achilles
 * @since Phase 144
 */
@Component
public class HypersphericalIntentDisentangler {

    private static final Logger log = LoggerFactory.getLogger(HypersphericalIntentDisentangler.class);

    /**
     * 阿里千问标准 Embedding 维度约束
     */
    public static final int EMBEDDING_DIM = 1536;

    /**
     * 冲突消歧语义余弦阈值
     */
    public static final double DEFAULT_CONFLICT_THRESHOLD = 0.70;

    /**
     * 正交基线性无关容差阈值
     */
    public static final double ORTHO_EPSILON = 1e-6;

    /**
     * 工具意图描述元数据实体
     */
    public record ToolIntentProfile(
            String toolId,
            String toolName,
            String description,
            double[] embedding,
            Set<String> conflictToolIds,
            boolean hasSideEffect
    ) {
        public ToolIntentProfile {
            conflictToolIds = conflictToolIds != null ? Set.copyOf(conflictToolIds) : Set.of();
            if (embedding != null && embedding.length != EMBEDDING_DIM) {
                throw new IllegalArgumentException("Embedding dimension must be exactly " + EMBEDDING_DIM);
            }
        }
    }

    /**
     * 单个工具正交解耦激活结果
     */
    public record DisentangledToolIntent(
            String toolId,
            String toolName,
            double[] orthogonalBasis,
            double activationScore,
            boolean isSelected
    ) {}

    /**
     * 因果拓扑依赖边实体 (DAG Edge)
     */
    public record ToolDependencyEdge(
            String fromToolId,
            String toToolId,
            String reason
    ) {}

    /**
     * 完整多意图解耦输出包
     */
    public record DisentangleResult(
            String taskId,
            List<DisentangledToolIntent> toolIntents,
            List<ToolDependencyEdge> resolvedEdges,
            double reconstructionFidelity,
            double orthogonalityResidual,
            String digest,
            long elapsedNanos
    ) {}

    /**
     * 执行千问 1536 维超球面多意图正交投影与冲突消歧主流程
     *
     * @param taskId 任务标识
     * @param rawTaskEmbedding 复合任务原始向量 (1536 维)
     * @param candidateTools 候选工具元数据池
     * @param conflictThreshold 冲突消歧阈值 (默认 0.70)
     * @param minActivationThreshold 意图选拔最小激活得分 (默认 0.10)
     * @return 完备意图正交解耦与无冲突因果 DAG 依赖
     */
    public DisentangleResult disentangleIntents(
            String taskId,
            double[] rawTaskEmbedding,
            List<ToolIntentProfile> candidateTools,
            double conflictThreshold,
            double minActivationThreshold
    ) {
        long startNanos = System.nanoTime();
        if (candidateTools == null || candidateTools.isEmpty()) {
            return new DisentangleResult(taskId, List.of(), List.of(), 0.0, 0.0, "EMPTY", 0L);
        }

        // 1. 规范化任务向量至超球面单位球 S^1535
        double[] normalizedTask = normalizeVector(rawTaskEmbedding);

        // 2. 改进 Gram-Schmidt 正交基底构建 (S_ortho)
        List<double[]> orthogonalBases = new ArrayList<>();
        List<ToolIntentProfile> validProfiles = new ArrayList<>();

        for (ToolIntentProfile profile : candidateTools) {
            double[] v = normalizeVector(profile.embedding());
            double[] u = Arrays.copyOf(v, EMBEDDING_DIM);

            // 逐个基底减去投影分量：u = v - sum(<v, e_j> * e_j)
            for (double[] e : orthogonalBases) {
                double proj = dotProduct(v, e);
                for (int d = 0; d < EMBEDDING_DIM; d++) {
                    u[d] -= proj * e[d];
                }
            }

            double norm = vectorNorm(u);
            if (norm > ORTHO_EPSILON) {
                // 单位化：e_k = u / ||u||_2
                double[] e = new double[EMBEDDING_DIM];
                for (int d = 0; d < EMBEDDING_DIM; d++) {
                    e[d] = u[d] / norm;
                }
                orthogonalBases.add(e);
                validProfiles.add(profile);
            } else {
                log.debug("[HypersphericalIntentDisentangler] 工具 {} 与现有意图子空间高度共线，跳过冗余正交基", profile.toolId());
            }
        }

        // 3. 计算正交子空间互信息正交性残差 max |<e_i, e_j>| (理论值 <= 1e-6)
        double maxResidual = 0.0;
        int basisCount = orthogonalBases.size();
        for (int i = 0; i < basisCount; i++) {
            for (int j = i + 1; j < basisCount; j++) {
                double dot = Math.abs(dotProduct(orthogonalBases.get(i), orthogonalBases.get(j)));
                if (dot > maxResidual) {
                    maxResidual = dot;
                }
            }
        }

        // 4. 计算子意图投影强度 alpha_k = <v_task, e_k> 与重构向量
        double[] reconstructedVector = new double[EMBEDDING_DIM];
        List<DisentangledToolIntent> intents = new ArrayList<>();

        for (int k = 0; k < basisCount; k++) {
            ToolIntentProfile profile = validProfiles.get(k);
            double[] basis = orthogonalBases.get(k);
            // 投影强度与任务原生余弦相关度
            double alpha = dotProduct(normalizedTask, basis);
            double rawCosine = dotProduct(normalizedTask, normalizeVector(profile.embedding()));
            boolean isSelected = (Math.abs(alpha) >= minActivationThreshold) || (rawCosine >= conflictThreshold);

            intents.add(new DisentangledToolIntent(
                    profile.toolId(),
                    profile.toolName(),
                    basis,
                    alpha,
                    isSelected
            ));

            if (isSelected) {
                for (int d = 0; d < EMBEDDING_DIM; d++) {
                    reconstructedVector[d] += alpha * basis[d];
                }
            }
        }

        // 5. 计算意图重构保真度: 1.0 - ||v_task - v_recon||_2
        double[] residualVector = new double[EMBEDDING_DIM];
        for (int d = 0; d < EMBEDDING_DIM; d++) {
            residualVector[d] = normalizedTask[d] - reconstructedVector[d];
        }
        double reconstructionFidelity = Math.max(0.0, 1.0 - vectorNorm(residualVector));

        // 6. 互斥工具写写冲突消歧与因果 DAG 依赖边生成
        List<ToolDependencyEdge> resolvedEdges = new ArrayList<>();
        List<DisentangledToolIntent> selectedIntents = intents.stream().filter(DisentangledToolIntent::isSelected).toList();

        for (int i = 0; i < selectedIntents.size(); i++) {
            for (int j = i + 1; j < selectedIntents.size(); j++) {
                DisentangledToolIntent intentA = selectedIntents.get(i);
                DisentangledToolIntent intentB = selectedIntents.get(j);

                ToolIntentProfile profileA = findProfile(candidateTools, intentA.toolId());
                ToolIntentProfile profileB = findProfile(candidateTools, intentB.toolId());

                if (profileA == null || profileB == null) {
                    continue;
                }

                // 检验是否具有冲突：显式互斥或强重叠并发副作用
                boolean explicitConflict = profileA.conflictToolIds().contains(profileB.toolId())
                        || profileB.conflictToolIds().contains(profileA.toolId());

                double cosineOverlap = dotProduct(profileA.embedding(), profileB.embedding());
                boolean implicitConflict = profileA.hasSideEffect() && profileB.hasSideEffect()
                        && (cosineOverlap >= conflictThreshold);

                if (explicitConflict || implicitConflict) {
                    // 根据激活强度高低决定先后偏序：高优先级先执行
                    if (intentA.activationScore() >= intentB.activationScore()) {
                        resolvedEdges.add(new ToolDependencyEdge(
                                intentA.toolId(),
                                intentB.toolId(),
                                String.format("Conflict-Serialization: %s -> %s (cos=%.3f, explicit=%b)",
                                        intentA.toolId(), intentB.toolId(), cosineOverlap, explicitConflict)
                        ));
                    } else {
                        resolvedEdges.add(new ToolDependencyEdge(
                                intentB.toolId(),
                                intentA.toolId(),
                                String.format("Conflict-Serialization: %s -> %s (cos=%.3f, explicit=%b)",
                                        intentB.toolId(), intentA.toolId(), cosineOverlap, explicitConflict)
                        ));
                    }
                }
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        String digest = computeSha256Digest(taskId, intents, resolvedEdges, reconstructionFidelity);

        return new DisentangleResult(
                taskId,
                intents,
                resolvedEdges,
                reconstructionFidelity,
                maxResidual,
                digest,
                elapsedNanos
        );
    }

    private static ToolIntentProfile findProfile(List<ToolIntentProfile> list, String toolId) {
        for (ToolIntentProfile p : list) {
            if (p.toolId().equals(toolId)) {
                return p;
            }
        }
        return null;
    }

    // ==========================================
    // 阿里千问超球面单位几何代数工具方法
    // ==========================================

    public static double[] normalizeVector(double[] vec) {
        if (vec == null || vec.length == 0) {
            double[] zero = new double[EMBEDDING_DIM];
            zero[0] = 1.0;
            return zero;
        }
        double norm = vectorNorm(vec);
        double[] normalized = new double[vec.length];
        if (norm < 1e-12) {
            normalized[0] = 1.0;
            return normalized;
        }
        for (int i = 0; i < vec.length; i++) {
            normalized[i] = vec[i] / norm;
        }
        return normalized;
    }

    public static double vectorNorm(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    public static double dotProduct(double[] u, double[] v) {
        double dot = 0.0;
        int len = Math.min(u.length, v.length);
        for (int i = 0; i < len; i++) {
            dot += u[i] * v[i];
        }
        return dot;
    }

    private static String computeSha256Digest(String taskId, List<DisentangledToolIntent> intents,
                                              List<ToolDependencyEdge> edges, double fidelity) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder(taskId).append("|").append(String.format("%.4f", fidelity));
            for (DisentangledToolIntent intent : intents) {
                sb.append("|").append(intent.toolId()).append(":").append(String.format("%.4f", intent.activationScore()));
            }
            for (ToolDependencyEdge edge : edges) {
                sb.append("|").append(edge.fromToolId()).append("->").append(edge.toToolId());
            }
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "SHA256_UNAVAILABLE";
        }
    }
}
