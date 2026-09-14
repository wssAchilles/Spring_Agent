package tech.qiantong.qknow.ai.audit.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 安全护栏态势感知聚合指标视图模型 (GuardrailMetricsVO)
 *
 * 承载大屏监控仪表盘所需的脱敏率、违规拦截率、PII 实体识别分布及 24 小时时序态势。
 *
 * @param totalRequests 审计请求总量
 * @param sanitizedCount PII 算法脱敏总量
 * @param blockedCount 对抗越狱/敏感红线阻断总量
 * @param redactionRate 综合脱敏拦截率 [0.0, 1.0]
 * @param averageFaithfulness 事实忠实度均值 [0.0, 1.0]
 * @param piiTypeDistribution PII 实体类型频次分布
 * @param hourlyRiskEvents 最近 24 小时风险事件时序序列
 * @param timestamp 聚合统计时间戳 (毫秒)
 *
 * @author qknow
 */
public record GuardrailMetricsVO(
        long totalRequests,
        long sanitizedCount,
        long blockedCount,
        double redactionRate,
        double averageFaithfulness,
        Map<String, Long> piiTypeDistribution,
        List<HourlyRiskPoint> hourlyRiskEvents,
        long timestamp
) {

    public record HourlyRiskPoint(
            String hourLabel,
            long interceptionCount,
            double averageFaithfulness
    ) {}

    public static GuardrailMetricsVO empty() {
        return new GuardrailMetricsVO(
                0L,
                0L,
                0L,
                0.0,
                1.0,
                Collections.emptyMap(),
                Collections.emptyList(),
                System.currentTimeMillis()
        );
    }
}
