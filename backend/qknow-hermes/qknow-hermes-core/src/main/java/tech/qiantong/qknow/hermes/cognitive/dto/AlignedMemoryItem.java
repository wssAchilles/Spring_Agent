package tech.qiantong.qknow.hermes.cognitive.dto;

/**
 * 记忆流时序衰减与因果对齐项
 * 遵循 Phase 84 定理 1.3
 *
 * @param memoryId        记忆唯一标识
 * @param content         记忆内容文本
 * @param compositeScore  综合效用检索得分 S(m, q)
 * @param recencyScore    艾宾浩斯强化衰减留存率 R(t)
 * @param importanceScore 认知重要性评分 I(m)
 * @param relevanceScore  阿里千问 1536 维超球面测地余弦相关度
 * @param causalClock     因果版本时钟 Happens-Before 标量
 * @param isMasked        是否被因果时序规则遮蔽剪除
 */
public record AlignedMemoryItem(
        String memoryId,
        String content,
        double compositeScore,
        double recencyScore,
        double importanceScore,
        double relevanceScore,
        long causalClock,
        boolean isMasked
) {}
