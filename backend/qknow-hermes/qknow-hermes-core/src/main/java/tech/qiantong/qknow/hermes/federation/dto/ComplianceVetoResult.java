package tech.qiantong.qknow.hermes.federation.dto;

import java.util.Map;

/**
 * 主权合规一票否决审查结果 Record
 */
public record ComplianceVetoResult(
        ComplianceVetoAction action,
        String ruleId,
        String ruleDescription,
        Map<String, Object> sanitizedParameters,
        double cbfSafetyMargin,
        long evaluationDurationNanos
) {
    public boolean isVetoed() {
        return action == ComplianceVetoAction.VETO_ABORTED;
    }
}
