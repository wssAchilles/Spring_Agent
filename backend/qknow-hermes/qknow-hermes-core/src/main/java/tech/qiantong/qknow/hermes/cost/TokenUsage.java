package tech.qiantong.qknow.hermes.cost;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 使用统计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    private long promptTokens;
    private long completionTokens;

    public long getTotalTokens() {
        return promptTokens + completionTokens;
    }
}
