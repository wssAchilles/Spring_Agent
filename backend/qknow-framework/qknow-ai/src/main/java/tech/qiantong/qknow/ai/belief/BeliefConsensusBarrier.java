package tech.qiantong.qknow.ai.belief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 信念共识安全屏障 (定理 1.3: 控制屏障函数 CBF 散度硬拦截与安全不变性)
 */
@Component
public class BeliefConsensusBarrier {

    private static final Logger log = LoggerFactory.getLogger(BeliefConsensusBarrier.class);

    // 最大容许散度限额 D*_max
    public static final double MAX_DIVERGENCE_LIMIT = 1.5;
    // 安全对齐回退降级动作
    public static final String ALIGNMENT_FALLBACK_ACTION = "ALIGNMENT_SYNC_FALLBACK";

    /**
     * 屏障检查结果
     */
    public record BarrierCheckResult(
            boolean barrierTriggered,
            String message,
            double safetyMargin
    ) {}

    /**
     * 判定控制屏障状态 h = D*_max - D_J
     */
    public BarrierCheckResult checkBarrier(double jeffreysDivergence) {
        double safetyMargin = MAX_DIVERGENCE_LIMIT - jeffreysDivergence;
        if (safetyMargin < 0.0) {
            log.warn("信念共识安全屏障触发拦截: 散度={}, 裕度={}",
                    String.format("%.4f", jeffreysDivergence), String.format("%.4f", safetyMargin));
            return new BarrierCheckResult(true, "跨层级信念分歧超过阈值 1.5，触发控制屏障硬拦截并请求自愈", safetyMargin);
        }
        return new BarrierCheckResult(false, "跨层级信念分歧在安全限额内，协同放行", safetyMargin);
    }

    /**
     * 控制屏障动作过滤：触发屏障时 100% 替换为对齐同步动作
     */
    public String filterAction(String proposedAction, boolean barrierTriggered) {
        if (barrierTriggered) {
            return ALIGNMENT_FALLBACK_ACTION;
        }
        return proposedAction;
    }
}
