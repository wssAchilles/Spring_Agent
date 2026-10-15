package tech.qiantong.qknow.hermes.tool.hoare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * 声明式 Hoare 逻辑前置条件守卫 {P}
 * 在工具调用被调度之前进行严格的前置状态与权限断言
 */
public class HoarePreconditionGuard {

    public record GuardCheckResult(boolean passed, String violationReason) {
        public static GuardCheckResult ok() {
            return new GuardCheckResult(true, "");
        }

        public static GuardCheckResult fail(String reason) {
            return new GuardCheckResult(false, reason);
        }
    }

    private final List<BiPredicate<Map<String, Object>, Map<String, Object>>> predicateList = new ArrayList<>();
    private final List<String> ruleDescriptions = new ArrayList<>();

    public HoarePreconditionGuard requireParameter(String paramName) {
        Objects.requireNonNull(paramName, "paramName cannot be null");
        predicateList.add((params, ctx) -> params != null && params.containsKey(paramName) && params.get(paramName) != null);
        ruleDescriptions.add("ParamRequired[" + paramName + "]");
        return this;
    }

    public HoarePreconditionGuard requirePositiveNumber(String paramName) {
        Objects.requireNonNull(paramName, "paramName cannot be null");
        predicateList.add((params, ctx) -> {
            if (params == null || !params.containsKey(paramName)) return false;
            Object val = params.get(paramName);
            if (val instanceof Number num) {
                return num.doubleValue() > 0.0;
            }
            return false;
        });
        ruleDescriptions.add("ParamPositive[" + paramName + "]");
        return this;
    }

    public HoarePreconditionGuard requireContextResource(String resourceKey) {
        Objects.requireNonNull(resourceKey, "resourceKey cannot be null");
        predicateList.add((params, ctx) -> ctx != null && ctx.containsKey(resourceKey) && ctx.get(resourceKey) != null);
        ruleDescriptions.add("ContextResourceRequired[" + resourceKey + "]");
        return this;
    }

    public HoarePreconditionGuard requireLeaseNotExpired(String leaseKey, long maxTtlMs) {
        Objects.requireNonNull(leaseKey, "leaseKey cannot be null");
        predicateList.add((params, ctx) -> {
            if (ctx == null || !ctx.containsKey(leaseKey)) return false;
            Object ts = ctx.get(leaseKey);
            if (ts instanceof Number num) {
                long leaseTime = num.longValue();
                return (System.currentTimeMillis() - leaseTime) <= maxTtlMs;
            }
            return false;
        });
        ruleDescriptions.add("LeaseNotExpired[" + leaseKey + ", ttl=" + maxTtlMs + "ms]");
        return this;
    }

    public HoarePreconditionGuard requireSecurityTokenConstantTime(String tokenParamKey, String expectedToken) {
        Objects.requireNonNull(tokenParamKey, "tokenParamKey cannot be null");
        Objects.requireNonNull(expectedToken, "expectedToken cannot be null");
        predicateList.add((params, ctx) -> {
            if (params == null || !params.containsKey(tokenParamKey)) return false;
            Object val = params.get(tokenParamKey);
            if (val == null) return false;
            byte[] actualBytes = val.toString().getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(actualBytes, expectedBytes);
        });
        ruleDescriptions.add("ConstantTimeTokenCheck[" + tokenParamKey + "]");
        return this;
    }

    public GuardCheckResult evaluate(Map<String, Object> inputParams, Map<String, Object> systemContext) {
        for (int i = 0; i < predicateList.size(); i++) {
            boolean satisfied = false;
            try {
                satisfied = predicateList.get(i).test(inputParams, systemContext);
            } catch (Exception e) {
                return GuardCheckResult.fail("RuleException at " + ruleDescriptions.get(i) + ": " + e.getMessage());
            }
            if (!satisfied) {
                return GuardCheckResult.fail("PreconditionViolation: " + ruleDescriptions.get(i) + " is not satisfied.");
            }
        }
        return GuardCheckResult.ok();
    }
}
