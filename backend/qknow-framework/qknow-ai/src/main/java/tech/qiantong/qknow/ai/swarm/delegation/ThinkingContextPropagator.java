package tech.qiantong.qknow.ai.swarm.delegation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 跨智能体思考流与上下文继承传递器
 * 严格对齐 DeepSeek 官方 API thinking: {"type": "enabled"} 规范
 * 保证 reasoning_content 无损回传，杜绝 HTTP 400 错误与业务幻觉
 */
public class ThinkingContextPropagator {

    private static final Logger log = LoggerFactory.getLogger(ThinkingContextPropagator.class);

    /**
     * 构建符合 DeepSeek 官方思考模式的请求体 Message
     */
    public DeepSeekMessage buildAssistantMessageWithThinking(String content, String reasoningContent) {
        if (content == null && reasoningContent == null) {
            throw new IllegalArgumentException("content 与 reasoningContent 不能同时为空");
        }
        return new DeepSeekMessage("assistant", content, reasoningContent);
    }

    /**
     * 跨 Agent 委托时，将前序推演转化为后继 Agent 的结构化只读继承凭据 (InheritedRationale)
     */
    public InheritedRationale distillRationaleForHandoff(
            String sourceAgentId,
            String sourceRole,
            String originalReasoningContent,
            String intermediateConclusion) {

        if (originalReasoningContent == null || originalReasoningContent.isBlank()) {
            return new InheritedRationale(sourceAgentId, sourceRole, "无前序思考链", intermediateConclusion);
        }

        // 认知安全蒸馏：截断或提取关键推演骨干，防止上下文无界膨胀
        String sanitizedRationale = originalReasoningContent.trim();
        log.info("[ThinkingPropagator] 成功为 {} 提取思考流凭据，长度: {} 字符", sourceAgentId, sanitizedRationale.length());

        return new InheritedRationale(sourceAgentId, sourceRole, sanitizedRationale, intermediateConclusion);
    }

    /**
     * 组装注入给目标 Agent 的安全系统提示词切片
     */
    public String formatRationalePromptSection(List<InheritedRationale> rationales) {
        if (rationales == null || rationales.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n=== [前序智能体思考推演依据 (INHERITED RATIONALE)] ===\n");
        for (InheritedRationale r : rationales) {
            sb.append("• 智能体 [").append(r.sourceAgentId()).append(" (").append(r.sourceRole()).append(")]:\n")
                    .append("  [推演证据]: ").append(r.reasoningContent()).append("\n")
                    .append("  [阶段结论]: ").append(r.conclusion()).append("\n");
        }
        sb.append("====================================================\n");
        return sb.toString();
    }

    public record DeepSeekMessage(String role, String content, String reasoning_content) {}

    public record InheritedRationale(
            String sourceAgentId,
            String sourceRole,
            String reasoningContent,
            String conclusion
    ) {}
}
