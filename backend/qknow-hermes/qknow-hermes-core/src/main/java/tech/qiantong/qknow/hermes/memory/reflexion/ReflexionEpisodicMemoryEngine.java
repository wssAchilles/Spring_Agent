package tech.qiantong.qknow.hermes.memory.reflexion;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 基于语言强化学习的自反思长程情景记忆引擎核心契约 (NeurIPS 2023 Reflexion 架构)
 * 实现跨试次、跨会话的历史失败轨迹反思提取、千问超球面向量化持久化与先验上下文注入
 *
 * @author Achilles
 * @since 2026-10-13
 */
public interface ReflexionEpisodicMemoryEngine {

    /**
     * 针对执行失败轨迹进行因果评估与自然语言反思提炼
     *
     * @param tenantId            租户标识 (多租户物理隔离)
     * @param agentRole           产生失败的智能体角色
     * @param taskIdentifier      当前任务标识符
     * @param trialSequence       试次编号
     * @param executionTrajectory 失败的完整动作与输出轨迹
     * @param environmentFeedback 环境或测试反馈的原始错误报文
     * @return 异步返回包含反思先验与 1536 维超球面向量的不可变凭单
     */
    CompletableFuture<ReflexionEpisodicMemoryReceipt> evaluateAndReflectAsync(
            String tenantId,
            String agentRole,
            String taskIdentifier,
            int trialSequence,
            String executionTrajectory,
            String environmentFeedback
    );

    /**
     * 在新任务发起时，基于当前任务意图的千问 1536 维超球面向量检索高相关反思先验
     *
     * @param tenantId            租户隔离域
     * @param queryVector1536     当前任务描述的千问 1536 维单位向量
     * @param topK                召回反思条数 (通常为 2~3 条)
     * @param minCosineThreshold  超球面余弦相似度下限 (默认 0.80)
     * @return 匹配的反思经验教训列表
     */
    List<ReflexionEpisodicMemoryReceipt> recallRelevantReflections(
            String tenantId,
            float[] queryVector1536,
            int topK,
            double minCosineThreshold
    );

    /**
     * 将召回的反思经验结构化格式化为 Prompt 先验注入文本 (Verbal Prior Context)
     *
     * @param relevantReflections 召回的反思记录
     * @return 格式化后的系统先验提示词块
     */
    String formatReflectionsAsPriorPrompt(List<ReflexionEpisodicMemoryReceipt> relevantReflections);
}
