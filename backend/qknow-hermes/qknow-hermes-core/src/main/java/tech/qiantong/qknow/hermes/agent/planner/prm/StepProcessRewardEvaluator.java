package tech.qiantong.qknow.hermes.agent.planner.prm;

import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 细粒度步骤级过程奖励评估器 (Step-Level Process Reward Evaluator - PRM)
 * 遵循多源混合打分方程与阿里千问 1536 维超球面单位几何规范：
 * 1. 步骤切分协议：基于 DeepSeek 思考流换行与段落标记切分思维步，保障 64-Token 规整门禁；
 * 2. 混合过程奖励打分方程：
 *    r_step = 0.35 * s_contract + 0.25 * s_causal + 0.25 * s_cos + 0.15 * s_llm
 * 3. 门禁拦截机制：若 r_step >= 0.60 则签发 PASSED 凭单；若 r_step < 0.60 则签发 TRIGGER_BACKTRACK 凭单；
 * 4. 签发带 SHA-256 与常量时间自验真的纯 Java 21 Record 不可变凭单 StepProcessRewardReceipt。
 */
public class StepProcessRewardEvaluator {

    public static final double DEFAULT_PRM_THRESHOLD = 0.60;
    public static final int VECTOR_DIMENSION = 1536;

    // 多源混合打分权重契约
    public static final double WEIGHT_CONTRACT = 0.35;
    public static final double WEIGHT_CAUSAL = 0.25;
    public static final double WEIGHT_COSINE = 0.25;
    public static final double WEIGHT_LLM = 0.15;

    private static final Pattern STEP_DELIMITER_PATTERN = Pattern.compile("(?m)(?:^\\s*(?:步骤|Step|###|阶段|Task)\\s*\\d+[:：.]?\\s*|\\n\\n+)");

    private final double prmThreshold;
    private final String secretKey;

    private final AtomicInteger totalEvaluated = new AtomicInteger(0);
    private final AtomicInteger totalPassed = new AtomicInteger(0);
    private final AtomicInteger totalBacktrackTriggered = new AtomicInteger(0);

    public StepProcessRewardEvaluator() {
        this(DEFAULT_PRM_THRESHOLD, "HERMES_PRM_SECRET_" + System.currentTimeMillis());
    }

    public StepProcessRewardEvaluator(double prmThreshold, String secretKey) {
        this.prmThreshold = prmThreshold;
        this.secretKey = secretKey == null ? "HERMES_PRM_DEFAULT_SECRET" : secretKey;
    }

    /**
     * 将长思维链或思考流 (reasoning_content) 规整切分为离散步骤
     * 具备 64 字符/Token 规整门禁，避免碎片化孤立标记
     */
    public List<String> segmentSteps(String reasoningContent) {
        if (reasoningContent == null || reasoningContent.isBlank()) {
            return Collections.emptyList();
        }

        String[] rawChunks = STEP_DELIMITER_PATTERN.split(reasoningContent);
        List<String> normalizedSteps = new ArrayList<>();
        StringBuilder currentBuffer = new StringBuilder();

        for (String chunk : rawChunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (currentBuffer.length() > 0) {
                currentBuffer.append("\n");
            }
            currentBuffer.append(trimmed);

            // 64 字符规整门禁：若累积步长达到规范，则固化为独立步骤
            if (currentBuffer.length() >= 64) {
                normalizedSteps.add(currentBuffer.toString());
                currentBuffer.setLength(0);
            }
        }

        if (currentBuffer.length() > 0) {
            if (!normalizedSteps.isEmpty() && currentBuffer.length() < 32) {
                // 过小残余片段合并至前一步
                int lastIdx = normalizedSteps.size() - 1;
                normalizedSteps.set(lastIdx, normalizedSteps.get(lastIdx) + "\n" + currentBuffer);
            } else {
                normalizedSteps.add(currentBuffer.toString());
            }
        }

        return Collections.unmodifiableList(normalizedSteps);
    }

    /**
     * 执行单步过程奖励综合评估
     *
     * @param traceSessionId   链路会话 ID
     * @param stepIndex        步骤序号
     * @param stepContent      步骤推演内容
     * @param targetEmbedding  目标意图千问 1536 维超球面单位向量
     * @param stepEmbedding    当前步骤千问 1536 维超球面单位向量
     * @param contractValid    Hoare 逻辑前置契约合规标志
     * @param causalOrderValid Kahn 因果拓扑偏序对齐标志
     * @param overrideLlmScore 可选的 LLM 自省打分 (null 则触发轻量自省启发式估算)
     * @return 包含密码学自验真签名的 StepProcessRewardReceipt 不可变凭单
     */
    public StepProcessRewardReceipt evaluateStep(
            String traceSessionId,
            int stepIndex,
            String stepContent,
            float[] targetEmbedding,
            float[] stepEmbedding,
            boolean contractValid,
            boolean causalOrderValid,
            Double overrideLlmScore
    ) {
        long startTime = System.currentTimeMillis();
        totalEvaluated.incrementAndGet();

        // 1. Hoare 契约合规得分
        double sContract = contractValid ? 1.0 : 0.0;

        // 2. Kahn 因果拓扑偏序得分
        double sCausal = causalOrderValid ? 1.0 : 0.0;

        // 3. 千问 1536 维超球面余弦相似度得分
        double sCosine;
        float[] normalizedStepVec = null;
        if (targetEmbedding != null && stepEmbedding != null) {
            normalizedStepVec = ConfidenceGatedPrefetchFilter.validateAndNormalize(stepEmbedding);
            double cosSim = ConfidenceGatedPrefetchFilter.computeCosineSimilarity(targetEmbedding, normalizedStepVec);
            // 测地余弦投影到 [0.0, 1.0] 非负空间
            sCosine = Math.max(0.0, Math.min(1.0, cosSim));
        } else {
            sCosine = 0.50; // 缺省向量时设为中性平滑分
        }

        // 4. 轻量自省反思得分
        double sLlm;
        if (overrideLlmScore != null) {
            sLlm = Math.max(0.0, Math.min(1.0, overrideLlmScore));
        } else {
            sLlm = estimateIntrospectionScore(stepContent);
        }

        // 多源混合打分方程
        double rStep = WEIGHT_CONTRACT * sContract
                + WEIGHT_CAUSAL * sCausal
                + WEIGHT_COSINE * sCosine
                + WEIGHT_LLM * sLlm;

        rStep = Math.max(0.0, Math.min(1.0, rStep));

        // 门禁判定：r_step >= prmThreshold 为 PASSED，否则为 TRIGGER_BACKTRACK
        StepProcessRewardReceipt.StepEvaluationStatus status = (rStep >= prmThreshold)
                ? StepProcessRewardReceipt.StepEvaluationStatus.PASSED
                : StepProcessRewardReceipt.StepEvaluationStatus.TRIGGER_BACKTRACK;

        if (status == StepProcessRewardReceipt.StepEvaluationStatus.PASSED) {
            totalPassed.incrementAndGet();
        } else {
            totalBacktrackTriggered.incrementAndGet();
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        String receiptId = "prm_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        return StepProcessRewardReceipt.createSigned(
                receiptId,
                traceSessionId,
                stepIndex,
                stepContent,
                rStep,
                status,
                normalizedStepVec,
                latencyMs,
                secretKey
        );
    }

    /**
     * 轻量启发式语义自省分析
     * 识别反思、自相矛盾、报错放弃等关键词
     */
    private double estimateIntrospectionScore(String stepContent) {
        if (stepContent == null || stepContent.isBlank()) {
            return 0.50;
        }
        String lower = stepContent.toLowerCase(Locale.ROOT);
        // 发现显著反思否定或逻辑混乱词组，适度削减自省分
        if (lower.contains("不对") || lower.contains("错误") || lower.contains("重新思考")
                || lower.contains("矛盾") || lower.contains("error") || lower.contains("invalid")) {
            return 0.40;
        }
        // 逻辑清晰且具有明确推导结论
        if (lower.contains("因此") || lower.contains("所以") || lower.contains("结论")
                || lower.contains("verified") || lower.contains("confirmed")) {
            return 0.95;
        }
        return 0.85;
    }

    public double getPrmThreshold() {
        return prmThreshold;
    }

    public int getTotalEvaluated() {
        return totalEvaluated.get();
    }

    public int getTotalPassed() {
        return totalPassed.get();
    }

    public int getTotalBacktrackTriggered() {
        return totalBacktrackTriggered.get();
    }

    public void reset() {
        totalEvaluated.set(0);
        totalPassed.set(0);
        totalBacktrackTriggered.set(0);
    }
}
