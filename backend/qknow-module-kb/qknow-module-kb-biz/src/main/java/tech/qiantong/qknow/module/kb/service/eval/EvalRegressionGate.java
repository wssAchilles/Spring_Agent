package tech.qiantong.qknow.module.kb.service.eval;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 离线评测与 Holdout 难例自动化防退化回归门禁
 * 负责在算法或配置变更后，对评测指标执行绝对红线与相对基准退化两级判定
 */
@Slf4j
@Service
public class EvalRegressionGate {

    // 评测指标绝对红线 (越大越好)
    private static final Map<String, Double> ABSOLUTE_THRESHOLDS = Map.of(
            "faithfulness", 0.85,
            "answer_relevance", 0.80,
            "context_recall", 0.80,
            "real_context_recall", 0.75,
            "real_mrr", 0.70
    );

    // 反向指标绝对红线 (越小越好，上限阈值)
    private static final Map<String, Double> MAX_ABSOLUTE_THRESHOLDS = Map.of(
            "zero_recall_rate", 0.05
    );

    // 允许的最大退化阈值 (2%)
    private static final double MAX_REGRESSION_TOLERANCE = 0.02;

    /**
     * 门禁决策判定实体
     */
    @Getter
    @Builder
    @ToString
    public static class GateDecision {
        private final boolean passed;
        private final String reason;
        private final Map<String, Object> details;
    }

    /**
     * 执行门禁评测判定
     *
     * @param currentMetrics  当前评测指标集合
     * @param baselineMetrics 历史基线指标集合 (可为空)
     * @return 门禁判定决策结果
     */
    public GateDecision evaluateGate(Map<String, Double> currentMetrics, Map<String, Double> baselineMetrics) {
        if (currentMetrics == null || currentMetrics.isEmpty()) {
            return GateDecision.builder()
                    .passed(false)
                    .reason("当前评测指标为空，门禁判定阻断")
                    .details(Collections.emptyMap())
                    .build();
        }

        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        // 1. 正向指标绝对红线判定 (不得低于阈值)
        for (Map.Entry<String, Double> entry : ABSOLUTE_THRESHOLDS.entrySet()) {
            String metricKey = entry.getKey();
            double threshold = entry.getValue();
            Double currentValue = currentMetrics.get(metricKey);

            if (currentValue != null) {
                details.put(metricKey + "_current", currentValue);
                details.put(metricKey + "_threshold", threshold);
                if (currentValue < threshold) {
                    String displayName = capitalize(metricKey);
                    violations.add(String.format("指标 %s 当前得分 %.4f 低于绝对红线 %.2f", displayName, currentValue, threshold));
                }
            }
        }

        // 2. 反向指标绝对红线判定 (不得高于阈值，例如零召回率)
        for (Map.Entry<String, Double> entry : MAX_ABSOLUTE_THRESHOLDS.entrySet()) {
            String metricKey = entry.getKey();
            double maxThreshold = entry.getValue();
            Double currentValue = currentMetrics.get(metricKey);

            if (currentValue != null) {
                details.put(metricKey + "_current", currentValue);
                details.put(metricKey + "_max_threshold", maxThreshold);
                if (currentValue > maxThreshold) {
                    String displayName = capitalize(metricKey);
                    violations.add(String.format("指标 %s 当前数值 %.4f 超过绝对红线 %.2f", displayName, currentValue, maxThreshold));
                }
            }
        }

        // 3. 相对基线退化判定
        if (baselineMetrics != null && !baselineMetrics.isEmpty()) {
            for (Map.Entry<String, Double> entry : currentMetrics.entrySet()) {
                String metricKey = entry.getKey();
                Double currentVal = entry.getValue();
                Double baselineVal = baselineMetrics.get(metricKey);

                if (currentVal != null && baselineVal != null) {
                    String displayName = capitalize(metricKey);
                    if (MAX_ABSOLUTE_THRESHOLDS.containsKey(metricKey)) {
                        // 反向指标：当前值大于基线值表示退化
                        double rise = currentVal - baselineVal;
                        double risePercent = baselineVal > 0 ? rise / baselineVal : (rise > 0 ? 1.0 : 0.0);
                        if (rise > MAX_REGRESSION_TOLERANCE || risePercent > MAX_REGRESSION_TOLERANCE) {
                            violations.add(String.format("指标 %s 相比 Baseline 退化超过 2%% (基准: %.4f, 当前: %.4f, 上升: %.2f%%)",
                                    displayName, baselineVal, currentVal, risePercent * 100));
                        }
                    } else if (baselineVal > 0) {
                        // 正向指标：当前值小于基线值表示退化
                        double drop = baselineVal - currentVal;
                        double dropPercent = drop / baselineVal;
                        if (drop > MAX_REGRESSION_TOLERANCE || dropPercent > MAX_REGRESSION_TOLERANCE) {
                            violations.add(String.format("指标 %s 相比 Baseline 退化超过 2%% (基准: %.4f, 当前: %.4f, 跌落: %.2f%%)",
                                    displayName, baselineVal, currentVal, dropPercent * 100));
                        }
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            String combinedReason = String.join("; ", violations);
            log.warn("[EvalRegressionGate] 评测门禁阻断: {}", combinedReason);
            return GateDecision.builder()
                    .passed(false)
                    .reason(combinedReason)
                    .details(details)
                    .build();
        }

        log.info("[EvalRegressionGate] 评测门禁通过，当前指标满足质量基线要求");
        return GateDecision.builder()
                .passed(true)
                .reason("评测指标全部达标且未发生退化")
                .details(details)
                .build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (str.contains("_")) {
            String[] parts = str.split("_");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.isEmpty()) {
                    sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                }
            }
            return sb.toString();
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
