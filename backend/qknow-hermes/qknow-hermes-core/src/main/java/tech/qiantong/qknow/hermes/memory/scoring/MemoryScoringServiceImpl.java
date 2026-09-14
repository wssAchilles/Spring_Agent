package tech.qiantong.qknow.hermes.memory.scoring;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆多维协同评分与去噪重排服务实现
 * 严格落实：
 * - 阿里千问 1536 维超球面归一化余弦相似度
 * - 动态艾宾浩斯强化衰减抑制 \lambda(k)
 * - 三维协同检索得分方程 S(m, q)
 * - 激活保护引理 (Lemma 1.1)
 * - MMR (Maximal Marginal Relevance) 多样性重排
 */
@Slf4j
public class MemoryScoringServiceImpl implements MemoryScoringService {

    // 权重配比: \alpha + \beta + \gamma = 1.0
    private static final double ALPHA_RECENCY = 0.25;
    private static final double BETA_IMPORTANCE = 0.35;
    private static final double GAMMA_RELEVANCE = 0.40;

    // 激活保护临界阈值 (Lemma 1.1)
    private static final double I_CRIT = 0.90;

    // 强化学习衰减平滑系数
    private static final double ETA_REINFORCE = 0.50;

    @Override
    public double computeCosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length == 0 || vecB.length == 0) {
            return 0.0;
        }
        int len = Math.min(vecA.length, vecB.length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        if (normA <= 1e-9 || normB <= 1e-9) {
            return 0.0;
        }
        double sim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        // 约束在 [-1.0, 1.0] 理论闭区间
        return Math.max(-1.0, Math.min(1.0, sim));
    }

    @Override
    public double calibrateSigmoid(double rawScore, double temperature) {
        double t = temperature > 0 ? temperature : 1.0;
        return 1.0 / (1.0 + Math.exp(-rawScore / t));
    }

    @Override
    public double computeRecency(MemoryNode node, long nowTimestamp) {
        if (node == null) {
            return 0.0;
        }
        long createdTime = node.getTimestamp();
        double deltaMillis = Math.max(0.0, (double) (nowTimestamp - createdTime));
        double deltaDays = deltaMillis / (1000.0 * 86400.0);

        // 动态艾宾浩斯强化衰减因子: \lambda(k) = \lambda_0 / (1 + \eta * ln(1 + k))
        int k = Math.max(0, node.getAccessCount());
        double lambda0 = node.getDecayRate() > 0 ? node.getDecayRate() : 0.05;
        double dynamicLambda = lambda0 / (1.0 + ETA_REINFORCE * Math.log(1.0 + k));

        return Math.exp(-dynamicLambda * deltaDays);
    }

    @Override
    public double computeCompositeScore(MemoryNode node, float[] queryEmbedding, long nowTimestamp) {
        if (node == null) {
            return 0.0;
        }

        double recency = computeRecency(node, nowTimestamp);
        double importance = Math.max(0.0, Math.min(1.0, node.getImportance()));

        double cosine = computeCosineSimilarity(node.getEmbedding(), queryEmbedding);
        // 相似度投影至 [0.0, 1.0]
        double relevance = Math.max(0.0, cosine);

        double rawScore = ALPHA_RECENCY * recency + BETA_IMPORTANCE * importance + GAMMA_RELEVANCE * relevance;

        // 引理 1.1: 激活保护机制 (Activation Protection Lemma)
        // 若 I_m >= I_crit，综合得分恒具有下界 S >= \beta * I_crit，永不因时间流逝沉没
        if (node.isCriticallyImportant(I_CRIT)) {
            double protectionFloor = BETA_IMPORTANCE * I_CRIT;
            rawScore = Math.max(rawScore, protectionFloor);
        }

        return Math.max(0.0, Math.min(1.0, rawScore));
    }

    @Override
    public List<MemoryNode> rerankByMMR(List<MemoryNode> candidates, float[] queryEmbedding, int topK, double lambdaMMR, long nowTimestamp) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }

        List<MemoryNode> remaining = new ArrayList<>(candidates);
        List<MemoryNode> selected = new ArrayList<>();
        int targetK = Math.min(topK, candidates.size());

        while (selected.size() < targetK && !remaining.isEmpty()) {
            MemoryNode bestCandidate = null;
            double bestMmrScore = -Double.MAX_VALUE;

            for (MemoryNode candidate : remaining) {
                double compScore = computeCompositeScore(candidate, queryEmbedding, nowTimestamp);

                // 计算与已选集合的最大冗余相似度
                double maxRedundancy = 0.0;
                for (MemoryNode sel : selected) {
                    double sim = computeCosineSimilarity(candidate.getEmbedding(), sel.getEmbedding());
                    if (sim > maxRedundancy) {
                        maxRedundancy = sim;
                    }
                }

                double mmr = lambdaMMR * compScore - (1.0 - lambdaMMR) * Math.max(0.0, maxRedundancy);
                if (mmr > bestMmrScore) {
                    bestMmrScore = mmr;
                    bestCandidate = candidate;
                }
            }

            if (bestCandidate != null) {
                selected.add(bestCandidate);
                remaining.remove(bestCandidate);
            } else {
                break;
            }
        }

        return selected;
    }
}
