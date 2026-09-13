package tech.qiantong.qknow.hermes.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tech.qiantong.qknow.hermes.tool.resilience.ToolCircuitBreaker;
import tech.qiantong.qknow.hermes.tool.resilience.ToolResilienceDecorator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 工具弹性治理、超长截断与超时熔断契约测试
 */
@ExtendWith(MockitoExtension.class)
class ToolResilienceContractTest {

    @Mock
    private ToolCallback mockCallback;

    private ToolDefinition toolDefinition;

    @BeforeEach
    void setUp() {
        toolDefinition = ToolDefinition.builder()
                .name("contractTestTool")
                .description("A tool for contract testing")
                .inputSchema("{}")
                .build();
        lenient().when(mockCallback.getToolDefinition()).thenReturn(toolDefinition);
    }

    @Test
    @DisplayName("契约验证：超长工具返回值采用 Head-Tail 智能截断，保护上下文不被撑爆")
    void testToolOutputHeadTailTruncation() {
        // 构建 30,000 字符的长响应，首部带 HEAD_KEY，尾部带 TAIL_KEY
        StringBuilder sb = new StringBuilder();
        sb.append("HEAD_KEY_START: ");
        for (int i = 0; i < 3000; i++) {
            sb.append("1234567890");
        }
        sb.append(" :TAIL_KEY_END");
        String massiveOutput = sb.toString();

        when(mockCallback.call(anyString())).thenReturn(massiveOutput);

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        // 配置最大允许 16000 字符
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 5000, 16000, 1, cb);

        String result = decorator.call("{}");

        // 断言截断后长度严格在 16500 字符以内（允许提示语的微小开销）
        assertTrue(result.length() <= 16500, "结果长度必须受控截断，当前=" + result.length());
        assertTrue(result.startsWith("HEAD_KEY_START:"), "头部关键信息必须完好保留");
        assertTrue(result.endsWith(":TAIL_KEY_END"), "尾部关键信息必须完好保留");
        assertTrue(result.contains("系统保护提示") || result.contains("省略"), "必须包含截断提示");
    }

    @Test
    @DisplayName("契约验证：工具耗时超过 timeoutMs 时强行异步中断并返回结构化 TIMEOUT_ERROR")
    void testToolExecutionTimeout() {
        when(mockCallback.call(anyString())).thenAnswer(invocation -> {
            Thread.sleep(1000); // 模拟耗时 1 秒
            return "delayed_result";
        });

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        // 超时设为 100ms
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 100, 16000, 1, cb);

        String result = decorator.call("{}");

        assertNotNull(result);
        assertTrue(result.contains("TIMEOUT_ERROR") || result.contains("超时"), "超时必须返回明确的超时结构体");
        assertTrue(result.contains("error"), "状态必须标记为 error");
    }

    @Test
    @DisplayName("契约验证：工具抛出底层异常时返回包含自愈指导的结构化 JSON，杜绝打崩流式传输")
    void testToolExceptionReturnsStructuredError() {
        when(mockCallback.call(anyString())).thenThrow(new IllegalArgumentException("Invalid date parameter format"));

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 5000, 16000, 1, cb);

        String result = decorator.call("{}");

        assertNotNull(result);
        assertTrue(result.contains("EXECUTION_ERROR") || result.contains("error"), "异常时必须返回结构化错误描述");
        assertTrue(result.contains("Invalid date parameter format"), "必须包含异常原因，以便大模型自我纠错");
    }
}
