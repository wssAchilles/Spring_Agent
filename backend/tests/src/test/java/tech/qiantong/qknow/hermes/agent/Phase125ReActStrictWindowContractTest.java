package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuardReceipt;
import tech.qiantong.qknow.hermes.agent.guard.ReActStrictWindowCycleGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 125 契约测试：ReAct 严格窗口不变性、局部致密性自动机与长周期交错死循环反例消解中枢
 */
class Phase125ReActStrictWindowContractTest {

    private ReActStrictWindowCycleGuard guard;

    @BeforeEach
    void setUp() {
        // 最大 10 步，滑动窗口容量 6，最大重复 3 次，最大二元转移重复 3 次
        guard = new ReActStrictWindowCycleGuard(10, 3, 6, 3);
    }

    @Test
    @DisplayName("契约 1：连续两次完全相同参数的工具调用，第 2 次立即短路拦截并签发审计凭单")
    void test01_ConsecutiveDuplicateImmediateBreaker() {
        String tool = "weather_query";
        String args1 = "{\"city\":\"Beijing\",\"date\":\"today\"}";
        String args2 = "{\"date\":\"today\",\"city\":\"Beijing\"}"; // 键逆序等价

        ReActStrictWindowCycleGuard.StrictCheckResult res1 = guard.inspectToolCallWithReceipt("sess-01", tool, args1);
        assertFalse(res1.checkResult().tripped(), "首次调用应当放行");
        assertFalse(res1.receipt().tripped());
        assertTrue(res1.receipt().verifySignature(), "首次凭单签名必须有效");

        ReActStrictWindowCycleGuard.StrictCheckResult res2 = guard.inspectToolCallWithReceipt("sess-01", tool, args2);
        assertTrue(res2.checkResult().tripped(), "连续使用完全相同参数的调用必须被短路拦截！");
        assertTrue(res2.receipt().tripped());
        assertTrue(res2.receipt().trippedReason().contains("重复"), "提示语必须指出重复调用");
        assertTrue(res2.receipt().verifySignature(), "拦截凭单签名必须有效");
    }

    @Test
    @DisplayName("契约 2：定理 1.1 验证：严格先淘汰后计数，彻底消除暂态 W+1 幽灵计数误杀反例")
    void test02_StrictWindowEvictionPrecedenceNoGhostCount() {
        // 场景：容量 W=6, 阈值 B=3
        // 步骤 1~6 依次调用：A, B, C, D, A, E
        // 此时窗口满载：[A, B, C, D, A, E]，其中 A 出现 2 次，最老的正是第 1 步的 A
        String toolA = "query_user";
        String argsA = "{\"id\":1001}";

        assertTrue(!guard.inspectToolCall(toolA, argsA).tripped(), "第 1 步 A 应当放行");
        assertTrue(!guard.inspectToolCall("tool_b", "{\"k\":1}").tripped(), "第 2 步 B 应当放行");
        assertTrue(!guard.inspectToolCall("tool_c", "{\"k\":2}").tripped(), "第 3 步 C 应当放行");
        assertTrue(!guard.inspectToolCall("tool_d", "{\"k\":3}").tripped(), "第 4 步 D 应当放行");
        assertTrue(!guard.inspectToolCall(toolA, argsA).tripped(), "第 5 步 A 应当放行 (第 2 次出现)");
        assertTrue(!guard.inspectToolCall("tool_e", "{\"k\":4}").tripped(), "第 6 步 E 应当放行");

        // 验证当前窗口大小为 6
        assertEquals(6, guard.getCallFingerprints().size(), "第 6 步后窗口大小应当恰好为 6");
        assertEquals(2, guard.getFingerprintCounts().get(guard.calculateFingerprint(toolA, argsA)), "窗口内 A 的频次为 2");

        // 第 7 步：再次调用 A
        // 若时序错误（先计数）：频次变为 2 + 1 = 3，错误触发熔断 (Ghost Count 误杀)
        // 严格时序（先淘汰）：最老 A 被淘汰，频次降为 1，再计入当前提议，有效频次为 1 + 1 = 2 < 3，必须安全放行！
        ReActStrictWindowCycleGuard.StrictCheckResult resA7 = guard.inspectToolCallWithReceipt("sess-02", toolA, argsA);
        assertFalse(resA7.checkResult().tripped(), "第 7 步调用 A 绝不能被幽灵计数误杀！最老 A 淘汰后真实有效频次仅为 2 < 3");
        assertEquals(6, guard.getCallFingerprints().size(), "淘汰后窗口大小依然维持在容量 6 内");
        assertEquals(2, guard.getFingerprintCounts().get(guard.calculateFingerprint(toolA, argsA)), "新窗口内 A 的计数严格为 2");

        // 第 8 步：调用 B (最老 B 被淘汰)
        assertFalse(guard.inspectToolCall("tool_b", "{\"k\":1}").tripped(), "第 8 步调用 B 正常放行");

        // 第 9 步：再次提议调用 A
        // 此时窗口内为 [C, D, A, E, A, B]，最老的是 C（不是 A）。
        // 计入新 A 后，窗口内 A 的频次达到 2 + 1 = 3，此时真实达到阈值，确定性触发熔断！
        ReActStrictWindowCycleGuard.StrictCheckResult resA9 = guard.inspectToolCallWithReceipt("sess-02", toolA, argsA);
        assertTrue(resA9.checkResult().tripped(), "第 9 步调用 A 真实达到滑动窗口最大重复次数 3，必须精准触发高频振荡熔断！");
        assertTrue(resA9.receipt().trippedReason().contains("滑动窗口"), "提示语必须说明滑动窗口频次超限");
    }

    @Test
    @DisplayName("契约 3：交错死循环消解：跨工具交替振荡 (A-B-A-B-A-B) 必须被确定性拦截")
    void test03_InterleavedOscillationDetectionAB_AB() {
        // 创建针对交错死循环的高灵敏守卫：容量 6，最大重复 4，转移重复 3
        ReActStrictWindowCycleGuard sensitiveGuard = new ReActStrictWindowCycleGuard(15, 4, 6, 3);
        String toolA = "search_tool";
        String argsA = "{\"keyword\":\"ai\"}";
        String toolB = "db_fetch";
        String argsB = "{\"id\":\"101\"}";

        // A1 -> B1 -> A2 -> B2 放行
        assertFalse(sensitiveGuard.inspectToolCall(toolA, argsA).tripped(), "A1 放行");
        assertFalse(sensitiveGuard.inspectToolCall(toolB, argsB).tripped(), "B1 放行");
        assertFalse(sensitiveGuard.inspectToolCall(toolA, argsA).tripped(), "A2 放行");
        assertFalse(sensitiveGuard.inspectToolCall(toolB, argsB).tripped(), "B2 放行");
        assertFalse(sensitiveGuard.inspectToolCall(toolA, argsA).tripped(), "A3 放行 (单工具未达 4)");

        // 此时准备执行 B3：形成第 3 次 A->B 转移，达到转移阈值 3，必须触发交错死循环熔断！
        ReActCycleGuard.CycleCheckResult resB3 = sensitiveGuard.inspectToolCall(toolB, argsB);
        assertTrue(resB3.tripped(), "第 3 次出现相同转移形成交替死循环必须被拦截！");
        assertTrue(resB3.injectionMessage().contains("交错死循环"), "提示语必须明确指出交错死循环");
    }

    @Test
    @DisplayName("契约 4：良性多元探索序列 0 误杀验证")
    void test04_BenignExplorationSequencePass() {
        // 模拟智能体在 10 步内探索不同工具或同一工具的不同参数，必须全部顺利通过
        for (int i = 1; i <= 10; i++) {
            String tool = "tool_" + (i % 3);
            String args = String.format("{\"query\":\"task_%d\",\"epoch\":%d}", i, System.nanoTime());
            ReActCycleGuard.CycleCheckResult res = guard.inspectToolCall(tool, args);
            assertFalse(res.tripped(), "良性多元探索第 " + i + " 步必须通过");
        }
    }

    @Test
    @DisplayName("契约 5：规范化指纹稳定性：乱序 JSON 键与空格扰动保持 100% 确定性哈希")
    void test05_CanonicalJsonFingerprintStability() {
        String tool = "order_service";
        String json1 = "{\"orderId\":\"O-1001\",\"amount\":99.5,\"tags\":[\"vip\",\"express\"]}";
        String json2 = "{\n  \"tags\": [\"vip\", \"express\"],\n  \"amount\": 99.5,\n  \"orderId\": \"O-1001\"\n}";
        String json3 = "{\"amount\":99.5,\"orderId\":\"O-1001\",\"tags\":[\"vip\",\"express\"]}";

        String fp1 = guard.calculateFingerprint(tool, json1);
        String fp2 = guard.calculateFingerprint(tool, json2);
        String fp3 = guard.calculateFingerprint(tool, json3);

        assertEquals(fp1, fp2, "不同格式与换行的 JSON 必须生成相同指纹");
        assertEquals(fp2, fp3, "不同键顺序的 JSON 必须生成相同指纹");
    }

    @Test
    @DisplayName("契约 6：不可变审计凭单 SHA-256 签名完整性与单比特篡改拦截")
    void test06_ReceiptSha256ImmutabilityAndSelfVerification() {
        ReActStrictWindowCycleGuard.StrictCheckResult result =
                guard.inspectToolCallWithReceipt("sess-999", "audit_tool", "{\"action\":\"verify\"}");

        ReActCycleGuardReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单签名验真必须通过");

        // 模拟篡改凭单内容
        ReActCycleGuardReceipt tampered = new ReActCycleGuardReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.toolName(),
                receipt.canonicalFingerprint(),
                receipt.windowCapacity(),
                receipt.windowSize(),
                receipt.currentFingerprintCount() + 10, // 恶意篡改计数
                receipt.tripped(),
                receipt.trippedReason(),
                receipt.evaluatedAtNanos(),
                receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "单字段被篡改的凭单自验真必须失败！");
    }

    @Test
    @DisplayName("契约 7：微秒级延迟预算：10,000 次判定单次纯内存耗时稳定 <= 20 微秒")
    void test07_MicrosecondLatencyBudget() {
        guard.reset();
        int iterations = 10_000;
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            guard.inspectToolCall("perf_tool", "{\"step\":" + i + "}");
        }

        long totalDurationNanos = System.nanoTime() - start;
        double avgMicros = (totalDurationNanos / 1_000.0) / iterations;

        System.out.println("Phase 125 ReActCycleGuard 10,000 次判定平均耗时: " + avgMicros + " 微秒");
        assertTrue(avgMicros <= 20.0, "单次内存判定耗时必须严格控制在 20 微秒以内，实测: " + avgMicros);
    }

    @Test
    @DisplayName("契约 8：并发安全性验证：8 线程并发调用状态一致性与无死锁")
    void test08_ThreadSafetyUnderConcurrentCalls() throws InterruptedException, ExecutionException {
        guard.reset();
        int threadCount = 8;
        int callsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            tasks.add(() -> {
                for (int i = 0; i < callsPerThread; i++) {
                    guard.checkStepLimit();
                    String tool = "concurrent_tool_" + (i % 5);
                    String args = String.format("{\"t\":%d,\"i\":%d}", threadId, i);
                    guard.inspectToolCall(tool, args);
                }
                return true;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "每个并发线程必须无异常完成");
        }
        executor.shutdown();

        // 步数总和应当严格等于 threadCount * callsPerThread
        assertEquals(threadCount * callsPerThread, guard.getCurrentStep(), "多线程并发自增步数必须严格保持无数据丢失");
        assertTrue(guard.getCallFingerprints().size() <= guard.getWindowCapacity(), "并发滑动窗口大小不得超过容量");
    }
}
