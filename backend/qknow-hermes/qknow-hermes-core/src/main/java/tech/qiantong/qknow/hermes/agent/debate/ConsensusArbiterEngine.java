package tech.qiantong.qknow.hermes.agent.debate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多智能体共识仲裁引擎接口
 * 组织有界轮次辩论并基于 DeepSeek 思考链思维推理生成终局仲裁凭单
 */
public interface ConsensusArbiterEngine {

    /**
     * 对存在争议的工蜂 Agent 产出组织有界轮次辩论并产出权威共识凭单
     *
     * @param question           原始用户提问
     * @param conflictingOutputs 各工蜂的初始分歧命题 (AgentName -> Output)
     * @param maxRounds          最大辩论轮次 (严格限制为 1 或 2，防止无界发散)
     * @return 包含思维链与仲裁裁决的只读不可变凭单
     */
    CompletableFuture<ConsensusArbitrationReceipt> arbitrateDebateAsync(
            String question,
            Map<String, String> conflictingOutputs,
            int maxRounds
    );
}
