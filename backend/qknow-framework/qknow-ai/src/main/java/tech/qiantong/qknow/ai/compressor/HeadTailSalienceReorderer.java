package tech.qiantong.qknow.ai.compressor;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首尾注意力显著性重排与 64-Token 规整前缀缓存对齐器 (定理 1.4 & Lost-in-the-Middle 抑制)
 */
@Component
public class HeadTailSalienceReorderer {

    private static final int CHUNK_SIZE = 64; // DeepSeek 官方 64-Token 规整块

    /**
     * 格式化并组装具有高前缀缓存复用率的长上下文 Prompt
     */
    public String buildAlignedPrompt(
            String systemBasePrompt,
            List<String> symbolicRules,
            String compressedContext,
            String currentQuery
    ) {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("[System Base Instructions]\n");
        if (systemBasePrompt != null && !systemBasePrompt.isBlank()) {
            headerBuilder.append(systemBasePrompt.trim()).append("\n");
        }

        if (symbolicRules != null && !symbolicRules.isEmpty()) {
            headerBuilder.append("[Immutable Symbolic Directives]\n");
            for (String rule : symbolicRules) {
                headerBuilder.append("- ").append(rule).append("\n");
            }
        }

        String rawHeader = headerBuilder.toString();
        // 64-Token 规整对齐 (通过受控空白/注释填充使前缀 Token 满足 64 整数倍)
        String alignedHeader = alignTo64Tokens(rawHeader);

        // 组装最终长 Prompt: [首部规整头部] -> [中间压缩上下文] -> [尾部当前Query与动态变量]
        StringBuilder prompt = new StringBuilder();
        prompt.append(alignedHeader).append("\n\n");

        prompt.append("=== [Retained Knowledge & Context] ===\n");
        if (compressedContext != null && !compressedContext.isBlank()) {
            prompt.append(compressedContext.trim()).append("\n\n");
        }

        prompt.append("=== [Current User Query] ===\n");
        prompt.append(currentQuery != null ? currentQuery.trim() : "").append("\n");

        return prompt.toString();
    }

    /**
     * 确保前缀头部对齐为 64-Token 整数倍
     */
    public String alignTo64Tokens(String text) {
        if (text == null) return "";
        int tokens = NeuroSymbolicContextCompressor.estimateTokenCount(text);
        int remainder = tokens % CHUNK_SIZE;
        if (remainder == 0) {
            return text;
        }

        int targetTokens = tokens + (CHUNK_SIZE - remainder);
        StringBuilder sb = new StringBuilder(text);
        sb.append("\n<!-- aligned_padding: ");
        while (NeuroSymbolicContextCompressor.estimateTokenCount(sb.toString() + " -->") < targetTokens) {
            sb.append("0");
        }
        sb.append(" -->");

        return sb.toString();
    }
}
