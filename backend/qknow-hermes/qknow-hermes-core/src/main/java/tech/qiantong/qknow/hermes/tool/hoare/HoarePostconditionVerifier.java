package tech.qiantong.qknow.hermes.tool.hoare;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 声明式 Hoare 逻辑后置不变量验证器 {Q}
 * 在工具执行完毕后核验系统状态转移是否违背预设法则
 */
public class HoarePostconditionVerifier {

    public record InvariantCheckResult(boolean passed, String violationReason) {
        public static InvariantCheckResult ok() {
            return new InvariantCheckResult(true, "");
        }

        public static InvariantCheckResult fail(String reason) {
            return new InvariantCheckResult(false, reason);
        }
    }

    @FunctionalInterface
    public interface InvariantPredicate {
        boolean test(Map<String, Object> preState, Map<String, Object> postState, Object toolResult);
    }

    private final List<InvariantPredicate> invariantList = new ArrayList<>();
    private final List<String> ruleDescriptions = new ArrayList<>();

    public HoarePostconditionVerifier requireNonNegativeField(String stateKey) {
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        invariantList.add((pre, post, res) -> {
            if (post == null || !post.containsKey(stateKey)) return false;
            Object val = post.get(stateKey);
            if (val instanceof Number num) {
                return num.doubleValue() >= 0.0;
            }
            return false;
        });
        ruleDescriptions.add("StateNonNegative[" + stateKey + "]");
        return this;
    }

    public HoarePostconditionVerifier requireMonotonicIncreasing(String stateKey) {
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        invariantList.add((pre, post, res) -> {
            if (pre == null || post == null) return false;
            if (!pre.containsKey(stateKey) || !post.containsKey(stateKey)) return false;
            Object preVal = pre.get(stateKey);
            Object postVal = post.get(stateKey);
            if (preVal instanceof Number num1 && postVal instanceof Number num2) {
                return num2.doubleValue() >= num1.doubleValue();
            }
            return false;
        });
        ruleDescriptions.add("StateMonotonicIncreasing[" + stateKey + "]");
        return this;
    }

    public HoarePostconditionVerifier requireResultNonNull() {
        invariantList.add((pre, post, res) -> res != null);
        ruleDescriptions.add("ResultNonNull");
        return this;
    }

    public HoarePostconditionVerifier requireCustomInvariant(String ruleName, InvariantPredicate predicate) {
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(predicate, "predicate cannot be null");
        invariantList.add(predicate);
        ruleDescriptions.add("CustomInvariant[" + ruleName + "]");
        return this;
    }

    public InvariantCheckResult evaluate(Map<String, Object> preState, Map<String, Object> postState, Object toolResult) {
        for (int i = 0; i < invariantList.size(); i++) {
            boolean satisfied = false;
            try {
                satisfied = invariantList.get(i).test(preState, postState, toolResult);
            } catch (Exception e) {
                return InvariantCheckResult.fail("InvariantException at " + ruleDescriptions.get(i) + ": " + e.getMessage());
            }
            if (!satisfied) {
                return InvariantCheckResult.fail("PostconditionViolation: " + ruleDescriptions.get(i) + " is broken.");
            }
        }
        return InvariantCheckResult.ok();
    }
}
