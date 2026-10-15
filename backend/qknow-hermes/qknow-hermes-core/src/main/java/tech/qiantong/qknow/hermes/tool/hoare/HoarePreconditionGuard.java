package tech.qiantong.qknow.hermes.tool.hoare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * 声明式 Hoare 逻辑前置条件守卫 {P}
 * 在工具调用被调度之前进行严格的前置状态与权限断言
 * 增强：支持输出面向 ACI 闭环的细粒度 Schema 差量与单步自愈指引
 */
public class HoarePreconditionGuard {

    public record GuardCheckResult(
            boolean passed,
            String violationReason,
            String violationType,
            List<String> missingParameters,
            Map<String, String> parameterErrors,
            String suggestedRemedy
    ) {
        public static GuardCheckResult ok() {
            return new GuardCheckResult(true, "", "NONE", Collections.emptyList(), Collections.emptyMap(), "");
        }

        public static GuardCheckResult fail(String reason) {
            return new GuardCheckResult(false, reason, "GENERAL_VIOLATION", Collections.emptyList(), Collections.emptyMap(), "Please review input parameters and retry.");
        }

        public static GuardCheckResult fail(
                String reason,
                String violationType,
                List<String> missingParams,
                Map<String, String> errors,
                String remedy
        ) {
            return new GuardCheckResult(
                    false,
                    reason,
                    violationType == null ? "GENERAL_VIOLATION" : violationType,
                    missingParams == null ? Collections.emptyList() : Collections.unmodifiableList(missingParams),
                    errors == null ? Collections.emptyMap() : Collections.unmodifiableMap(errors),
                    remedy == null ? "" : remedy
            );
        }
    }

    private record GuardRule(
            String description,
            String violationType,
            String targetKey,
            String remedyGuide,
            BiPredicate<Map<String, Object>, Map<String, Object>> predicate
    ) {}

    private final List<GuardRule> rules = new ArrayList<>();

    public HoarePreconditionGuard requireParameter(String paramName) {
        Objects.requireNonNull(paramName, "paramName cannot be null");
        rules.add(new GuardRule(
                "ParamRequired[" + paramName + "]",
                "MISSING_REQUIRED_PARAMETER",
                paramName,
                "Please provide non-null parameter '" + paramName + "' in the tool call arguments.",
                (params, ctx) -> params != null && params.containsKey(paramName) && params.get(paramName) != null
        ));
        return this;
    }

    public HoarePreconditionGuard requirePositiveNumber(String paramName) {
        Objects.requireNonNull(paramName, "paramName cannot be null");
        rules.add(new GuardRule(
                "ParamPositive[" + paramName + "]",
                "INVALID_PARAMETER_VALUE",
                paramName,
                "Parameter '" + paramName + "' must be a positive number (> 0.0).",
                (params, ctx) -> {
                    if (params == null || !params.containsKey(paramName)) return false;
                    Object val = params.get(paramName);
                    if (val instanceof Number num) {
                        return num.doubleValue() > 0.0;
                    }
                    return false;
                }
        ));
        return this;
    }

    public HoarePreconditionGuard requireContextResource(String resourceKey) {
        Objects.requireNonNull(resourceKey, "resourceKey cannot be null");
        rules.add(new GuardRule(
                "ContextResourceRequired[" + resourceKey + "]",
                "MISSING_CONTEXT_RESOURCE",
                resourceKey,
                "System context resource '" + resourceKey + "' is required before invoking this tool.",
                (params, ctx) -> ctx != null && ctx.containsKey(resourceKey) && ctx.get(resourceKey) != null
        ));
        return this;
    }

    public HoarePreconditionGuard requireLeaseNotExpired(String leaseKey, long maxTtlMs) {
        Objects.requireNonNull(leaseKey, "leaseKey cannot be null");
        rules.add(new GuardRule(
                "LeaseNotExpired[" + leaseKey + ", ttl=" + maxTtlMs + "ms]",
                "EXPIRED_LEASE",
                leaseKey,
                "Lease '" + leaseKey + "' has expired (TTL=" + maxTtlMs + "ms). Please request a lease renewal.",
                (params, ctx) -> {
                    if (ctx == null || !ctx.containsKey(leaseKey)) return false;
                    Object ts = ctx.get(leaseKey);
                    if (ts instanceof Number num) {
                        long leaseTime = num.longValue();
                        return (System.currentTimeMillis() - leaseTime) <= maxTtlMs;
                    }
                    return false;
                }
        ));
        return this;
    }

    public HoarePreconditionGuard requireSecurityTokenConstantTime(String tokenParamKey, String expectedToken) {
        Objects.requireNonNull(tokenParamKey, "tokenParamKey cannot be null");
        Objects.requireNonNull(expectedToken, "expectedToken cannot be null");
        rules.add(new GuardRule(
                "ConstantTimeTokenCheck[" + tokenParamKey + "]",
                "AUTHENTICATION_FAILURE",
                tokenParamKey,
                "Security token in '" + tokenParamKey + "' is invalid or signature mismatch. Provide valid credentials.",
                (params, ctx) -> {
                    if (params == null || !params.containsKey(tokenParamKey)) return false;
                    Object val = params.get(tokenParamKey);
                    if (val == null) return false;
                    byte[] actualBytes = val.toString().getBytes(StandardCharsets.UTF_8);
                    byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
                    return MessageDigest.isEqual(actualBytes, expectedBytes);
                }
        ));
        return this;
    }

    public GuardCheckResult evaluate(Map<String, Object> inputParams, Map<String, Object> systemContext) {
        for (GuardRule rule : rules) {
            boolean satisfied = false;
            try {
                satisfied = rule.predicate().test(inputParams, systemContext);
            } catch (Exception e) {
                return GuardCheckResult.fail(
                        "RuleException at " + rule.description() + ": " + e.getMessage(),
                        "INTERNAL_EVAL_EXCEPTION",
                        Collections.emptyList(),
                        Map.of(rule.targetKey(), "Exception during evaluation: " + e.getMessage()),
                        "Check system context and parameter types."
                );
            }
            if (!satisfied) {
                List<String> missing = new ArrayList<>();
                Map<String, String> errors = new LinkedHashMap<>();
                if ("MISSING_REQUIRED_PARAMETER".equals(rule.violationType()) || "MISSING_CONTEXT_RESOURCE".equals(rule.violationType())) {
                    missing.add(rule.targetKey());
                } else {
                    Object currentVal = inputParams != null ? inputParams.get(rule.targetKey()) : null;
                    errors.put(rule.targetKey(), "Current value: [" + currentVal + "], Rule violated: " + rule.description());
                }
                return GuardCheckResult.fail(
                        "PreconditionViolation: " + rule.description() + " is not satisfied.",
                        rule.violationType(),
                        missing,
                        errors,
                        rule.remedyGuide()
                );
            }
        }
        return GuardCheckResult.ok();
    }
}
