package tech.qiantong.qknow.ai.belief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分层贝叶斯意图推断器 (定理 1.1: 冯·米塞斯-费希尔似然与后验单调指数收敛)
 */
@Component
public class HierarchicalBayesianIntentInferer {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalBayesianIntentInferer.class);

    // 千问 1536 维超球面集中度参数 kappa
    public static final double KAPPA = 4.0;
    // 信念熵收敛阈值 ln(2) 约 0.6931
    public static final double ENTROPY_CONVERGENCE_THRESHOLD = Math.log(2.0);
    // 最小置信度阈值
    public static final double MIN_CONFIDENCE_THRESHOLD = 0.85;

    // 意图空间在千问 1536 维超球面上的中心锚点
    private final ConcurrentHashMap<String, float[]> intentAnchors = new ConcurrentHashMap<>();

    /**
     * 意图推断输出结果
     */
    public record InferenceResult(
            String bestIntent,
            double confidence,
            double entropy,
            boolean converged,
            Map<String, Double> posteriorDistribution
    ) {}

    public HierarchicalBayesianIntentInferer() {
        // 默认初始化三类典型协同意图及伪中心 (1536 维超球面正交特征占位)
        registerIntent("QUERY_EXPLAIN", generateNormalizedAnchor(1));
        registerIntent("TRANSFORM_EXECUTE", generateNormalizedAnchor(2));
        registerIntent("AUDIT_REFLECT", generateNormalizedAnchor(3));
    }

    public void registerIntent(String intentName, float[] anchor) {
        if (intentName != null && anchor != null && anchor.length == 1536) {
            intentAnchors.put(intentName, normalizeHypersphere(anchor));
        }
    }

    /**
     * 顺序贝叶斯推断多步观测证据后的意图后验分布 (定理 1.1)
     */
    public InferenceResult inferIntent(List<float[]> observationEmbeddings, Map<String, Double> initialPrior) {
        if (observationEmbeddings == null || observationEmbeddings.isEmpty()) {
            throw new IllegalArgumentException("观测证据序列不能为空");
        }

        List<String> intents = new ArrayList<>(intentAnchors.keySet());
        if (intents.isEmpty()) {
            throw new IllegalStateException("意图候选空间未注册");
        }

        // 1. 初始化先验分布
        Map<String, Double> posterior = new HashMap<>();
        double priorSum = 0.0;
        for (String intent : intents) {
            double p = (initialPrior != null && initialPrior.containsKey(intent))
                    ? Math.max(0.001, initialPrior.get(intent))
                    : 1.0 / intents.size();
            posterior.put(intent, p);
            priorSum += p;
        }
        for (String intent : intents) {
            posterior.put(intent, posterior.get(intent) / priorSum);
        }

        // 2. 依次吸收证据递推更新贝叶斯后验
        for (float[] obs : observationEmbeddings) {
            float[] normalizedObs = normalizeHypersphere(obs);
            double totalWeight = 0.0;
            Map<String, Double> unnormalized = new HashMap<>();

            for (String intent : intents) {
                float[] anchor = intentAnchors.get(intent);
                double cosineSim = computeDotProduct(normalizedObs, anchor);
                // vMF 似然模型: P(o|I) propto exp(kappa * cos_sim)
                double likelihood = Math.exp(KAPPA * cosineSim);
                double priorProb = posterior.get(intent);
                double val = likelihood * priorProb;
                unnormalized.put(intent, val);
                totalWeight += val;
            }

            // 归一化后验
            if (totalWeight > 0.0) {
                for (String intent : intents) {
                    posterior.put(intent, unnormalized.get(intent) / totalWeight);
                }
            }
        }

        // 3. 计算最佳意图、最大置信度与香农信念熵
        String bestIntent = intents.get(0);
        double maxProb = -1.0;
        double entropy = 0.0;

        for (String intent : intents) {
            double p = posterior.get(intent);
            if (p > maxProb) {
                maxProb = p;
                bestIntent = intent;
            }
            if (p > 1e-9) {
                entropy -= p * Math.log(p);
            }
        }

        boolean converged = (entropy <= ENTROPY_CONVERGENCE_THRESHOLD) && (maxProb >= MIN_CONFIDENCE_THRESHOLD);

        log.debug("贝叶斯意图推断完成: bestIntent={}, conf={}, entropy={}, converged={}",
                bestIntent, String.format("%.4f", maxProb), String.format("%.4f", entropy), converged);

        return new InferenceResult(bestIntent, maxProb, entropy, converged, Collections.unmodifiableMap(posterior));
    }

    private static float[] normalizeHypersphere(float[] v) {
        float[] norm = new float[v.length];
        double sumSq = 0.0;
        for (float x : v) sumSq += x * x;
        double mag = Math.sqrt(Math.max(1e-12, sumSq));
        for (int i = 0; i < v.length; i++) norm[i] = (float) (v[i] / mag);
        return norm;
    }

    private static double computeDotProduct(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    public static float[] generateNormalizedAnchor(int seed) {
        float[] v = new float[1536];
        Random rnd = new Random(seed * 7919L);
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) rnd.nextGaussian();
        }
        return normalizeHypersphere(v);
    }
}
