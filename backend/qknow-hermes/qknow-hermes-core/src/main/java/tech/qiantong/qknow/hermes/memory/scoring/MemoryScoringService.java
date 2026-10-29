package tech.qiantong.qknow.hermes.memory.scoring;

import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.List;

/**
 * 记忆多维协同评分与去噪重排服务契约
 * 覆盖：
 * 1. 配置维度一致的超球面余弦度量与 Sigmoid 校准
 * 2. 动态艾宾浩斯时序衰减方程推导
 * 3. 三维协同评分 S(m, q) = \alpha*Recency + \beta*Importance + \gamma*Relevance
 * 4. 激活保护引理 (Lemma 1.1)
 * 5. MMR 多样性重排
 */
public interface MemoryScoringService {

    /**
     * 计算同维嵌入向量余弦相似度 [-1.0, 1.0]
     */
    double computeCosineSimilarity(float[] vecA, float[] vecB);

    /**
     * Sigmoid 温度平滑校准 (0.0, 1.0)
     */
    double calibrateSigmoid(double rawScore, double temperature);

    /**
     * 计算记忆的新近度 (Recency)，应用动态艾宾浩斯强化衰减方程
     * \lambda(k) = \lambda_0 / (1 + \eta * ln(1 + k))
     * Recency = exp(-\lambda(k) * \Delta t)
     */
    double computeRecency(MemoryNode node, long nowTimestamp);

    /**
     * 计算三维协同检索得分 S(m, q)，包含激活保护引理 (Lemma 1.1)
     */
    double computeCompositeScore(MemoryNode node, float[] queryEmbedding, long nowTimestamp);

    /**
     * 基于 MMR (Maximal Marginal Relevance) 执行多样性去重排序
     */
    List<MemoryNode> rerankByMMR(List<MemoryNode> candidates, float[] queryEmbedding, int topK, double lambdaMMR, long nowTimestamp);

    /**
     * 使用非支配前沿和拥挤距离重排记忆候选；嵌入长度由当前模型配置决定。
     */
    default List<MemoryNode> rerankByParetoFrontier(List<MemoryNode> candidates, float[] queryEmbedding, int topK, long nowTimestamp) {
        return rerankByMMR(candidates, queryEmbedding, topK, 0.70, nowTimestamp);
    }
}
