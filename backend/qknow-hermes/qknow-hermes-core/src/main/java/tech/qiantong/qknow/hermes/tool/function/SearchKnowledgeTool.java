package tech.qiantong.qknow.hermes.tool.function;

import org.springframework.ai.chat.model.ToolContext;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalProbeReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalRetrievalReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.CausalEvolutionStep;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 思考链内动态知识检索与海马体多跳子图探针工具（Retrieval-as-Reasoning / IRCoT）。
 * <p>
 * 彻底根治旧版无视 {@code query.getQuery()} 永远复读首轮静态预检索结果的缺陷：
 * 1. 当绑定 {@link BiTemporalHippocampalGraphEngine} 或自定义动态回调时，对模型在思考链中途生成的动态子查询
 *    执行识别记忆过滤 + 稠密-稀疏二分图 PPR 多跳检索，并附带持久错题本防错警示；
 * 2. 即使在仅传入静态预检索上下文的兼容模式下，亦使用千问 1024 维局部敏感投影按动态子查询对候选段落重排裁剪。
 * </p>
 */
public class SearchKnowledgeTool implements BiFunction<knowledgeQuery, ToolContext, String> {

    private static final int EMBEDDING_DIM = 1024;

    private final String knowledgeId;
    private final String knowledgeName;
    private final String preRetrievedContent;
    private final Function<String, String> dynamicSubqueryDelegate;

    public SearchKnowledgeTool(String knowledgeId, String knowledgeName, String preRetrievedContent) {
        this(knowledgeId, knowledgeName, preRetrievedContent, null);
    }

    public SearchKnowledgeTool(
            String knowledgeId,
            String knowledgeName,
            String preRetrievedContent,
            Function<String, String> dynamicSubqueryDelegate
    ) {
        this.knowledgeId = knowledgeId;
        this.knowledgeName = knowledgeName;
        this.preRetrievedContent = preRetrievedContent;
        this.dynamicSubqueryDelegate = dynamicSubqueryDelegate;
    }

    /**
     * 绑定双时态海马体记忆超图引擎构造支持思考链内 IRCoT 多跳检索的工具实例。
     */
    public static SearchKnowledgeTool withHippocampalEngine(
            String knowledgeId,
            String knowledgeName,
            String fallbackPreRetrieved,
            BiTemporalHippocampalGraphEngine engine,
            long validTimeEpochMs,
            long txTimeEpochMs
    ) {
        Objects.requireNonNull(engine, "BiTemporalHippocampalGraphEngine 不能为空");
        return new SearchKnowledgeTool(knowledgeId, knowledgeName, fallbackPreRetrieved, dynamicSubQuery -> {
            float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(dynamicSubQuery, EMBEDDING_DIM);
            HippocampalProbeReceipt receipt = engine.probeIterativeSubgraph(
                    "ircot_" + UUID.randomUUID().toString().substring(0, 8),
                    dynamicSubQuery,
                    qVec,
                    validTimeEpochMs,
                    txTimeEpochMs,
                    3
            );
            if (receipt.recalledPassageContents().isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【双时态海马体多跳检索结果 | 子查询: ").append(dynamicSubQuery).append("】\n");
            for (int i = 0; i < receipt.recalledPassageContents().size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(receipt.recalledPassageContents().get(i)).append('\n');
            }
            if (!receipt.recalledErrorBookWarnings().isEmpty()) {
                sb.append("【持久错题本防错约束】\n");
                for (String warning : receipt.recalledErrorBookWarnings()) {
                    sb.append("- ").append(warning).append('\n');
                }
            }
            return sb.toString().trim();
        });
    }

    /**
     * 绑定 Phase 8.1 双时态因果演化检索器（真实千问向量 + 确定性时态锚点解析 + Kahn 因果拓扑序，消除硬编码 nowMs 时间盲视）。
     */
    public static SearchKnowledgeTool withBiTemporalCausalRetriever(
            String knowledgeId,
            String knowledgeName,
            String fallbackPreRetrieved,
            BiTemporalCausalEvolutionRetriever retriever
    ) {
        Objects.requireNonNull(retriever, "BiTemporalCausalEvolutionRetriever 不能为空");
        return new SearchKnowledgeTool(knowledgeId, knowledgeName, fallbackPreRetrieved, dynamicSubQuery -> {
            BiTemporalCausalRetrievalReceipt receipt = retriever.retrieve(
                    "btcr_" + UUID.randomUUID().toString().substring(0, 8),
                    dynamicSubQuery,
                    System.currentTimeMillis(),
                    4
            );
            if (receipt.orderedCausalChain().isEmpty() && receipt.pprReceipt().recalledPassageContents().isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【双时态因果演化检索 | 模式: ")
                    .append(receipt.anchorSpec().mode().name())
                    .append(" | 子查询: ").append(dynamicSubQuery).append("】\n");
            if (!receipt.orderedCausalChain().isEmpty()) {
                sb.append("[有序因果事实链]\n");
                for (CausalEvolutionStep step : receipt.orderedCausalChain()) {
                    sb.append(step.stepRank()).append(". ").append(step.propositionText()).append('\n');
                }
            }
            if (!receipt.pprReceipt().recalledPassageContents().isEmpty()) {
                sb.append("[海马体段落佐证]\n");
                for (int i = 0; i < receipt.pprReceipt().recalledPassageContents().size(); i++) {
                    sb.append("[").append(i + 1).append("] ")
                            .append(receipt.pprReceipt().recalledPassageContents().get(i)).append('\n');
                }
            }
            if (!receipt.recalledErrorBookWarnings().isEmpty()) {
                sb.append("【持久错题本防错约束】\n");
                for (String warning : receipt.recalledErrorBookWarnings()) {
                    sb.append("- ").append(warning).append('\n');
                }
            }
            return sb.toString().trim();
        });
    }

    @Override
    public String apply(knowledgeQuery query, ToolContext toolContext) {
        String subQuery = (query != null && query.getQuery() != null) ? query.getQuery().trim() : "";

        // 1. 优先执行动态多跳检索回调
        if (!subQuery.isEmpty() && dynamicSubqueryDelegate != null) {
            String dynamicResult = dynamicSubqueryDelegate.apply(subQuery);
            if (dynamicResult != null && !dynamicResult.isBlank()) {
                return dynamicResult;
            }
        }

        // 2. 兼容路径：若仅有预检索上下文，按动态 subQuery 对段落进行相关性重排而非盲目全盘复读
        if (preRetrievedContent == null || preRetrievedContent.isEmpty()) {
            return "知识库 [" + knowledgeName + "] 中没有找到 \"" + subQuery + "\" 的相关信息!";
        }
        if (subQuery.isEmpty()) {
            return "根据知识库检索结果：\n" + preRetrievedContent;
        }

        String[] blocks = preRetrievedContent.split("\\n\\n+");
        if (blocks.length <= 1) {
            return "根据知识库检索结果：\n" + preRetrievedContent;
        }
        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(subQuery, EMBEDDING_DIM);
        List<Map.Entry<String, Double>> scored = new ArrayList<>();
        for (String block : blocks) {
            if (!block.isBlank()) {
                float[] bVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(block, EMBEDDING_DIM);
                double dot = 0.0;
                for (int i = 0; i < EMBEDDING_DIM; i++) {
                    dot += (double) qVec[i] * bVec[i];
                }
                scored.add(Map.entry(block.trim(), dot));
            }
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder ranked = new StringBuilder("根据知识库针对子查询 [" + subQuery + "] 的聚焦检索结果：\n");
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            ranked.append(scored.get(i).getKey()).append("\n\n");
        }
        return ranked.toString().trim();
    }
}
