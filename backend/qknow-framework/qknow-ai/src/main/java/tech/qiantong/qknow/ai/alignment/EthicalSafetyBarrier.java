package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态伦理控制屏障函数与超平面正交投影拉回器 (定理 1.2 & 1.3)
 */
@Component
public class EthicalSafetyBarrier {

    private static final Logger log = LoggerFactory.getLogger(EthicalSafetyBarrier.class);

    private final ConstitutionalRuleBook ruleBook;

    public EthicalSafetyBarrier(ConstitutionalRuleBook ruleBook) {
        this.ruleBook = ruleBook;
    }

    public record BarrierDecision(
            boolean allowed,
            boolean modifiedByProjection,
            String verdictCode,
            Map<String, Object> sanitizedParams,
            double safetyMargin,
            String violationDetail
    ) {}

    /**
     * 执行全方位安全屏障检测与参数正交修补
     */
    public BarrierDecision inspectAndFilter(
            String actionName,
            Map<String, Object> parameters,
            GoalDriftDetector.DriftEvaluation drift
    ) {
        // 1. 宪政原则硬红线核验
        Optional<ConstitutionalRuleBook.ConstitutionalPrinciple> violationOpt = ruleBook.checkViolation(actionName, parameters);
        if (violationOpt.isPresent()) {
            ConstitutionalRuleBook.ConstitutionalPrinciple v = violationOpt.get();
            if (v.tier() == ConstitutionalRuleBook.PrincipleTier.ABSOLUTE_REDLINE) {
                return new BarrierDecision(
                        false,
                        false,
                        "CRITICAL_BLOCKED_REDLINE",
                        parameters != null ? parameters : Map.of(),
                        0.0,
                        "Violated redline principle: " + v.id() + " (" + v.name() + ")"
                );
            }
        }

        // 2. 目标严重漂移拦截
        if (drift != null && drift.status() == GoalDriftDetector.DriftStatus.SEVERE_DRIFT) {
            return new BarrierDecision(
                    false,
                    false,
                    "BLOCKED_SEVERE_GOAL_DRIFT",
                    parameters != null ? parameters : Map.of(),
                    0.10,
                    "Goal drift below acceptable threshold: cosine=" + drift.cosineSimilarity()
            );
        }

        // 3. 中度偏离或次级合规问题：正交超平面投影修补 (Safe Projection)
        boolean needsProjection = (drift != null && drift.status() == GoalDriftDetector.DriftStatus.MODERATE_DRIFT)
                || (violationOpt.isPresent() && violationOpt.get().tier() == ConstitutionalRuleBook.PrincipleTier.BUSINESS_COMPLIANCE);

        if (needsProjection) {
            Map<String, Object> safeParams = new HashMap<>(parameters != null ? parameters : Map.of());
            // 注入安全约束补丁：限制最大行数、开启只读沙箱、注入合规水印
            safeParams.put("boundedScope", true);
            safeParams.put("maxRecords", 50);
            safeParams.put("readOnly", true);
            safeParams.put("complianceTagged", true);

            return new BarrierDecision(
                    true,
                    true,
                    "APPROVED_WITH_PROJECTION",
                    Collections.unmodifiableMap(safeParams),
                    0.85,
                    "Action parameters projected into safe constitutional hyperplane"
            );
        }

        // 4. 正常合规无损放行
        return new BarrierDecision(
                true,
                false,
                "APPROVED_SAFE",
                parameters != null ? parameters : Map.of(),
                0.98,
                "Fully aligned and compliant"
        );
    }
}
