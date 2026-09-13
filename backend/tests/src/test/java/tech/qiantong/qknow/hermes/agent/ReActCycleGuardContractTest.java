package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReAct 循环死循环检测与熔断契约测试
 */
class ReActCycleGuardContractTest {

    private ReActCycleGuard guard;

    @BeforeEach
    void setUp() {
        // 最大 10 步，滑动窗口内最多重复 3 次
        guard = new ReActCycleGuard(10, 3);
    }

    @Test
    @DisplayName("契约验证：连续两次完全相同参数的工具调用，第二次必被短路拦截并注入纠偏警示")
    void testBlockConsecutiveDuplicateToolCall() {
        String tool = "weather_query";
        String args1 = "{\"city\":\"Beijing\",\"date\":\"today\"}";
        // 字段顺序颠倒，语义等价
        String args2 = "{\"date\":\"today\",\"city\":\"Beijing\"}";

        ReActCycleGuard.CycleCheckResult res1 = guard.inspectToolCall(tool, args1);
        assertFalse(res1.tripped(), "首次调用应当放行");
        assertNull(res1.injectionMessage());

        ReActCycleGuard.CycleCheckResult res2 = guard.inspectToolCall(tool, args2);
        assertTrue(res2.tripped(), "连续使用完全相同参数的调用必须被短路拦截！");
        assertNotNull(res2.injectionMessage());
        assertTrue(res2.injectionMessage().contains("重复"), "注入消息必须包含重复警告");
    }

    @Test
    @DisplayName("契约验证：A-B-A-B-A 窗口震荡死循环，达到阈值必须触发熔断")
    void testSlidingWindowOscillationDetection() {
        String toolA = "search_tool";
        String argsA = "{\"query\":\"Java\"}";
        String toolB = "db_query";
        String argsB = "{\"sql\":\"SELECT 1\"}";

        assertFalse(guard.inspectToolCall(toolA, argsA).tripped()); // A 第 1 次
        assertFalse(guard.inspectToolCall(toolB, argsB).tripped()); // B 第 1 次
        assertFalse(guard.inspectToolCall(toolA, argsA).tripped()); // A 第 2 次 (有 B 间隔)
        assertFalse(guard.inspectToolCall(toolB, argsB).tripped()); // B 第 2 次
        
        // A 第 3 次触发窗口频次阈值 (maxRepeatedCalls=3)
        ReActCycleGuard.CycleCheckResult resA3 = guard.inspectToolCall(toolA, argsA);
        assertTrue(resA3.tripped(), "滑动窗口内相同调用出现 3 次必须触发振荡熔断！");
        assertTrue(resA3.injectionMessage().contains("循环调用"), "提示语必须说明循环调用超限");
    }

    @Test
    @DisplayName("契约验证：相同工具但不同参数正常放行")
    void testAllowDistinctParameters() {
        String tool = "search_tool";
        assertFalse(guard.inspectToolCall(tool, "{\"query\":\"React\"}").tripped());
        assertFalse(guard.inspectToolCall(tool, "{\"query\":\"Vue\"}").tripped());
        assertFalse(guard.inspectToolCall(tool, "{\"query\":\"Angular\"}").tripped());
    }

    @Test
    @DisplayName("契约验证：步数限制严格保护")
    void testStepLimitProtection() {
        for (int i = 1; i <= 10; i++) {
            assertTrue(guard.checkStepLimit(), "步数 " + i + " 应当在限制内");
        }
        assertFalse(guard.checkStepLimit(), "第 11 步必须被步数门限阻断");
    }
}
