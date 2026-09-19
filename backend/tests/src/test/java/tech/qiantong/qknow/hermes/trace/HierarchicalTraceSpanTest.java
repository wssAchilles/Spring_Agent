package tech.qiantong.qknow.hermes.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.trace.model.HierarchicalTraceSpan;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 106 契约测试集一：层次化分布式追踪片段模型测试
 *
 * @author Achilles
 * @version 1.0
 */
public class HierarchicalTraceSpanTest {

    @Test
    @DisplayName("契约 1：Java 21 Record 不可变性与多类型 Span 模型验证")
    void testRecordImmutabilityAndTypes() {
        HierarchicalTraceSpan span = new HierarchicalTraceSpan(
                "span-001",
                "trace-106",
                null,
                "DeepSeek-R1-Reasoning",
                "AGENT_REASONING",
                System.nanoTime(),
                12500L,
                450,
                "SUCCESS",
                "用户企业知识库查询需求",
                "推导出的业务解决方案",
                Map.of("model", "deepseek-r1", "temperature", "0.6")
        );

        assertNotNull(span);
        assertEquals("span-001", span.spanId());
        assertEquals("trace-106", span.traceId());
        assertNull(span.parentSpanId());
        assertEquals("AGENT_REASONING", span.spanType());
        assertEquals(450, span.tokenCount());
        assertEquals("SUCCESS", span.status());

        // 验证 attributes 不可修改保护
        assertThrows(UnsupportedOperationException.class, () -> span.attributes().put("hacked", "value"));
    }

    @Test
    @DisplayName("契约 2：自适应文本超长截断与 SHA-256 指纹锚定 (超过 1KB 压缩并标记哈希)")
    void testAdaptiveSummaryTruncationAndFingerprint() {
        // 构造超过 2000 字符的超大 Prompt
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是包含海量系统提示词与企业背景上下文的数据分片-").append(i).append("；");
        }
        String hugePrompt = sb.toString();
        assertTrue(hugePrompt.length() > 2000);

        HierarchicalTraceSpan span = new HierarchicalTraceSpan(
                "span-huge",
                "trace-huge",
                "parent-000",
                "HugePromptStep",
                "TOOL_MCP",
                System.nanoTime(),
                5000L,
                1200,
                "SUCCESS",
                hugePrompt,
                "简短出参",
                Map.of()
        );

        // 截断验证
        String summary = span.summaryInput();
        assertNotNull(summary);
        assertTrue(summary.length() < 500, "超过 1KB 文本必须被截断至紧凑摘要，实际长度: " + summary.length());
        assertTrue(summary.contains("[TRUNCATED len="), "必须包含截断元数据标识");
        assertTrue(summary.contains("sha256="), "必须包含 SHA-256 指纹");

        // 短文本保持不变
        assertEquals("简短出参", span.summaryOutput());
    }
}
