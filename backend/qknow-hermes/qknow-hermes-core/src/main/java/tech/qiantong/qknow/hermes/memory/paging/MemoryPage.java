package tech.qiantong.qknow.hermes.memory.paging;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 虚拟内存分层页表模型 (MemoryPage)
 * <p>
 * 遵循现代操作系统虚拟内存分页体系与不可变数据规范，将大模型长程上下文抽象为
 * 常驻内核页 (CorePage)、递归压缩摘要页 (CompactedPage) 与时序交互快轨页 (RecallPage)。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public sealed interface MemoryPage extends Serializable permits MemoryPage.CorePage, MemoryPage.CompactedPage, MemoryPage.RecallPage {

    int pageId();
    long timestamp();
    String pageSignature();
    int estimatedTokens();
    boolean verifySignature(String secretKey);

    /**
     * 常驻核心内核页 (CorePage)
     * <p>
     * 锁定在 Token 0 起始处，承载安全宪章、系统级角色指令与不可变业务约束。
     * 自动补齐至 64 整数倍字符/Token，确保 DeepSeek 硬盘上下文缓存 (Context Caching) 稳定命中。
     * </p>
     */
    record CorePage(
            int pageId,
            String systemInstructions,
            Map<String, String> invariantConstraints,
            int alignedPaddingTokens,
            long timestamp,
            String pageSignature
    ) implements MemoryPage {

        public CorePage {
            Objects.requireNonNull(systemInstructions, "systemInstructions 不能为空");
            invariantConstraints = invariantConstraints != null ? Map.copyOf(invariantConstraints) : Map.of();
            Objects.requireNonNull(pageSignature, "pageSignature 不能为空");
        }

        @Override
        public int estimatedTokens() {
            int raw = (systemInstructions.length() / 3) + invariantConstraints.size() * 8;
            int remainder = raw % 64;
            int pad = (remainder == 0) ? 0 : (64 - remainder);
            return raw + pad;
        }

        @Override
        public boolean verifySignature(String secretKey) {
            String payload = String.format("CORE|%d|%s|%d|%s", pageId, systemInstructions, timestamp, secretKey);
            return safeConstantTimeEquals(pageSignature, computeSha256(payload));
        }

        public static CorePage createAndSign(int pageId, String instructions, Map<String, String> constraints, String secretKey) {
            long now = System.currentTimeMillis();
            String payload = String.format("CORE|%d|%s|%d|%s", pageId, instructions, now, secretKey);
            String sig = computeSha256(payload);
            return new CorePage(pageId, instructions, constraints, 0, now, sig);
        }
    }

    /**
     * 递归压缩摘要页 (CompactedPage)
     * <p>
     * 在上下文触及高水位线 (>=75%) 时，由递归压缩引擎提炼沉淀的因果历史简报与未决任务列表。
     * </p>
     */
    record CompactedPage(
            int pageId,
            String historicalSummary,
            List<String> activePendingGoals,
            long timestamp,
            String pageSignature
    ) implements MemoryPage {

        public CompactedPage {
            Objects.requireNonNull(historicalSummary, "historicalSummary 不能为空");
            activePendingGoals = activePendingGoals != null ? List.copyOf(activePendingGoals) : List.of();
            Objects.requireNonNull(pageSignature, "pageSignature 不能为空");
        }

        @Override
        public int estimatedTokens() {
            return (historicalSummary.length() / 3) + activePendingGoals.size() * 10;
        }

        @Override
        public boolean verifySignature(String secretKey) {
            String payload = String.format("COMPACT|%d|%s|%d|%s", pageId, historicalSummary, timestamp, secretKey);
            return safeConstantTimeEquals(pageSignature, computeSha256(payload));
        }

        public static CompactedPage createAndSign(int pageId, String summary, List<String> goals, String secretKey) {
            long now = System.currentTimeMillis();
            String payload = String.format("COMPACT|%d|%s|%d|%s", pageId, summary, now, secretKey);
            String sig = computeSha256(payload);
            return new CompactedPage(pageId, summary, goals, now, sig);
        }
    }

    /**
     * 活跃时序交互快轨页 (RecallPage)
     * <p>
     * 承载当前会话最近活跃的单次回合交互，挂载阿里千问 1536 维超球面单位向量。
     * </p>
     */
    record RecallPage(
            int pageId,
            String role,
            String content,
            float[] sphericalVector, // 阿里千问 1536 维单位向量 (||v||_2 = 1.0)
            Map<String, String> episodicFacts,
            long timestamp,
            String pageSignature
    ) implements MemoryPage {

        public RecallPage {
            Objects.requireNonNull(role, "role 不能为空");
            Objects.requireNonNull(content, "content 不能为空");
            Objects.requireNonNull(sphericalVector, "sphericalVector 不能为空");
            if (sphericalVector.length != 1536) {
                throw new IllegalArgumentException("超球面向量必须严格为阿里千问 1536 维");
            }
            sphericalVector = sphericalVector.clone();
            episodicFacts = episodicFacts != null ? Map.copyOf(episodicFacts) : Map.of();
            Objects.requireNonNull(pageSignature, "pageSignature 不能为空");
        }

        @Override
        public int estimatedTokens() {
            return (content.length() / 3) + episodicFacts.size() * 6;
        }

        @Override
        public boolean verifySignature(String secretKey) {
            String payload = String.format("RECALL|%d|%s|%s|%d|%s", pageId, role, content, timestamp, secretKey);
            return safeConstantTimeEquals(pageSignature, computeSha256(payload));
        }

        public static RecallPage createAndSign(int pageId, String role, String content, float[] vector, Map<String, String> facts, String secretKey) {
            long now = System.currentTimeMillis();
            String payload = String.format("RECALL|%d|%s|%s|%d|%s", pageId, role, content, now, secretKey);
            String sig = computeSha256(payload);
            return new RecallPage(pageId, role, content, vector, facts, now, sig);
        }
    }

    // ==================== 密码学常量时间与 SHA-256 辅助 ====================

    private static boolean safeConstantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 签名计算异常", e);
        }
    }
}
