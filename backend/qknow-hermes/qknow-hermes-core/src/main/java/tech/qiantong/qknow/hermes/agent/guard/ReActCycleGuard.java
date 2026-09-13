package tech.qiantong.qknow.hermes.agent.guard;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * ReAct 循环卫士：防死循环、重复调用短路拦截与滑动窗口震荡熔断
 */
@Slf4j
public class ReActCycleGuard {

    private final int maxSteps;
    private final int maxRepeatedCalls;
    private int currentStep = 0;

    // 滑动窗口：记录最近的工具调用指纹 (最多保留 6 次)
    private final LinkedList<String> callFingerprints = new LinkedList<>();
    // 记录工具调用指纹出现的频次
    private final Map<String, Integer> fingerprintCounts = new HashMap<>();

    public ReActCycleGuard(int maxSteps, int maxRepeatedCalls) {
        this.maxSteps = maxSteps;
        this.maxRepeatedCalls = maxRepeatedCalls;
    }

    public ReActCycleGuard() {
        this(10, 3);
    }

    /**
     * 校验步数是否超限
     */
    public boolean checkStepLimit() {
        return ++currentStep <= maxSteps;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    /**
     * 生成工具调用的确定性规范指纹 (Canonical SHA-256)
     */
    public String calculateFingerprint(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            argumentsJson = "{}";
        }
        try {
            // 对 JSON 键进行排序规范化，消除由字段无序导致的哈希误判
            Object parsed = JSON.parse(argumentsJson);
            String canonicalJson = JSON.toJSONString(parsed, JSONWriter.Feature.MapSortField);
            return DigestUtil.sha256Hex(toolName + "::" + canonicalJson);
        } catch (Exception e) {
            return DigestUtil.sha256Hex(toolName + "::" + argumentsJson.trim());
        }
    }

    /**
     * 检查工具调用是否陷入死循环
     * @param toolName 工具名称
     * @param argumentsJson 工具入参 JSON
     * @return 检查结果 (tripped 为 true 表示短路拦截)
     */
    public CycleCheckResult inspectToolCall(String toolName, String argumentsJson) {
        String fp = calculateFingerprint(toolName, argumentsJson);

        // 1. 即时连续重复调用检测 (Consecutive Duplicate Call)
        if (!callFingerprints.isEmpty() && callFingerprints.getLast().equals(fp)) {
            log.warn("[ReActGuard] 触发连续完全重复工具调用短路拦截: tool={}, fp={}", toolName, fp);
            return CycleCheckResult.breaker(
                    String.format("【系统纠偏提示】：你已连续使用完全相同的参数调用了工具 '%s'。该工具在相同参数下的返回结果已在上下文中，严禁重复无效调用！请直接根据已有结果回答，或调整查询参数/换用备选工具。", toolName)
            );
        }

        // 2. 滑动窗口振荡调用检测 (Oscillation Detection within 6-step window)
        int count = fingerprintCounts.getOrDefault(fp, 0) + 1;
        if (count >= maxRepeatedCalls) {
            log.warn("[ReActGuard] 触发滑动窗口高频振荡循环熔断: tool={}, count={}", toolName, count);
            return CycleCheckResult.breaker(
                    String.format("【系统纠偏提示】：检测到在当前推理会话中循环调用工具 '%s' 超过 %d 次。请停止尝试该工具，基于当前已知全部信息进行归纳总结，并明确向用户说明局限性。", toolName, maxRepeatedCalls)
            );
        }

        // 维护最多 6 步的滑动窗口
        if (callFingerprints.size() >= 6) {
            String removed = callFingerprints.removeFirst();
            fingerprintCounts.computeIfPresent(removed, (k, v) -> v > 1 ? v - 1 : null);
        }
        callFingerprints.addLast(fp);
        fingerprintCounts.put(fp, count);

        return CycleCheckResult.pass();
    }

    public record CycleCheckResult(boolean tripped, String injectionMessage) {
        public static CycleCheckResult pass() {
            return new CycleCheckResult(false, null);
        }

        public static CycleCheckResult breaker(String message) {
            return new CycleCheckResult(true, message);
        }
    }
}
