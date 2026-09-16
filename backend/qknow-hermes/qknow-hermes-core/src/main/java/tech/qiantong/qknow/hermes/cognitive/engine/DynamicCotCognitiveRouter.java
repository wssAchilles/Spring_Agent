package tech.qiantong.qknow.hermes.cognitive.engine;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.cognitive.dto.CognitiveStrategy;

/**
 * 动态思维链认知路由决策器 (DynamicCotCognitiveRouter)
 * 遵循 Phase 84 定理 1.1：
 * 基于连续四维特征度量 C(q) = 0.15*L + 0.35*H + 0.30*D + 0.20*R 进行微秒级自适应三级分流
 */
@Slf4j
public class DynamicCotCognitiveRouter {

    public static final double THRESHOLD_DIRECT = 0.35;
    public static final double THRESHOLD_DEEP = 0.70;

    public record RoutingDecision(
            CognitiveStrategy strategy,
            double complexityScore,
            long latencyNanos,
            String reason
    ) {}

    /**
     * 微秒级认知策略评估路由，单步耗时严格 <= 50us
     *
     * @param userPrompt         用户原始输入
     * @param contextTokenLength 当前上下文预估 Token 数
     * @param registeredToolCount 已注册候选工具数
     * @return 路由决策记录
     */
    public RoutingDecision route(String userPrompt, int contextTokenLength, int registeredToolCount) {
        long startNs = System.nanoTime();

        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return new RoutingDecision(CognitiveStrategy.DIRECT_ANSWER, 0.0, System.nanoTime() - startNs, "空输入默认直出");
        }

        String trimmed = userPrompt.trim();
        int charLen = trimmed.length();

        // 1. 简易问候/常识问答极速探针 (O(1) 短路)
        if (charLen <= 12 && isGreetingOrSimple(trimmed)) {
            long latency = System.nanoTime() - startNs;
            return new RoutingDecision(CognitiveStrategy.DIRECT_ANSWER, 0.10, latency, "命中极简打招呼/常识问答探针");
        }

        // 2. 规范化长度分量 L(q) = tanh(length / 120.0)
        double lengthScore = Math.tanh(charLen / 120.0);

        // 3. 意图不确定性熵 H(q)
        double entropy = computePromptEntropy(trimmed);

        // 4. 多跳逻辑依赖拓扑度 D(q)
        double multiHop = computeMultiHopDegree(trimmed);

        // 5. 候选工具关联激活度 R(q)
        double toolRelevance = computeToolRelevance(trimmed, registeredToolCount);

        // 6. 综合复杂度评价方程 C(q) = 0.15*L + 0.35*H + 0.30*D + 0.20*R
        double complexity = 0.15 * lengthScore + 0.35 * entropy + 0.30 * multiHop + 0.20 * toolRelevance;
        complexity = Math.max(0.0, Math.min(1.0, complexity));

        CognitiveStrategy strategy;
        String reason;
        if (complexity < THRESHOLD_DIRECT) {
            strategy = CognitiveStrategy.DIRECT_ANSWER;
            reason = String.format("复杂度低 (%.4f < %.2f)，分流至 DIRECT_ANSWER 零思考链直出", complexity, THRESHOLD_DIRECT);
        } else if (complexity < THRESHOLD_DEEP) {
            strategy = CognitiveStrategy.SHORT_COT;
            reason = String.format("复杂度适中 (%.4f)，分流至 SHORT_COT 轻量启发思维链", complexity);
        } else {
            strategy = CognitiveStrategy.DEEP_REASONING;
            reason = String.format("复杂度高 (%.4f >= %.2f)，激活 DEEP_REASONING 深度展开推理树", complexity, THRESHOLD_DEEP);
        }

        long latencyNs = System.nanoTime() - startNs;
        return new RoutingDecision(strategy, complexity, latencyNs, reason);
    }

    private boolean isGreetingOrSimple(String prompt) {
        String p = prompt.toLowerCase();
        return p.contains("你好") || p.contains("您好") || p.contains("hello") || p.contains("hi")
                || p.contains("在吗") || p.contains("早上好") || p.contains("下午好") || p.contains("晚上好")
                || p.equals("谢谢") || p.equals("再见") || p.equals("ok") || p.equals("好的");
    }

    private double computePromptEntropy(String prompt) {
        int questionMarks = 0;
        int logicalConnectives = 0;
        for (char c : prompt.toCharArray()) {
            if (c == '?' || c == '？') questionMarks++;
        }
        if (prompt.contains("如果") || prompt.contains("并且") || prompt.contains("或者")
                || prompt.contains("对比") || prompt.contains("分析") || prompt.contains("原因")
                || prompt.contains("为什么") || prompt.contains("怎么") || prompt.contains("区别")) {
            logicalConnectives++;
        }
        double raw = (questionMarks * 0.35) + (logicalConnectives * 0.40);
        return Math.min(1.0, raw);
    }

    private double computeMultiHopDegree(String prompt) {
        int hopHints = 0;
        if (prompt.contains("第一步") || prompt.contains("首先") || prompt.contains("接着")
                || prompt.contains("然后") || prompt.contains("最后") || prompt.contains("再")) {
            hopHints++;
        }
        if (prompt.contains("结合") || prompt.contains("先") || prompt.contains("汇总")
                || prompt.contains("跨") || prompt.contains("综合")) {
            hopHints++;
        }
        if (prompt.contains("计算") || prompt.contains("统计") || prompt.contains("对比")) {
            hopHints++;
        }
        return Math.min(1.0, hopHints * 0.45);
    }

    private double computeToolRelevance(String prompt, int registeredToolCount) {
        if (registeredToolCount <= 0) return 0.0;
        int toolHints = 0;
        if (prompt.contains("查") || prompt.contains("搜索") || prompt.contains("检索")
                || prompt.contains("数据库") || prompt.contains("订单") || prompt.contains("接口")
                || prompt.contains("api") || prompt.contains("执行") || prompt.contains("获取")) {
            toolHints++;
        }
        return Math.min(1.0, toolHints * 0.60);
    }
}
