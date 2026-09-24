package tech.qiantong.qknow.hermes.agent.guard;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 严格窗口时序 ReAct 循环守卫 (Phase 125 核心组件)
 * 严格对齐 ESWA Lemma 3.2 与 Algorithm 3 形式化时序：
 * 1. 淘汰前置（Eviction-Precedence）：先淘汰窗口最老指纹并递减频次、再计算当前提议频次、最后进行阈值判定；
 * 2. 彻底消除暂态 W+1 幽灵计数 (Transient Ghost Count) 漏洞；
 * 3. 基于二元转移对 (Bi-gram Transition) 消解长周期交错死循环 (如 A -> B -> A -> B)；
 * 4. 签发纯 Java 21 Record 格式不可变防篡改审计凭单 (ReActCycleGuardReceipt)。
 */
@Slf4j
public class ReActStrictWindowCycleGuard {

    private final int maxSteps;
    private final int maxRepeatedCalls;
    private final int windowCapacity;
    private final int maxTransitionRepeats;

    private int currentStep = 0;
    private final ReentrantLock lock = new ReentrantLock();

    // 严格定长滑动窗口：记录最近执行成功的工具调用规范指纹 (容量恒 <= windowCapacity)
    private final LinkedList<String> callFingerprints = new LinkedList<>();
    // 窗口内指纹出现频次计数表
    private final Map<String, Integer> fingerprintCounts = new HashMap<>();
    // 二元调用转移对窗口 (prevFp -> currFp)
    private final LinkedList<String> transitionPairs = new LinkedList<>();
    // 二元调用转移对频次计数表
    private final Map<String, Integer> transitionCounts = new HashMap<>();

    public ReActStrictWindowCycleGuard(int maxSteps, int maxRepeatedCalls, int windowCapacity, int maxTransitionRepeats) {
        this.maxSteps = maxSteps;
        this.maxRepeatedCalls = maxRepeatedCalls;
        this.windowCapacity = windowCapacity;
        this.maxTransitionRepeats = maxTransitionRepeats;
    }

    public ReActStrictWindowCycleGuard(int maxSteps, int maxRepeatedCalls) {
        this(maxSteps, maxRepeatedCalls, 6, 3);
    }

    public ReActStrictWindowCycleGuard() {
        this(10, 3, 6, 3);
    }

    /**
     * 校验总推理步数是否超限
     */
    public boolean checkStepLimit() {
        lock.lock();
        try {
            return ++currentStep <= maxSteps;
        } finally {
            lock.unlock();
        }
    }

    public int getCurrentStep() {
        lock.lock();
        try {
            return currentStep;
        } finally {
            lock.unlock();
        }
    }

    public int getWindowCapacity() {
        return windowCapacity;
    }

    public int getMaxRepeatedCalls() {
        return maxRepeatedCalls;
    }

    /**
     * 生成工具调用的确定性规范指纹 (Canonical SHA-256)
     * 对 JSON 键进行排序规范化，消除无序字典导致的哈希漂移
     */
    public String calculateFingerprint(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            argumentsJson = "{}";
        }
        try {
            Object parsed = JSON.parse(argumentsJson);
            String canonicalJson = JSON.toJSONString(parsed, JSONWriter.Feature.MapSortField);
            return DigestUtil.sha256Hex(toolName + "::" + canonicalJson);
        } catch (Exception e) {
            return DigestUtil.sha256Hex(toolName + "::" + argumentsJson.trim());
        }
    }

    /**
     * 核心校验方法：检查工具调用是否陷入死循环（严格先淘汰后计数）
     * @param toolName 工具名称
     * @param argumentsJson 工具入参 JSON
     * @return 检查结果 (tripped 为 true 表示短路拦截)
     */
    public ReActCycleGuard.CycleCheckResult inspectToolCall(String toolName, String argumentsJson) {
        StrictCheckResult result = inspectToolCallWithReceipt(UUID.randomUUID().toString(), toolName, argumentsJson);
        return result.checkResult();
    }

    /**
     * 带不可变审计凭单的工具调用检查
     */
    public StrictCheckResult inspectToolCallWithReceipt(String sessionId, String toolName, String argumentsJson) {
        String fp = calculateFingerprint(toolName, argumentsJson);
        String receiptId = "RCP-" + UUID.randomUUID().toString().substring(0, 12);

        lock.lock();
        try {
            // 阶段 1：即时连续完全重复调用检测 (Consecutive Duplicate Check)
            if (!callFingerprints.isEmpty() && callFingerprints.getLast().equals(fp)) {
                String reason = String.format("【系统纠偏提示】：你已连续使用完全相同的参数调用了工具 '%s'。该工具在相同参数下的返回结果已在上下文中，严禁重复无效调用！请直接根据已有结果回答，或调整查询参数/换用备选工具。", toolName);
                log.warn("[ReActStrictGuard] 触发连续完全重复调用短路拦截: tool={}, fp={}", toolName, fp);
                ReActCycleGuardReceipt receipt = ReActCycleGuardReceipt.create(
                        receiptId, sessionId, toolName, fp, windowCapacity, callFingerprints.size(),
                        fingerprintCounts.getOrDefault(fp, 0), true, reason
                );
                return new StrictCheckResult(ReActCycleGuard.CycleCheckResult.breaker(reason), receipt);
            }

            // 阶段 2：严格先淘汰最老指纹 (Eviction-Precedence)
            // 保证参与后续断言的窗口元素总数在纳入当前提议后严格不超过 windowCapacity
            if (callFingerprints.size() >= windowCapacity) {
                String evictedFp = callFingerprints.removeFirst();
                fingerprintCounts.computeIfPresent(evictedFp, (k, v) -> v > 1 ? v - 1 : null);

                // 同步淘汰最老的二元转移对
                if (!transitionPairs.isEmpty()) {
                    String evictedTrans = transitionPairs.removeFirst();
                    transitionCounts.computeIfPresent(evictedTrans, (k, v) -> v > 1 ? v - 1 : null);
                }
            }

            // 阶段 3：计算淘汰后的当前提议指纹有效频次
            int proposedCount = fingerprintCounts.getOrDefault(fp, 0) + 1;

            // 阶段 4：滑动窗口单指纹高频振荡阈值判定 (Oscillation Threshold Evaluation)
            if (proposedCount >= maxRepeatedCalls) {
                String reason = String.format("【系统纠偏提示】：检测到在当前滑动窗口中循环调用工具 '%s' 达到 %d 次。请停止尝试该工具，基于当前已知全部信息进行归纳总结，并明确向用户说明局限性。", toolName, maxRepeatedCalls);
                log.warn("[ReActStrictGuard] 触发严格滑动窗口高频振荡熔断: tool={}, proposedCount={}, limit={}", toolName, proposedCount, maxRepeatedCalls);
                ReActCycleGuardReceipt receipt = ReActCycleGuardReceipt.create(
                        receiptId, sessionId, toolName, fp, windowCapacity, callFingerprints.size(),
                        proposedCount, true, reason
                );
                return new StrictCheckResult(ReActCycleGuard.CycleCheckResult.breaker(reason), receipt);
            }

            // 阶段 5：二元交错死循环判定 (Interleaved Oscillation Detection, e.g. A -> B -> A -> B -> A -> B)
            if (!callFingerprints.isEmpty()) {
                String lastFp = callFingerprints.getLast();
                String transitionKey = lastFp + "->" + fp;
                int currentTransCount = transitionCounts.getOrDefault(transitionKey, 0) + 1;
                if (currentTransCount >= maxTransitionRepeats) {
                    // 若前驱转移交错重复，且当前形成交错振荡循环
                    String reason = String.format("【系统纠偏提示】：检测到跨工具交错死循环与高频重复循环调用 (Interleaved Oscillation Cycle: %s)。请停止在固定工具间来回震荡，重新梳理解决思路！", toolName);
                    log.warn("[ReActStrictGuard] 触发跨工具交错死循环熔断: transition={}, count={}", transitionKey, currentTransCount);
                    ReActCycleGuardReceipt receipt = ReActCycleGuardReceipt.create(
                            receiptId, sessionId, toolName, fp, windowCapacity, callFingerprints.size(),
                            proposedCount, true, reason
                    );
                    return new StrictCheckResult(ReActCycleGuard.CycleCheckResult.breaker(reason), receipt);
                }
            }

            // 阶段 6：通过校验，原子提交状态变更入队
            callFingerprints.addLast(fp);
            fingerprintCounts.put(fp, proposedCount);

            if (callFingerprints.size() >= 2) {
                int size = callFingerprints.size();
                String prevFp = callFingerprints.get(size - 2);
                String transitionKey = prevFp + "->" + fp;
                transitionPairs.addLast(transitionKey);
                transitionCounts.put(transitionKey, transitionCounts.getOrDefault(transitionKey, 0) + 1);
            }

            ReActCycleGuardReceipt receipt = ReActCycleGuardReceipt.create(
                    receiptId, sessionId, toolName, fp, windowCapacity, callFingerprints.size(),
                    proposedCount, false, "PASSED"
            );
            return new StrictCheckResult(ReActCycleGuard.CycleCheckResult.pass(), receipt);

        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置状态（用于全新会话）
     */
    public void reset() {
        lock.lock();
        try {
            currentStep = 0;
            callFingerprints.clear();
            fingerprintCounts.clear();
            transitionPairs.clear();
            transitionCounts.clear();
        } finally {
            lock.unlock();
        }
    }

    public List<String> getCallFingerprints() {
        lock.lock();
        try {
            return new ArrayList<>(callFingerprints);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Integer> getFingerprintCounts() {
        lock.lock();
        try {
            return new HashMap<>(fingerprintCounts);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 封装检查结果与密码学审计凭单
     */
    public record StrictCheckResult(
            ReActCycleGuard.CycleCheckResult checkResult,
            ReActCycleGuardReceipt receipt
    ) {}
}
