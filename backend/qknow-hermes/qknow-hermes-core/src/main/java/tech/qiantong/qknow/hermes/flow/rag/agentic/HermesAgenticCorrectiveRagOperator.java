package tech.qiantong.qknow.hermes.flow.rag.agentic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hermes 工作流主动自纠错自反思检索算子 (Agentic Corrective-RAG Operator)
 * 对初排召回质量进行三态研判 (RELEVANT / AMBIGUOUS / IRRELEVANT)，在置信度不足时主动触发查询重写或外部降级。
 */
public class HermesAgenticCorrectiveRagOperator {

    private static final Logger log = LoggerFactory.getLogger(HermesAgenticCorrectiveRagOperator.class);

    public enum RetrievalQualityGrade {
        RELEVANT,       // 充分相关，直接送入生成通道
        AMBIGUOUS,      // 模糊不清，触发多跳拓扑扩展与反思重写
        IRRELEVANT      // 完全无关，触发查询智能泛化重写或外部工具降级
    }

    public record RagInspectionDecision(
            RetrievalQualityGrade grade,
            double confidenceScore,
            String rewriteQueryOrRationale,
            List<String> finalEvidenceChunks
    ) {}

    /**
     * 评估检索质量并做出自反思决策
     *
     * @param queryText        用户查询
     * @param candidateChunks  初排召回切片
     * @param maxCosSimilarity 最高余弦相似度得分
     * @return 决策凭据
     */
    public RagInspectionDecision evaluateAndReflect(
            String queryText,
            List<String> candidateChunks,
            double maxCosSimilarity
    ) {
        if (candidateChunks == null || candidateChunks.isEmpty() || maxCosSimilarity < 0.35) {
            log.warn("[AgenticCRAG] 检索质量极低 (IRRELEVANT): query={}, maxCos={}", queryText, maxCosSimilarity);
            String rewrittenQuery = rewriteQueryForBroadening(queryText);
            return new RagInspectionDecision(
                    RetrievalQualityGrade.IRRELEVANT,
                    maxCosSimilarity,
                    rewrittenQuery,
                    List.of()
            );
        }

        if (maxCosSimilarity >= 0.70) {
            log.info("[AgenticCRAG] 检索质量充分相关 (RELEVANT): query={}, maxCos={}", queryText, maxCosSimilarity);
            return new RagInspectionDecision(
                    RetrievalQualityGrade.RELEVANT,
                    maxCosSimilarity,
                    "直接采纳高相关切片",
                    candidateChunks
            );
        }

        // 介于 0.35 到 0.70 之间判定为 AMBIGUOUS
        log.info("[AgenticCRAG] 检索质量置信度模糊 (AMBIGUOUS): query={}, maxCos={}", queryText, maxCosSimilarity);
        String enrichedQuery = enrichQueryWithKeywords(queryText);
        return new RagInspectionDecision(
                RetrievalQualityGrade.AMBIGUOUS,
                maxCosSimilarity,
                enrichedQuery,
                candidateChunks
        );
    }

    private String rewriteQueryForBroadening(String query) {
        return "泛化重写: " + query.replaceAll("[？?！!，,。.]", " ").trim();
    }

    private String enrichQueryWithKeywords(String query) {
        return "扩展拓扑搜索: " + query + " 核心背景与关联实体";
    }
}
