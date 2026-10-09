package tech.qiantong.qknow.hermes.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 纯 Java 21 Record 格式模型解析不可变存证凭单
 * 符合企业级高可用与密码学零时序泄漏工程铁律
 *
 * @author Achilles
 * @since Phase 153
 */
public record ModelResolutionReceipt(
        String requestedPlatform,
        String requestedModel,
        String resolvedModel,
        ResolutionType resolutionType,
        long timestampEpochMs,
        String signatureDigest
) {

    public enum ResolutionType {
        EXACT_MATCH,       // 精确直通
        LOGICAL_ALIAS,     // 逻辑别名映射（primary, fast 等）
        LEGACY_UPGRADE,    // 历史废弃模型透明向后兼容升级（deepseek-chat 等）
        DEFAULT_FALLBACK   // 输入为空或空白安全兜底
    }

    /**
     * 工厂构建方法，自动生成 SHA-256 自验真哈希串
     */
    public static ModelResolutionReceipt of(String requestedPlatform, String requestedModel,
                                           String resolvedModel, ResolutionType resolutionType) {
        long timestamp = System.currentTimeMillis();
        String payload = (requestedPlatform != null ? requestedPlatform : "") + "|"
                + (requestedModel != null ? requestedModel : "") + "|"
                + (resolvedModel != null ? resolvedModel : "") + "|"
                + resolutionType.name() + "|"
                + timestamp;
        String digest = computeSha256(payload);
        return new ModelResolutionReceipt(requestedPlatform, requestedModel, resolvedModel,
                resolutionType, timestamp, digest);
    }

    /**
     * 密码学防侧信道常量时间自验真比对
     */
    public boolean verifyReceipt() {
        String payload = (requestedPlatform != null ? requestedPlatform : "") + "|"
                + (requestedModel != null ? requestedModel : "") + "|"
                + (resolvedModel != null ? resolvedModel : "") + "|"
                + resolutionType.name() + "|"
                + timestampEpochMs;
        String expectedDigest = computeSha256(payload);
        byte[] expectedBytes = expectedDigest.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.signatureDigest.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 必须支持 SHA-256 算法", e);
        }
    }
}
