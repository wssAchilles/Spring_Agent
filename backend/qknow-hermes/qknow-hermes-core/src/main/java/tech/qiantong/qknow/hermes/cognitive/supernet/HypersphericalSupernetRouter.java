package tech.qiantong.qknow.hermes.cognitive.supernet;

import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.memory.bitemporal.NativeHippocampalSimdBridge;

import java.util.*;

/**
 * 超球面流形 {@code S^1023} 上的 Meta-MDP 认知超网路由器（对应博士论文第 3 章核心架构）。
 * <p>
 * 数学机制设计：
 * 1. 基于千问 1024 维单位球面 {@code S^1023} 的 von Mises-Fisher (vMF) 方向核注意力：
 *    调用 {@link NativeHippocampalSimdBridge#computeVmfKernelWeights} 计算策略原型与查询向量的方向相关性；
 * 2. 基于 Normal-Inverse-Gamma (NIG) 在线共轭经验贝叶斯 Thompson Sampling：
 *    在动作空间 {@code O_pool × {NONE, LOW, MEDIUM, HIGH}} 中结合 vMF 注意力先验平衡探索与利用；
 * 3. 非平稳证据平方弦漂移重规划门控：
 *    计算 {@code Delta_drift(t) = 2 * (1 - <z_t, z_{t-1}>)}，当超限时指数衰减旧统计量并按需重规划（Theorem 3.1）；
 * 4. 早退算子与 DeepSeek 思考模式官方参数合规下发。
 * </p>
 */
public class HypersphericalSupernetRouter {

    public static final int EMBEDDING_DIM = 1024;
    public static final float DEFAULT_VMF_KAPPA = 8.0f;
    public static final double DEFAULT_DRIFT_REPLAN_THRESHOLD = 0.35;
    public static final double DEFAULT_EARLY_EXIT_CONFIDENCE = 0.95;
    private static final double NON_STATIONARY_DISCOUNT_GAMMA = 0.25;
    private static final double MIN_SETTLED_UTILITY = 0.70;
    private static final int WARMUP_STEPS_AFTER_REPLAN = 4;

    private record StrategyArm(SupernetTopologyOperator operator, DeepSeekReasoningEffort effort) {
        String key() {
            return operator.name() + "::" + effort.name();
        }
    }

    private static final class NigPosteriorState {
        final StrategyArm arm;
        float[] prototypeUnit1024;
        double mu0;
        double lambda;
        double alpha;
        double beta;
        long totalSelections;
        double cumulativeObservedUtility;

        NigPosteriorState(StrategyArm arm, float[] initialPrototype) {
            this.arm = arm;
            this.prototypeUnit1024 = initialPrototype;
            this.mu0 = 0.65;
            this.lambda = 1.0;
            this.alpha = 2.0;
            this.beta = 0.15;
        }

        synchronized void discountForDrift(double gamma) {
            double g = Math.max(0.05, Math.min(1.0, gamma));
            this.lambda = Math.max(1.0, this.lambda * g);
            this.alpha = Math.max(2.0, 2.0 + (this.alpha - 2.0) * g);
            this.beta = Math.max(0.08, 0.12 + (this.beta - 0.12) * g);
        }

        synchronized void updateObservation(float[] query1024, double observedUtility) {
            double u = Math.max(0.0, Math.min(1.0, observedUtility));
            double oldMu = this.mu0;
            double oldLambda = this.lambda;
            double newLambda = oldLambda + 1.0;
            double newMu = (oldLambda * oldMu + u) / newLambda;
            double delta = u - oldMu;
            this.alpha += 0.5;
            this.beta += 0.5 * (oldLambda / newLambda) * delta * delta;
            this.lambda = newLambda;
            this.mu0 = newMu;
            this.totalSelections++;
            this.cumulativeObservedUtility += u;

            // 高收益观测时沿 S^1023 切向温和牵引策略原型质心（超球面自演化）
            if (query1024 != null && query1024.length == EMBEDDING_DIM && u >= MIN_SETTLED_UTILITY) {
                double eta = 0.15 * u;
                float[] updated = new float[EMBEDDING_DIM];
                double normSq = 0.0;
                for (int i = 0; i < EMBEDDING_DIM; i++) {
                    double v = prototypeUnit1024[i] + eta * query1024[i];
                    updated[i] = (float) v;
                    normSq += v * v;
                }
                double norm = Math.sqrt(normSq);
                if (norm > 1e-9) {
                    for (int i = 0; i < EMBEDDING_DIM; i++) {
                        updated[i] = (float) (updated[i] / norm);
                    }
                    this.prototypeUnit1024 = updated;
                }
            }
        }

        synchronized double sampleThompsonValue(Random rng) {
            double invVar = sampleGamma(alpha, rng) / Math.max(1e-6, beta);
            double stdDev = Math.sqrt(1.0 / Math.max(1e-6, lambda * invVar));
            return mu0 + stdDev * rng.nextGaussian();
        }
    }

    public record TelemetrySnapshot(
            long totalRoutingDecisions,
            long totalReplansTriggered,
            long totalEarlyExits,
            double lastChordDriftSquared,
            String activeTopologyOperator,
            String activeReasoningEffort,
            double averageExpectedUtility
    ) {}

    private final String hmacSecretKey;
    private final Random rng;
    private final List<NigPosteriorState> arms;
    private final Map<String, NigPosteriorState> armIndex;

    private StrategyArm activeStickyArm;
    private int stepsSinceLastReplan = 0;
    private double lastObservedUtility = 1.0;
    private double lastChordDriftSquared = 0.0;
    private double lastExpectedUtility = 0.65;
    private long totalRoutingDecisions = 0L;
    private long totalReplansTriggered = 0L;
    private long totalEarlyExits = 0L;

    public HypersphericalSupernetRouter() {
        this("hermes-supernet-default-secret", System.currentTimeMillis());
    }

    public HypersphericalSupernetRouter(String hmacSecretKey, long randomSeed) {
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-supernet-default-secret"
                : hmacSecretKey;
        this.rng = new Random(randomSeed);
        this.arms = buildInitialArms();
        this.armIndex = new LinkedHashMap<>();
        for (NigPosteriorState state : arms) {
            armIndex.put(state.arm.key(), state);
        }
        this.activeStickyArm = arms.get(0).arm;
    }

    private static final Map<SupernetTopologyOperator, String> OP_ANCHORS = Map.of(
            SupernetTopologyOperator.DIRECT_FAST, "简单 查询 定义 直答 lookup simple single fact direct answer who what when where",
            SupernetTopologyOperator.SINGLE_REACT, "单步 两跳 工具 检索 知识库 问答 核验 two-hop question passage retrieval tool search lookup",
            SupernetTopologyOperator.PLAN_SOLVE, "多跳 复杂 推理 长对话 时态 表格 统计 相关系数 3-hop 4-hop multi-hop reasoning temporal table calculation correlation plan solve",
            SupernetTopologyOperator.ADVERSARIAL_DEBATE, "多方 博弈 风险 矛盾 冲突 辩论 合规 复核 conflict contradiction risk high-risk audit debate",
            SupernetTopologyOperator.SWARM_PARALLEL, "大规模 并行 协同 拆解 蜂群 汇总 parallel multi-entity decomposition synthesis swarm",
            SupernetTopologyOperator.CODE_ACT_SANDBOX, "编程 代码 沙盒 数据分析 表格 统计 聚合 计算 脚本 验证 python math codeact sandbox aggregation table chart",
            SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE, "多跳 并行 子图 桥接 拆解 上下文隔离 map-reduce 3-hop 4-hop bridge entity parallel subgraph decomposition",
            SupernetTopologyOperator.CAUSAL_TREE_SEARCH, "因果 反思 回溯 分叉 树搜索 矛盾 纠偏 错题 深度验证 causal tree search reflection backtrack fork recovery"
    );

    private static String buildAnchorText(SupernetTopologyOperator op, DeepSeekReasoningEffort effort) {
        String effortDesc = switch (effort) {
            case NONE -> "effort_none fast direct concise lookup";
            case LOW -> "effort_low brief verification simple two-hop";
            case MEDIUM -> "effort_medium multi-step analytical reasoning evidence synthesis";
            case HIGH -> "effort_high deep multi-hop 3-hop 4-hop temporal math correlation verification";
        };
        return OP_ANCHORS.getOrDefault(op, op.name()) + " " + effortDesc;
    }

    private static List<NigPosteriorState> buildInitialArms() {
        List<NigPosteriorState> list = new ArrayList<>();
        for (SupernetTopologyOperator op : List.of(
                SupernetTopologyOperator.DIRECT_FAST,
                SupernetTopologyOperator.SINGLE_REACT,
                SupernetTopologyOperator.PLAN_SOLVE,
                SupernetTopologyOperator.ADVERSARIAL_DEBATE,
                SupernetTopologyOperator.SWARM_PARALLEL,
                SupernetTopologyOperator.CODE_ACT_SANDBOX,
                SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE,
                SupernetTopologyOperator.CAUSAL_TREE_SEARCH
        )) {
            for (DeepSeekReasoningEffort effort : DeepSeekReasoningEffort.values()) {
                // 跳过语义冲突极端组合以保持紧凑算子空间（例如蜂群/代码/子图/因果树禁止配 NONE，直答不配 HIGH）
                if (op == SupernetTopologyOperator.DIRECT_FAST && effort == DeepSeekReasoningEffort.HIGH) {
                    continue;
                }
                if (op == SupernetTopologyOperator.SWARM_PARALLEL && effort == DeepSeekReasoningEffort.NONE) {
                    continue;
                }
                if (op == SupernetTopologyOperator.CODE_ACT_SANDBOX && effort == DeepSeekReasoningEffort.NONE) {
                    continue;
                }
                if (op == SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE && effort == DeepSeekReasoningEffort.NONE) {
                    continue;
                }
                if (op == SupernetTopologyOperator.CAUSAL_TREE_SEARCH && effort == DeepSeekReasoningEffort.NONE) {
                    continue;
                }
                String anchorText = buildAnchorText(op, effort);
                float[] proto = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(anchorText, EMBEDDING_DIM);
                list.add(new NigPosteriorState(new StrategyArm(op, effort), proto));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 将全部策略臂原型校准到真实 {@code text-embedding-v4} {@code S^1023} 语义空间，消除随机哈希冷启动正交失配。
     */
    public synchronized void calibrateArmPrototypes(java.util.function.Function<String, float[]> unit1024Encoder) {
        if (unit1024Encoder == null) {
            return;
        }
        for (NigPosteriorState state : arms) {
            String anchorText = buildAnchorText(state.arm.operator(), state.arm.effort());
            float[] encoded = unit1024Encoder.apply(anchorText);
            if (encoded != null && encoded.length == EMBEDDING_DIM) {
                state.prototypeUnit1024 = normalizeOrFallback(encoded);
            }
        }
    }

    /**
     * 在超球面流形上执行 Meta-MDP 认知策略路由与按需漂移重规划。
     */
    public synchronized SupernetRoutingReceipt routeAndAdapt(
            String requestId,
            int stepIndex,
            float[] queryUnit1024,
            float[] previousEvidenceUnit1024,
            float[] currentEvidenceUnit1024,
            double evidenceConfidence
    ) {
        String safeReqId = (requestId == null || requestId.isBlank()) ? "req-supernet" : requestId;
        float[] safeQuery = normalizeOrFallback(queryUnit1024);

        // 1. 早退算子判别：认知证据置信度已饱和 (>= 0.95) 时立即早退，显著压降期望计算深度
        if (evidenceConfidence >= DEFAULT_EARLY_EXIT_CONFIDENCE) {
            double driftSq = computeSquaredChordDrift(previousEvidenceUnit1024, currentEvidenceUnit1024);
            this.lastChordDriftSquared = driftSq;
            this.totalRoutingDecisions++;
            this.totalEarlyExits++;
            return SupernetRoutingReceipt.createSigned(
                    safeReqId, stepIndex,
                    SupernetTopologyOperator.EARLY_EXIT, DeepSeekReasoningEffort.NONE,
                    driftSq, false, true, evidenceConfidence, 0.0,
                    System.currentTimeMillis(), hmacSecretKey
            );
        }

        // 2. 计算证据质心平方弦漂移 Delta_drift(t) = 2 * (1 - <z_t, z_{t-1}>)
        double driftSq = computeSquaredChordDrift(previousEvidenceUnit1024, currentEvidenceUnit1024);
        boolean evidenceDrifted = (previousEvidenceUnit1024 != null && currentEvidenceUnit1024 != null)
                && driftSq > DEFAULT_DRIFT_REPLAN_THRESHOLD;
        boolean replanTriggered = (stepIndex == 0) || evidenceDrifted;

        if (evidenceDrifted) {
            for (NigPosteriorState state : arms) {
                state.discountForDrift(NON_STATIONARY_DISCOUNT_GAMMA);
            }
            this.stepsSinceLastReplan = 0;
            this.totalReplansTriggered++;
        } else if (stepIndex == 0) {
            this.stepsSinceLastReplan = 0;
            this.totalReplansTriggered++;
        } else {
            this.stepsSinceLastReplan++;
        }

        // 3. 计算 S^1023 上全部策略原型的 vMF 核注意力权重与注意力熵
        float[] flatCentroids = new float[arms.size() * EMBEDDING_DIM];
        for (int m = 0; m < arms.size(); m++) {
            System.arraycopy(arms.get(m).prototypeUnit1024, 0, flatCentroids, m * EMBEDDING_DIM, EMBEDDING_DIM);
        }
        float[] vmfRaw = NativeHippocampalSimdBridge.computeVmfKernelWeights(
                safeQuery, flatCentroids, arms.size(), EMBEDDING_DIM, DEFAULT_VMF_KAPPA
        );

        double sumVmf = 0.0;
        for (float v : vmfRaw) {
            sumVmf += Math.max(1e-9, v);
        }
        double[] attentionWeights = new double[arms.size()];
        double attentionEntropy = 0.0;
        for (int m = 0; m < arms.size(); m++) {
            attentionWeights[m] = Math.max(1e-9, vmfRaw[m]) / sumVmf;
            attentionEntropy -= attentionWeights[m] * Math.log(attentionWeights[m]);
        }

        // 4. 在需要重规划、处于重规划后探索窗口、或上步收益低于收敛阈值时重新执行 Thompson Sampling 选路
        StrategyArm chosenArm = activeStickyArm;
        double chosenExpectedUtility = lastExpectedUtility;
        boolean needSample = replanTriggered
                || stepsSinceLastReplan <= WARMUP_STEPS_AFTER_REPLAN
                || lastObservedUtility < MIN_SETTLED_UTILITY
                || activeStickyArm == null;

        if (needSample) {
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestIdx = 0;
            for (int m = 0; m < arms.size(); m++) {
                NigPosteriorState st = arms.get(m);
                double tsMean = st.sampleThompsonValue(rng);
                // 联合效用：经验贝叶斯收益后验 + 超球面 vMF 语义亲和先验 - 适度计算能耗惩罚
                double vmfPriorBonus = 0.55 * (attentionWeights[m] * arms.size() - 1.0);
                double costPenalty = 0.22 * (st.arm.operator().normalizedComputeCost() + st.arm.effort().normalizedCost());
                double score = tsMean + vmfPriorBonus - costPenalty;
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = m;
                }
            }
            chosenArm = arms.get(bestIdx).arm;
            chosenExpectedUtility = Math.max(0.0, Math.min(1.0, arms.get(bestIdx).mu0));
            this.activeStickyArm = chosenArm;
        }

        this.lastChordDriftSquared = driftSq;
        this.lastExpectedUtility = chosenExpectedUtility;
        this.totalRoutingDecisions++;

        return SupernetRoutingReceipt.createSigned(
                safeReqId,
                stepIndex,
                chosenArm.operator(),
                chosenArm.effort(),
                driftSq,
                replanTriggered,
                false,
                chosenExpectedUtility,
                attentionEntropy,
                System.currentTimeMillis(),
                hmacSecretKey
        );
    }

    /**
     * 向特定策略臂反馈真实观测效用 {@code u_t in [0, 1]}，实时更新 NIG 共轭后验与超球面质心。
     */
    public synchronized void updatePosterior(
            SupernetTopologyOperator operator,
            DeepSeekReasoningEffort effort,
            float[] queryUnit1024,
            double observedUtility
    ) {
        if (operator == null || effort == null) {
            return;
        }
        String key = operator.name() + "::" + effort.name();
        NigPosteriorState state = armIndex.get(key);
        if (state != null) {
            state.updateObservation(normalizeOrFallback(queryUnit1024), observedUtility);
            this.lastObservedUtility = Math.max(0.0, Math.min(1.0, observedUtility));
        }
    }

    /**
     * 根据路由凭单构造严格符合 DeepSeek 最新官方规范的 {@link DeepSeekChatOptions}。
     */
    public DeepSeekChatOptions buildDeepSeekChatOptions(SupernetRoutingReceipt receipt, String modelName) {
        Objects.requireNonNull(receipt, "receipt 不能为空");
        String targetModel = (modelName == null || modelName.isBlank()) ? "deepseek-flash" : modelName;
        DeepSeekReasoningEffort effort = receipt.reasoningEffort();

        if (!effort.thinkingEnabled()) {
            return DeepSeekChatOptions.builder()
                    .model(targetModel)
                    .thinkingEnabled(Boolean.FALSE)
                    .reasoningEffort(null)
                    .temperature(0.2)
                    .topP(0.95)
                    .build();
        }
        // 开启思考努力度时：thinkingEnabled=true, reasoning_effort=low/medium/high, temperature=null, topP=0.95
        return DeepSeekChatOptions.builder()
                .model(targetModel)
                .thinkingEnabled(Boolean.TRUE)
                .reasoningEffort(effort.wireValue())
                .temperature(null)
                .topP(0.95)
                .build();
    }

    /**
     * 获取指定拓扑算子与思考深度策略臂当前的 {@code S^1023} 原型只读快照。
     */
    public synchronized float[] getPrototypeSnapshot(
            SupernetTopologyOperator operator,
            DeepSeekReasoningEffort effort
    ) {
        if (operator == null || effort == null) {
            return null;
        }
        NigPosteriorState state = armIndex.get(new StrategyArm(operator, effort).key());
        if (state == null || state.prototypeUnit1024 == null) {
            return null;
        }
        return Arrays.copyOf(state.prototypeUnit1024, state.prototypeUnit1024.length);
    }

    /**
     * 在 {@code S^1023} 黎曼切空间上执行 RHO 回顾性自我偏好梯度更新并重新执行 L2 单位归一化。
     */
    public synchronized boolean applyRiemannianPreferenceStep(
            SupernetTopologyOperator operator,
            DeepSeekReasoningEffort effort,
            float[] positiveAnchor1024,
            float[] negativeAnchor1024,
            double learningRate
    ) {
        if (operator == null || effort == null) {
            return false;
        }
        NigPosteriorState state = armIndex.get(new StrategyArm(operator, effort).key());
        if (state == null || state.prototypeUnit1024 == null) {
            return false;
        }
        double lr = Math.max(0.01, Math.min(0.5, learningRate));
        float[] proto = state.prototypeUnit1024;
        double[] grad = new double[EMBEDDING_DIM];
        double dotPos = 0.0;
        double dotNeg = 0.0;
        if (positiveAnchor1024 != null && positiveAnchor1024.length == EMBEDDING_DIM) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                dotPos += (double) proto[i] * positiveAnchor1024[i];
            }
        }
        if (negativeAnchor1024 != null && negativeAnchor1024.length == EMBEDDING_DIM) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                dotNeg += (double) proto[i] * negativeAnchor1024[i];
            }
        }
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            double g = 0.0;
            if (positiveAnchor1024 != null && positiveAnchor1024.length == EMBEDDING_DIM) {
                g += (positiveAnchor1024[i] - dotPos * proto[i]);
            }
            if (negativeAnchor1024 != null && negativeAnchor1024.length == EMBEDDING_DIM) {
                g -= 0.5 * (negativeAnchor1024[i] - dotNeg * proto[i]);
            }
            grad[i] = proto[i] + lr * g;
        }
        double normSq = 0.0;
        for (double v : grad) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        if (norm <= 1e-12) {
            return false;
        }
        float[] updated = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            updated[i] = (float) (grad[i] / norm);
        }
        state.prototypeUnit1024 = updated;
        return true;
    }

    public synchronized TelemetrySnapshot getTelemetrySnapshot() {
        return new TelemetrySnapshot(
                totalRoutingDecisions,
                totalReplansTriggered,
                totalEarlyExits,
                lastChordDriftSquared,
                activeStickyArm != null ? activeStickyArm.operator().name() : SupernetTopologyOperator.SINGLE_REACT.name(),
                activeStickyArm != null ? activeStickyArm.effort().name() : DeepSeekReasoningEffort.NONE.name(),
                lastExpectedUtility
        );
    }

    public static double computeSquaredChordDrift(float[] prevUnit, float[] currUnit) {
        if (prevUnit == null || currUnit == null || prevUnit.length == 0 || prevUnit.length != currUnit.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < prevUnit.length; i++) {
            dot += (double) prevUnit[i] * currUnit[i];
        }
        double clampedCos = Math.max(-1.0, Math.min(1.0, dot));
        return Math.max(0.0, 2.0 * (1.0 - clampedCos));
    }

    private static float[] normalizeOrFallback(float[] vec) {
        if (vec == null || vec.length != EMBEDDING_DIM) {
            return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("default_query", EMBEDDING_DIM);
        }
        double normSq = 0.0;
        for (float v : vec) {
            normSq += (double) v * v;
        }
        double norm = Math.sqrt(normSq);
        if (norm <= 1e-12) {
            return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("default_query", EMBEDDING_DIM);
        }
        if (Math.abs(norm - 1.0) < 1e-4) {
            return vec;
        }
        float[] out = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            out[i] = (float) (vec[i] / norm);
        }
        return out;
    }

    private static double sampleGamma(double shapeAlpha, Random rng) {
        if (shapeAlpha < 1.0) {
            double u = Math.max(1e-12, rng.nextDouble());
            return sampleGamma(shapeAlpha + 1.0, rng) * Math.pow(u, 1.0 / shapeAlpha);
        }
        double d = shapeAlpha - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        for (int guard = 0; guard < 128; guard++) {
            double x = rng.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0.0) {
                continue;
            }
            v = v * v * v;
            double u = Math.max(1e-12, rng.nextDouble());
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
        return Math.max(1e-4, d);
    }
}
