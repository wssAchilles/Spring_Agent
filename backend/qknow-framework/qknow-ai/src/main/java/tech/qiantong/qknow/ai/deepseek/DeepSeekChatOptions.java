package tech.qiantong.qknow.ai.deepseek;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 专用对话选项，对齐官方最新 API 规范：
 * 包含思考模式开关 (thinking)、思考力度 (reasoning_effort) 与原生工具定义 (tools)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekChatOptions implements ChatOptions {

    /**
     * 目标模型名称，默认建议为 deepseek-flash
     */
    private String model;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 核采样概率
     */
    private Double topP;

    /**
     * 最大输出 Token 数
     */
    private Integer maxTokens;

    /**
     * 存在惩罚
     */
    private Double presencePenalty;

    /**
     * 频率惩罚
     */
    private Double frequencyPenalty;

    /**
     * 停止序列
     */
    private List<String> stopSequences;

    /**
     * Top-K 采样
     */
    private Integer topK;

    /**
     * 思考模式开关: true 为 {"type": "enabled"}，false 为 {"type": "disabled"}
     */
    private Boolean thinkingEnabled;

    /**
     * 思考力度: "low", "medium", "high"
     */
    private String reasoningEffort;

    /**
     * 工具定义列表
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具选择策略: "auto", "none", "required" 或具体函数 Map
     */
    private Object toolChoice;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChatOptions> T copy() {
        return (T) DeepSeekChatOptions.builder()
                .model(this.model)
                .temperature(this.temperature)
                .topP(this.topP)
                .maxTokens(this.maxTokens)
                .presencePenalty(this.presencePenalty)
                .frequencyPenalty(this.frequencyPenalty)
                .stopSequences(this.stopSequences != null ? new ArrayList<>(this.stopSequences) : null)
                .topK(this.topK)
                .thinkingEnabled(this.thinkingEnabled)
                .reasoningEffort(this.reasoningEffort)
                .tools(this.tools != null ? new ArrayList<>(this.tools) : null)
                .toolChoice(this.toolChoice)
                .build();
    }
}
