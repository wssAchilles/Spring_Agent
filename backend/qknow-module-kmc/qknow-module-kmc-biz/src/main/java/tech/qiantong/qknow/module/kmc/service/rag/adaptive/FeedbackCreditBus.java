package tech.qiantong.qknow.module.kmc.service.rag.adaptive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多模态用户反馈信用分配总线 (基于逆倾向得分加权 IPS 消除展示位置偏差)
 */
@Slf4j
@Service
public class FeedbackCreditBus {

    /**
     * 计算特定反馈类型的原始效用分值 ([-1.0, 1.0])
     */
    public double computeBaseUtility(String feedbackType, Long dwellTimeMs) {
        if (feedbackType == null) {
            return 0.0;
        }
        switch (feedbackType.toUpperCase()) {
            case "UPVOTE":
                return 1.0;
            case "DOWNVOTE":
                return -0.8;
            case "COPY":
                return 0.6;
            case "CORRECTION":
                return -0.5;
            case "DWELL":
                if (dwellTimeMs == null || dwellTimeMs < 3000) {
                    return -0.2; // 快速跳过/未仔细阅读
                } else if (dwellTimeMs <= 60000) {
                    // 3s ~ 60s 深度阅读正向收益
                    return Math.min(0.5, 0.2 + (dwellTimeMs - 3000.0) / 100000.0);
                } else {
                    return 0.2; // 放置挂起
                }
            default:
                return 0.0;
        }
    }

    /**
     * 基于位置偏差模型 PBM 计算逆倾向加权权重 w_IPS = 1 / p(k) = sqrt(k)
     * @param positionIdx 文档展示排序位次 (从 1 开始)
     */
    public double computeIpsWeight(Integer positionIdx) {
        if (positionIdx == null || positionIdx <= 1) {
            return 1.0;
        }
        // 位置概率 p(k) = 1 / sqrt(k) => 逆倾向权重 = sqrt(k)
        // 限制最大权重为 3.0，杜绝长尾过大方差
        return Math.min(3.0, Math.sqrt(positionIdx));
    }

    /**
     * 结合多模态反馈与 IPS 计算最终无偏归一化奖励值 [0, 1]
     */
    public double calculateNormalizedReward(String feedbackType, Long dwellTimeMs, Integer positionIdx) {
        double baseUtility = computeBaseUtility(feedbackType, dwellTimeMs);
        double ipsWeight = computeIpsWeight(positionIdx);

        // 加权效用
        double weightedUtility = baseUtility * ipsWeight;

        // 截断到 [-1.0, 1.0]
        double clamped = Math.max(-1.0, Math.min(1.0, weightedUtility));

        // 映射到 [0, 1] 供 LinUCB 使用
        return (clamped + 1.0) / 2.0;
    }
}
