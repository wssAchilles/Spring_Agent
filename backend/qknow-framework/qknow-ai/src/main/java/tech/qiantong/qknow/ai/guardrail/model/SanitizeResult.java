package tech.qiantong.qknow.ai.guardrail.model;

/**
 * PII 脱敏结果封装
 *
 * @param sanitizedText 脱敏后的安全文本
 * @param modified 是否发生了脱敏替换
 * @param hitCount 命中的敏感信息实体数量
 * @param latencyMicros 处理耗时（微秒）
 *
 * @author qknow
 */
public record SanitizeResult(
        String sanitizedText,
        boolean modified,
        int hitCount,
        long latencyMicros
) {
    public SanitizeResult(String sanitizedText, boolean modified, int hitCount) {
        this(sanitizedText, modified, hitCount, 0L);
    }
}
