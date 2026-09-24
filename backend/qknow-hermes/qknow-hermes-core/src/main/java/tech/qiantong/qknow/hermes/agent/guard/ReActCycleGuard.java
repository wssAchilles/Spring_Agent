package tech.qiantong.qknow.hermes.agent.guard;

import lombok.extern.slf4j.Slf4j;

/**
 * ReAct 循环卫士：防死循环、重复调用短路拦截与滑动窗口震荡熔断
 * 基于 ReActStrictWindowCycleGuard 实现严格时序淘汰前置（Eviction-Precedence）与交错死循环消解
 */
@Slf4j
public class ReActCycleGuard extends ReActStrictWindowCycleGuard {

    public ReActCycleGuard(int maxSteps, int maxRepeatedCalls) {
        super(maxSteps, maxRepeatedCalls, 6, 3);
    }

    public ReActCycleGuard() {
        super(10, 3, 6, 3);
    }

    /**
     * 检查结果数据契约 (保持向下完全兼容)
     */
    public record CycleCheckResult(boolean tripped, String injectionMessage) {
        public static CycleCheckResult pass() {
            return new CycleCheckResult(false, null);
        }

        public static CycleCheckResult breaker(String message) {
            return new CycleCheckResult(true, message);
        }
    }
}
