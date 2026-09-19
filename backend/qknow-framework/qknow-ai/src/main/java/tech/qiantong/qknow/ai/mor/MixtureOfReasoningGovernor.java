package tech.qiantong.qknow.ai.mor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.mor.cache.CognitiveScaffold;
import tech.qiantong.qknow.ai.mor.cache.CoTCognitiveCacheService;
import tech.qiantong.qknow.ai.mor.model.ReasoningDecision;

import java.util.List;
import java.util.Optional;

/**
 * 自适应思考调控中枢 (Adaptive Thinking Governor / Mixture-of-Reasoning Governor)
 * 判定多目标帕累托选路: FAST_FLASH vs FLASH_WITH_SCAFFOLD vs DEEP_THINKING (定理 1)
 * 唯一生成主干 deepseek-flash 参数化思考动态控制
 */
@Service
public class MixtureOfReasoningGovernor {

    private static final Logger log = LoggerFactory.getLogger(MixtureOfReasoningGovernor.class);

    private final CoTCognitiveCacheService cognitiveCacheService;

    // 推荐生产权重: w1=0.40 (语义复杂度), w2=0.35 (检索缺口), w3=0.25 (冲突因子)
    private static final double W1 = 0.40;
    private static final double W2 = 0.35;
    private static final double W3 = 0.25;

    public MixtureOfReasoningGovernor(CoTCognitiveCacheService cognitiveCacheService) {
        this.cognitiveCacheService = cognitiveCacheService;
    }

    /**
     * 评估选路判定主入口
     */
    public ReasoningDecision evaluateRoute(
            String userQuery,
            float[] queryEmbedding1536,
            List<String> recalledSliceSignatures,
            double meanRecallScore,
            boolean hasTemporalConflict,
            boolean isCragAmbiguous
    ) {
        // 1. 语义复杂度判定 C_semantic
        double semanticComplexity = calculateSemanticComplexity(userQuery);

        // 2. RAG 上下文置信度缺口 (1 - Conf_rag)
        double confidenceGap = 1.0 - Math.max(0.0, Math.min(1.0, meanRecallScore));

        // 3. 冲突与歧义因子 Δ_conflict
        double conflictFactor = 0.0;
        if (hasTemporalConflict) {
            conflictFactor = Math.max(conflictFactor, 1.0);
        }
        if (isCragAmbiguous) {
            conflictFactor = Math.max(conflictFactor, 0.85);
        }

        // 4. 三维判定得分 Φ(Q)
        double compositeScore = W1 * semanticComplexity + W2 * confidenceGap + W3 * conflictFactor;

        log.info("[Adaptive Governor] 评估完成: Φ(Q)={}, C_sem={}, 1-Conf={}, Δ_conf={}",
                String.format("%.3f", compositeScore),
                String.format("%.3f", semanticComplexity),
                String.format("%.3f", confidenceGap),
                String.format("%.3f", conflictFactor));

        // 5. 分支判断逻辑
        // 分支 A: 若命中认知脚手架且具有中度以上复杂度或存在冲突/不确定性，以 Flash 挂载脚手架极速复用品质 (thinking: disabled)
        if (queryEmbedding1536 != null && queryEmbedding1536.length == 1536) {
            Optional<CognitiveScaffold> cachedScaffold = cognitiveCacheService.getScaffold(queryEmbedding1536, recalledSliceSignatures);
            if (cachedScaffold.isPresent() && (compositeScore >= 0.15 || semanticComplexity >= 0.15 || conflictFactor > 0.0)) {
                return new ReasoningDecision(
                        ReasoningDecision.RoutingBranch.FLASH_WITH_SCAFFOLD,
                        compositeScore, semanticComplexity, 1.0 - confidenceGap, conflictFactor,
                        cachedScaffold.get().scaffoldContent(),
                        "命中权威决策树脚手架，以 deepseek-flash 极速模式直出推演品质",
                        null
                );
            }
        }

        // 分支 B: 复杂度极低且语义本身简单且无显著冲突 -> FAST_FLASH 直出 (thinking: disabled)
        if (compositeScore < 0.35 && semanticComplexity < 0.35 && conflictFactor < 0.50) {
            return new ReasoningDecision(
                    ReasoningDecision.RoutingBranch.FAST_FLASH,
                    compositeScore, semanticComplexity, 1.0 - confidenceGap, conflictFactor,
                    null,
                    "查询意图简单直接且检索高置信，走 deepseek-flash 毫秒级直出",
                    null
            );
        }

        // 分支 C: 复杂逻辑、冲突严重或歧义高且未命中脚手架 -> DEEP_THINKING (thinking: enabled, 动态 effort)
        String effort = "medium";
        if (conflictFactor >= 0.85 || compositeScore >= 0.85) {
            effort = "high";
        } else if (compositeScore < 0.65) {
            effort = "low";
        }

        return new ReasoningDecision(
                ReasoningDecision.RoutingBranch.DEEP_THINKING,
                compositeScore, semanticComplexity, 1.0 - confidenceGap, conflictFactor,
                null,
                "复杂因果/时态冲突/未命中脚手架，激活 deepseek-flash 深度长思考链推演",
                effort
        );
    }

    /**
     * 计算输入 Query 语义复杂度 C_semantic [0, 1]
     */
    private double calculateSemanticComplexity(String query) {
        if (query == null || query.isBlank()) {
            return 0.0;
        }
        int len = query.length();
        double score = 0.0;

        // 1. 长度启发式基础分
        if (len > 30) score += 0.10;
        if (len > 60) score += 0.10;
        if (len > 120) score += 0.10;

        // 2. 深度推理关键词匹配
        String[] deepKeywords = {"为什么", "推导", "因果", "为什么说", "矛盾", "权衡", "区别", "比较", "证明", "反事实", "优化", "溯源", "架构", "一致性", "分布式", "原理", "原因", "分析"};
        int matched = 0;
        for (String kw : deepKeywords) {
            if (query.contains(kw)) {
                matched++;
            }
        }
        score += Math.min(0.35, matched * 0.10);

        // 3. 结构化关键词与代码块
        if (query.contains("select") || query.contains("SELECT") || query.contains("where") || query.contains("WHERE") ||
                query.contains("{") || query.contains("```") || query.contains("class ") || query.contains("def ")) {
            score += 0.25;
        }

        // 4. 多条件连接词
        if (query.contains("如果") || query.contains("并且") || query.contains("但是") ||
                query.contains("或者") || query.contains("然而") || query.contains("由于")) {
            score += 0.15;
        }

        return Math.min(1.0, score);
    }

    /**
     * 构建 Flash 快速直出规整 Prompt (对齐 64-token 前缀边界，提升 KV 前缀复用率)
     */
    public String buildFastFlashOptimizedPrompt(String systemPrompt, String userQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("[System: QKnow-V3-Fast] 64-token-aligned-prefix: ");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt).append("\n");
        }
        sb.append("User Query: ").append(userQuery);
        return sb.toString();
    }

    /**
     * 兼容别名方法 (用于历史调用与旧契约测试)
     */
    public String buildFastV3OptimizedPrompt(String systemPrompt, String userQuery) {
        return buildFastFlashOptimizedPrompt(systemPrompt, userQuery);
    }

    /**
     * 构建挂载认知脚手架的增强 Prompt (注入 200~400 字决策树)
     */
    public String buildScaffoldAugmentedPrompt(String systemPrompt, String scaffold, String userQuery) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt).append("\n\n");
        }
        sb.append("[Cognitive Scaffold: 权威因果推演结构化决策树]\n");
        sb.append(scaffold != null ? scaffold : "").append("\n\n");
        sb.append("请依据上述权威认知脚手架指引，直接组织紧凑权威的最终解答：\n");
        sb.append(userQuery);
        return sb.toString();
    }
}
