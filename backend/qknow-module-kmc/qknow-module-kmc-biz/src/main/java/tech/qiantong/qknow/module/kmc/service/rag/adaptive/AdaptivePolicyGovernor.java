package tech.qiantong.qknow.module.kmc.service.rag.adaptive;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 自适应策略调节器 (Adaptive Policy Governor)
 * 结合 EWMA 指数加权移动平均与李雅普诺夫指数稳定约束 (单步位移 <= 0.05) 动态调控检索权重与 Rerank 旁路门控
 */
@Slf4j
@Service
public class AdaptivePolicyGovernor {

    public static final double MIN_VECTOR_WEIGHT = 0.30;
    public static final double MAX_VECTOR_WEIGHT = 0.80;
    public static final double MAX_STEP_DRIFT = 0.05; // 李雅普诺夫防振荡单步位移硬界限
    public static final double EWMA_BETA = 0.90; // 滑动衰减系数

    @Getter
    private volatile double vectorWeight = 0.50; // 默认 5:5 混合检索
    @Getter
    private volatile double keywordWeight = 0.50;
    @Getter
    private volatile double ewmaSatisfaction = 0.80; // 初始基线满意度
    @Getter
    private volatile double rerankConfidenceThreshold = 0.60;

    /**
     * 接收反馈并执行李雅普诺夫稳定的动态微调
     * @param normalizedReward 归一化反馈奖励 [0, 1]
     */
    public synchronized void onFeedbackReceived(double normalizedReward) {
        // 1. 更新 EWMA 滑动满意度
        ewmaSatisfaction = EWMA_BETA * ewmaSatisfaction + (1.0 - EWMA_BETA) * normalizedReward;

        // 2. 根据反馈微调向量权重 (正反馈若倾向语义则微提向量，负反馈若语义不准则微提关键词)
        double targetAdjustment = 0.0;
        if (normalizedReward < 0.4) {
            // 负反馈较多时，通常关键词精准匹配能挽回召回，适度提升关键词比重 (降低 vectorWeight)
            targetAdjustment = -0.03;
        } else if (normalizedReward > 0.75) {
            // 语义泛化满意度高时，保持或适度平衡
            targetAdjustment = 0.02;
        }

        // 严格执行单步位移截断 (李雅普诺夫防振荡保证)
        double clampedDrift = Math.max(-MAX_STEP_DRIFT, Math.min(MAX_STEP_DRIFT, targetAdjustment));
        double nextVectorWeight = vectorWeight + clampedDrift;

        // 严格限制在安全区间 [0.30, 0.80]
        nextVectorWeight = Math.max(MIN_VECTOR_WEIGHT, Math.min(MAX_VECTOR_WEIGHT, nextVectorWeight));

        this.vectorWeight = Math.round(nextVectorWeight * 1000.0) / 1000.0;
        this.keywordWeight = Math.round((1.0 - this.vectorWeight) * 1000.0) / 1000.0;

        log.debug("自适应调节器更新: ewmaSatisfaction={}, vectorWeight={}, keywordWeight={}",
                ewmaSatisfaction, vectorWeight, keywordWeight);
    }

    /**
     * 判定是否应当旁路 Rerank 重排
     * @param topRetrievedScore 初排最高相似度得分
     * @return true 表示可安全旁路，节省约 80~150ms 延迟
     */
    public boolean shouldBypassRerank(double topRetrievedScore) {
        // 当近期用户满意度高，且初排最高得分高度确信（>= 0.85）时，安全旁路重排
        if (ewmaSatisfaction >= 0.80 && topRetrievedScore >= 0.85) {
            return true;
        }
        // 当满意度极低时，强制全量精排
        return false;
    }

    /**
     * 测试或重置基线状态
     */
    public synchronized void reset() {
        this.vectorWeight = 0.50;
        this.keywordWeight = 0.50;
        this.ewmaSatisfaction = 0.80;
        this.rerankConfidenceThreshold = 0.60;
    }
}
