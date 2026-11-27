package tech.qiantong.qknow.hermes.prefix;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DeepSeek 64-Token 块前缀缓存不变性审计与常量时间验签器。
 * <p>
 * 严格遵循 AGENTS.md §7.5 零时序侧信道泄漏铁律：所有摘要比对与 HMAC 验签统一使用 {@link MessageDigest#isEqual(byte[], byte[])}。
 * </p>
 */
public class PrefixCacheStabilityAuditor {

    public static final int DEEPSEEK_CACHE_BLOCK_TOKENS = 64;
    private final byte[] secretKeyBytes;
    private final AtomicLong totalAudits = new AtomicLong(0L);
    private final AtomicLong preservedCount = new AtomicLong(0L);
    private final AtomicLong driftedCount = new AtomicLong(0L);
    private final AtomicInteger lastAligned64TokenBlocks = new AtomicInteger(0);
    private final AtomicReference<String> lastPrefixHashHex = new AtomicReference<>("");

    public PrefixCacheStabilityAuditor(String secretKey) {
        String effective = (secretKey == null || secretKey.isBlank())
                ? "hermes-default-prefix-audit-secret"
                : secretKey;
        this.secretKeyBytes = effective.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 审计单轮请求的前缀哈希不变性并签发不可变凭单。
     *
     * @param requestId                请求唯一标识
     * @param roundIndex               当前推理或反思轮次（从 1 开始）
     * @param systemPrompt             不可变系统提示词
     * @param canonicalTopLevelToolIds 顶层锁定的静态元工具标识列表
     * @param baselineSha256           首轮基线 SHA-256 摘要（首轮传 null 表示建立基线）
     * @return 前缀稳定性密码学凭单
     */
    public PrefixCacheStabilityReceipt auditRound(
            String requestId,
            int roundIndex,
            String systemPrompt,
            List<String> canonicalTopLevelToolIds,
            byte[] baselineSha256
    ) {
        Objects.requireNonNull(requestId, "requestId 不能为空");
        String safePrompt = systemPrompt != null ? systemPrompt : "";
        String joinedTools = canonicalTopLevelToolIds != null ? String.join("|", canonicalTopLevelToolIds) : "";
        String canonicalPrefixPayload = safePrompt + "\n@@STATIC_TOOLS@@\n" + joinedTools;

        byte[] currentSha256 = sha256(canonicalPrefixPayload.getBytes(StandardCharsets.UTF_8));
        boolean preserved = (baselineSha256 == null) || MessageDigest.isEqual(baselineSha256, currentSha256);

        int estimatedTokens = estimateTokenCount(canonicalPrefixPayload);
        int alignedBlocks = Math.max(0, estimatedTokens / DEEPSEEK_CACHE_BLOCK_TOKENS);

        totalAudits.incrementAndGet();
        if (preserved) {
            preservedCount.incrementAndGet();
        } else {
            driftedCount.incrementAndGet();
        }
        lastAligned64TokenBlocks.set(alignedBlocks);
        lastPrefixHashHex.set(toHexPrefix(currentSha256));

        byte[] signature = computeHmac(requestId, roundIndex, currentSha256, alignedBlocks, preserved);
        return new PrefixCacheStabilityReceipt(
                requestId,
                roundIndex,
                currentSha256,
                alignedBlocks,
                preserved,
                signature
        );
    }

    /**
     * 获取前缀缓存不变性运行态聚合遥测。
     */
    public Map<String, Object> getTelemetryStats() {
        long total = totalAudits.get();
        long preserved = preservedCount.get();
        double rate = total == 0L ? 1.0 : (double) preserved / total;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAudits", total);
        stats.put("preservedCount", preserved);
        stats.put("driftedCount", driftedCount.get());
        stats.put("preservationRate", rate);
        stats.put("lastAligned64TokenBlocks", lastAligned64TokenBlocks.get());
        stats.put("lastPrefixHash", lastPrefixHashHex.get());
        return Collections.unmodifiableMap(stats);
    }

    private static String toHexPrefix(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(bytes.length, 8) * 2);
        for (int i = 0; i < Math.min(bytes.length, 8); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    /**
     * 常量时间校验前缀凭单签名完整性，防止篡改伪造缓存命中指标。
     */
    public boolean verifyReceipt(PrefixCacheStabilityReceipt receipt) {
        if (receipt == null) {
            return false;
        }
        byte[] expected = computeHmac(
                receipt.requestId(),
                receipt.roundIndex(),
                receipt.systemAndToolsSha256(),
                receipt.aligned64TokenBlocks(),
                receipt.prefixPreserved()
        );
        return MessageDigest.isEqual(expected, receipt.hmacSignature());
    }

    /**
     * 估算中英文混排上下文的 DeepSeek 分词 Token 数（确保至少精确识别 64-token 块级规模）。
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int hanCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                hanCount++;
            } else {
                otherCount++;
            }
        }
        return (int) Math.ceil(hanCount * 0.65 + otherCount * 0.28);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private byte[] computeHmac(
            String requestId,
            int roundIndex,
            byte[] systemAndToolsSha256,
            int alignedBlocks,
            boolean preserved
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            byte[] reqBytes = requestId.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(reqBytes.length + 4 + systemAndToolsSha256.length + 4 + 1);
            buffer.put(reqBytes);
            buffer.putInt(roundIndex);
            buffer.put(systemAndToolsSha256);
            buffer.putInt(alignedBlocks);
            buffer.put((byte) (preserved ? 1 : 0));
            return mac.doFinal(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 计算失败", e);
        }
    }
}
