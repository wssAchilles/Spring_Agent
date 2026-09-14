package tech.qiantong.qknow.ai.deepseek.prefix;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 前缀对齐度检查与级联哈希模拟分析器 (PrefixAlignmentInspector)
 *
 * @author qknow
 */
@Slf4j
@Component
public class PrefixAlignmentInspector {

    @Data
    @Builder
    public static class AlignmentInspectionReport {
        private int totalBlocks;
        private int alignedStaticBlocks;
        private double estimatedCacheHitRate;
        private boolean isAvalancheFree;
        private List<String> blockHashes;
    }

    /**
     * 模拟 DeepSeek 服务端以 64-Token (约 192 字符) 划分块并计算级联滚动哈希
     */
    public List<String> simulateCascadedBlockHashes(List<Message> messages) {
        StringBuilder fullText = new StringBuilder();
        for (Message msg : messages) {
            fullText.append(msg.getText()).append("\n");
        }

        String text = fullText.toString();
        int blockSizeChars = PrefixCacheAligner.DEEPSEEK_CACHE_BLOCK_SIZE * 3; // 近似一个 block 的字符数
        List<String> hashes = new ArrayList<>();

        String prevHash = "INIT_SEED_000000000000000000000000";
        for (int i = 0; i < text.length(); i += blockSizeChars) {
            int end = Math.min(i + blockSizeChars, text.length());
            String chunk = text.substring(i, end);
            prevHash = sha256Hex(prevHash + ":" + chunk);
            hashes.add(prevHash);
        }

        return hashes;
    }

    /**
     * 计算两个请求之间的前缀块最长公共前缀 (LCP) 块数
     */
    public int calculateLongestMatchingBlockCount(List<String> hashesA, List<String> hashesB) {
        int match = 0;
        int minLen = Math.min(hashesA.size(), hashesB.size());
        for (int i = 0; i < minLen; i++) {
            if (hashesA.get(i).equals(hashesB.get(i))) {
                match++;
            } else {
                break; // 级联哈希只要一个不匹配立即断开，前缀雪崩
            }
        }
        return match;
    }

    /**
     * 针对对齐 Payload 生成审查报告
     */
    public AlignmentInspectionReport inspectAlignment(PrefixCacheAligner.AlignedPromptPayload payload) {
        List<String> hashes = simulateCascadedBlockHashes(payload.getAlignedMessages());
        int staticTokens = payload.getTargetAlignedTokens();
        int alignedBlocks = staticTokens / PrefixCacheAligner.DEEPSEEK_CACHE_BLOCK_SIZE;

        int totalTokens = staticTokens + PrefixCacheAligner.estimateTokens(payload.getDynamicTailText());
        double hitRate = (double) staticTokens / Math.max(1, totalTokens);

        return AlignmentInspectionReport.builder()
                .totalBlocks(hashes.size())
                .alignedStaticBlocks(alignedBlocks)
                .estimatedCacheHitRate(hitRate)
                .isAvalancheFree(alignedBlocks > 0)
                .blockHashes(hashes)
                .build();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
