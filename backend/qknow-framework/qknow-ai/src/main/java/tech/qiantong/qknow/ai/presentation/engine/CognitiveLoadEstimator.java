package tech.qiantong.qknow.ai.presentation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.presentation.dto.UserCognitiveStateDTO;
import tech.qiantong.qknow.ai.presentation.enums.CognitiveLoadLevel;

/**
 * 用户即时认知负荷估算引擎
 * 
 * 核心数学模型:
 * CL = 0.35 * Phi_len + 0.25 * Phi_cad + 0.20 * Phi_ent + 0.20 * Phi_urg + 0.05 * correction
 * 算法复杂度 O(1)，单次耗时严格 <= 1ms。
 */
@Component
public class CognitiveLoadEstimator {

    private static final Logger log = LoggerFactory.getLogger(CognitiveLoadEstimator.class);

    private static final double W_LEN = 0.35;
    private static final double W_CAD = 0.25;
    private static final double W_ENT = 0.20;
    private static final double W_URG = 0.20;

    /**
     * 计算连续认知负荷得分 [0.0, 1.0]
     */
    public double estimateCognitiveScore(UserCognitiveStateDTO state) {
        if (state == null) {
            return 0.50; // 默认中等负荷
        }

        // 1. 上下文长度因子: 4000 Token 饱和
        double phiLen = Math.min(1.0, Math.max(0.0, state.getContextTokenLength() / 4000.0));

        // 2. 停顿节奏因子: 停留时间越短/点击越急促，负荷越高 (tau_0 = 1000ms)
        double dwell = Math.max(0L, state.getDwellTimeMs());
        double phiCad = Math.exp(-dwell / 1000.0);

        // 3. 信息熵率因子
        double phiEnt = Math.min(1.0, Math.max(0.0, state.getInformationEntropy()));

        // 4. 业务场景紧急度
        double phiUrg = Math.min(1.0, Math.max(0.0, state.getUrgencyFactor()));

        // 5. 综合凸线性组合
        double score = W_LEN * phiLen + W_CAD * phiCad + W_ENT * phiEnt + W_URG * phiUrg;

        // 6. 纠错补偿
        if (state.getCorrectionCount() > 0) {
            score += Math.min(0.15, state.getCorrectionCount() * 0.05);
        }

        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * 将连续得分映射为四态认知负荷等级
     */
    public CognitiveLoadLevel mapToLevel(double score) {
        if (score < 0.35) {
            return CognitiveLoadLevel.LOW;
        } else if (score < 0.65) {
            return CognitiveLoadLevel.MEDIUM;
        } else if (score < 0.85) {
            return CognitiveLoadLevel.HIGH;
        } else {
            return CognitiveLoadLevel.CRITICAL;
        }
    }
}
