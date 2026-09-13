package tech.qiantong.qknow.hermes.eval;

import java.util.List;

/**
 * 主张忠实度评估器 (Claim Faithfulness Evaluator)
 * 实现 RAGAS 提出的主张分解算法 (Claim-Decomposition)。
 */
public class ClaimFaithfulnessEvaluator {

    /**
     * 评估主张的忠实度
     *
     * @param generatedText 生成的文本
     * @param context       参考上下文
     * @return 评估结果
     */
    public EvaluationResult evaluate(String generatedText, String context) {
        // 1. 将生成的文本拆解为多个独立的主张 (Claims)
        List<String> claims = decomposeIntoClaims(generatedText);
        if (claims == null || claims.isEmpty()) {
            return new EvaluationResult(0, 0, 0.0);
        }

        // 2. 逐一验证每个 Claim 是否在 context 中有支持
        int faithfulCount = 0;
        for (String claim : claims) {
            if (verifyClaimFaithfulness(claim, context)) {
                faithfulCount++;
            }
        }

        // 3. 计算忠实度分数：忠实 Claim 数量 / 总 Claim 数量
        double score = (double) faithfulCount / claims.size();
        return new EvaluationResult(claims.size(), faithfulCount, score);
    }

    /**
     * 构造拆解 Prompt 并调用 LLM 进行主张分解。
     * （在实际业务中会调用真实的大模型，测试时通过子类覆盖此方法进行 Mock）
     *
     * @param text 生成的原始文本
     * @return 拆解后的原子主张 (Atomic Claims) 列表
     */
    protected List<String> decomposeIntoClaims(String text) {
        String prompt = "Please decompose the following text into atomic claims:\n" + text;
        return callLlmForDecomposition(prompt);
    }

    /**
     * 构造验证 Prompt 并调用 LLM 验证 Claim 的忠实度。
     * （在实际业务中会调用真实的大模型，测试时通过子类覆盖此方法进行 Mock）
     *
     * @param claim   待验证的主张
     * @param context 提供的参考上下文
     * @return 该主张是否忠实于上下文
     */
    protected boolean verifyClaimFaithfulness(String claim, String context) {
        String prompt = String.format("Context: %s\nClaim: %s\nIs the claim fully supported by the context? Answer true or false.", context, claim);
        return callLlmForVerification(prompt);
    }

    /**
     * 内部方法：调用 LLM 进行拆解
     */
    protected List<String> callLlmForDecomposition(String prompt) {
        throw new UnsupportedOperationException("真实 LLM 拆解调用尚未实现");
    }

    /**
     * 内部方法：调用 LLM 进行验证
     */
    protected boolean callLlmForVerification(String prompt) {
        throw new UnsupportedOperationException("真实 LLM 验证调用尚未实现");
    }

    /**
     * 评估结果数据类
     */
    public static class EvaluationResult {
        private final int totalClaims;
        private final int faithfulClaims;
        private final double faithfulnessScore;

        public EvaluationResult(int totalClaims, int faithfulClaims, double faithfulnessScore) {
            this.totalClaims = totalClaims;
            this.faithfulClaims = faithfulClaims;
            this.faithfulnessScore = faithfulnessScore;
        }

        public int getTotalClaims() {
            return totalClaims;
        }

        public int getFaithfulClaims() {
            return faithfulClaims;
        }

        public double getFaithfulnessScore() {
            return faithfulnessScore;
        }
    }
}
