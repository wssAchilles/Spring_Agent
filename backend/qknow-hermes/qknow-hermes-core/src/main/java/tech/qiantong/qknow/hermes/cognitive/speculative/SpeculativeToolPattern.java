package tech.qiantong.qknow.hermes.cognitive.speculative;

import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.util.Objects;

/**
 * 安全格约束的模式感知投机工具模式元组 {@code P = (C, T, f, p)}（Java 21 Record）。
 */
public record SpeculativeToolPattern(
        String patternId,
        String contextSignature,
        String targetToolName,
        double confidence,
        ToolSideEffectTier sideEffectTier
) {
    public SpeculativeToolPattern {
        Objects.requireNonNull(patternId, "patternId 不能为空");
        Objects.requireNonNull(contextSignature, "contextSignature 不能为空");
        Objects.requireNonNull(targetToolName, "targetToolName 不能为空");
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        sideEffectTier = sideEffectTier == null ? ToolSideEffectTier.IRREVERSIBLE : sideEffectTier;
    }
}
