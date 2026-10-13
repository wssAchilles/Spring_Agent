package tech.qiantong.qknow.hermes.agent.governor;

import tech.qiantong.qknow.hermes.agent.governor.dto.ShapleyCreditAllocationReceipt;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 基于合作博弈论的蒙特卡洛夏普利边际价值分配器契约接口 (ShapleyCreditAllocator)
 * 形式化满足四大博弈论公理（有效性、对称性、虚拟人公理、可加性），实现多智能体协作链条的公理化价值归因。
 */
public interface ShapleyCreditAllocator {

    /**
     * 计算多智能体协作联盟的夏普利边际贡献值
     *
     * @param sessionId 协作会话 ID
     * @param agentIds 参与协作的所有智能体 ID 列表
     * @param characteristicFunction 特征函数 v(S): 输入参与子集 S，返回该子集协作综合收益 [0.0, 100.0]
     * @param monteCarloPermutations 蒙特卡洛抽样排列数 (推荐 M = 20~30)
     * @return 包含各智能体公理化归因权重与签名验真的不可变凭单
     */
    ShapleyCreditAllocationReceipt allocateCredits(
            String sessionId,
            List<String> agentIds,
            ToDoubleFunction<List<String>> characteristicFunction,
            int monteCarloPermutations
    );
}
