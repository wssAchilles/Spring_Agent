package tech.qiantong.qknow.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class DefensiveToolExecutorTest {

    @TempDir
    Path tempDir;

    private DefensiveToolExecutor executor;

    @BeforeEach
    void setUp() {
        // 配置 maxRepeats = 3, timeout = 1000ms, maxLength = 100, spillThreshold = 1024 * 1024 (1MB)
        executor = new DefensiveToolExecutor(3, 1000, 100, 1024 * 1024, tempDir);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void testCircuitBreakerOnSemanticLoop() {
        String toolName = "search_tool";
        String params = "{\"query\": \"java concurrency\"}";
        Callable<String> toolLogic = () -> "result";

        // 模拟连续 3 次传入完全相同的参数，应该成功
        for (int i = 0; i < 3; i++) {
            String res = executor.executeTool(toolName, params, toolLogic);
            assertEquals("result", res);
        }

        // 模拟第 4 次调用，断言抛出 CircuitBreakerException
        CircuitBreakerException exception = assertThrows(CircuitBreakerException.class, () -> {
            executor.executeTool(toolName, params, toolLogic);
        });
        
        assertTrue(exception.getMessage().contains("Semantic loop detected"));
    }

    @Test
    void testResultSpillForLargeOutput() {
        // 模拟工具返回 5MB 大小的字符串
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5 * 1024 * 1024; i++) {
            sb.append("A");
        }
        String largeResult = sb.toString();

        Callable<String> toolLogic = () -> largeResult;

        String result = executor.executeTool("log_tool", "{}", toolLogic);

        // 断言返回结果已执行 Spill(落盘换 URI)
        assertTrue(result.startsWith("FileURI: file://"));
        assertTrue(result.contains(tempDir.toAbsolutePath().toString()));
    }
    
    @Test
    void testHeadTailTruncation() {
        // 模拟结果长度 > maxLength (100) 且 < spillThreshold (1MB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append(i % 10);
        }
        String mediumResult = sb.toString();
        
        Callable<String> toolLogic = () -> mediumResult;

        String result = executor.executeTool("log_tool", "{}", toolLogic);

        // 断言结果被成功执行头尾截断 (Head-Tail Truncation)
        assertTrue(result.contains("<...[truncated]...>"));
        assertTrue(result.length() < 200);
        assertTrue(result.startsWith("01234")); // head 部分
        assertTrue(result.endsWith("56789")); // tail 部分
    }
    
    @Test
    void testTimeoutBudget() {
        // 模拟耗时工具调用
        Callable<String> slowLogic = () -> {
            Thread.sleep(2000);
            return "done";
        };
        
        // 断言超时断路器生效
        CircuitBreakerException exception = assertThrows(CircuitBreakerException.class, () -> {
            executor.executeTool("slow_tool", "{}", slowLogic);
        });
        
        assertTrue(exception.getMessage().contains("工具执行超时"));
    }
}
