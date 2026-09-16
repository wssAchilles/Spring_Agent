package tech.qiantong.qknow.hermes.cognitive.engine;

import tech.qiantong.qknow.hermes.cognitive.dto.AlignedMemoryItem;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 记忆流时序衰减与因果对齐器 (TemporalCognitiveMemoryAligner)
 * 遵循 Phase 84 定理 1.3 与命题 2.1：
 * 艾宾浩斯强化时间衰减 R(t) + 重要性激活保护 I(m) + 阿里千问 1536 维超球面测地余弦相关度 + 因果时钟遮蔽
 */
public class TemporalCognitiveMemoryAligner {

    private static final double ALPHA_RECENCY = 0.25;
    private static final double BETA_IMPORTANCE = 0.35;
    private static final double GAMMA_RELEVANCE = 0.40;
    private static final double CRITICAL_IMPORTANCE_FLOOR = 0.90;

    /**
     * 单步记忆流时序与因果对齐检索，耗时严格 <= 2ms
     *
     * @param candidates         候选记忆列表
     * @param queryEmbedding     当前查询的阿里千问 1536 维超球面归一化嵌入向量
     * @param currentTimestamp   当前物理时间戳毫秒
     * @param currentSessionId   当前会话 ID
     * @param currentCausalClock 当前因果时钟版本
     * @param topK               召回上限
     * @return 对齐后有效记忆项列表 (已过滤冲突遮蔽项)
     */
    public List<AlignedMemoryItem> alignAndRetrieve(
            List<MemoryNode> candidates,
            float[] queryEmbedding,
            long currentTimestamp,
            String currentSessionId,
            long currentCausalClock,
            int topK
    ) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        List<AlignedMemoryItem> alignedList = new ArrayList<>(candidates.size());

        for (MemoryNode node : candidates) {
            boolean isMasked = false;

            // 1. 因果时钟与作用域遮蔽校验 (Causal Masking)
            boolean isAction = false;
            String nodeSessionId = null;
            long nodeCausalClock = 0L;

            Map<String, Object> meta = node.getMetadata();
            if (meta != null) {
                Object actObj = meta.get("isActionNode");
                if (Boolean.TRUE.equals(actObj) || "true".equalsIgnoreCase(String.valueOf(actObj))) {
                    isAction = true;
                }
                Object actType = meta.get("actionType");
                if (actType != null && String.valueOf(actType).contains("ACTION")) {
                    isAction = true;
                }
                Object sessObj = meta.get("sessionId");
                if (sessObj != null) {
                    nodeSessionId = String.valueOf(sessObj);
                }
                Object clockObj = meta.get("causalClock");
                if (clockObj instanceof Number num) {
                    nodeCausalClock = num.longValue();
                }
            }

            // 若文本包含破坏性写动作关键字，同样具备副作用判定
            if (node.getContent() != null) {
                String c = node.getContent();
                if (c.contains("清空") || c.contains("删除") || c.contains("重置") || c.contains("DELETE") || c.contains("DROP")) {
                    isAction = true;
                }
            }

            // 若节点为具备副作用的操作节点且所属会话与当前不同，直接遮蔽，防止反向污染
            if (isAction && (nodeSessionId != null && !nodeSessionId.equals(currentSessionId))) {
                isMasked = true;
            }
            // 若历史操作时钟严重落后于当前时钟 (超过 50 个版本以上被判定为陈旧废弃覆盖)
            if (isAction && nodeCausalClock > 0 && nodeCausalClock < currentCausalClock - 50) {
                isMasked = true;
            }

            if (isMasked) {
                alignedList.add(new AlignedMemoryItem(
                        node.getId(), node.getContent(), 0.0, 0.0, node.getImportance(), 0.0, nodeCausalClock, true
                ));
                continue;
            }

            // 2. 艾宾浩斯强化时间半衰期衰减留存率 R(t) = exp(-delta_t / S_k)
            long createdTime = node.getTimestamp();
            double deltaDays = Math.max(0.0, (currentTimestamp - createdTime) / 86400000.0);
            int accessCount = Math.max(0, node.getAccessCount());
            double dynamicStrength = 7.0 * (1.0 + 0.20 * Math.log(1.0 + accessCount));
            double recency = Math.exp(-deltaDays / dynamicStrength);

            // 3. 阿里千问 1536 维超球面测地余弦内积
            double cosine = computeCosine(node.getEmbedding(), queryEmbedding);
            double relevance = Math.max(0.0, cosine);

            // 4. 重要性
            double importance = Math.max(0.0, Math.min(1.0, node.getImportance()));

            // 5. 综合效用得分 S(m, q) 与激活保护引理
            double score = ALPHA_RECENCY * recency + BETA_IMPORTANCE * importance + GAMMA_RELEVANCE * relevance;
            if (importance >= CRITICAL_IMPORTANCE_FLOOR) {
                score = Math.max(score, BETA_IMPORTANCE * CRITICAL_IMPORTANCE_FLOOR);
            }

            alignedList.add(new AlignedMemoryItem(
                    node.getId(), node.getContent(), score, recency, importance, relevance, nodeCausalClock, false
            ));
        }

        // 过滤被因果遮蔽的陈旧节点，按综合效用得分降序排列并截断 Top-K
        return alignedList.stream()
                .filter(it -> !it.isMasked())
                .sorted(Comparator.comparingDouble(AlignedMemoryItem::compositeScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 阿里千问 1536 维超球面单位向量内积计算
     */
    public double computeCosine(float[] vecA, float[] vecB) {
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
        return Math.max(-1.0, Math.min(1.0, dot / (Math.sqrt(normA) * Math.sqrt(normB))));
    }
}
