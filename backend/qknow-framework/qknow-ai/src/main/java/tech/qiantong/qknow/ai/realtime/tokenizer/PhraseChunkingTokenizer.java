package tech.qiantong.qknow.ai.realtime.tokenizer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 40: 标点符号与短语双门限流式微分块分词器 (Theorem 1.1)
 * 用于将 DeepSeek 流式 Token 按自然语义标点或字数阈值切分为微块，供下游 TTS 提前合成。
 *
 * @author qknow
 */
@Component
public class PhraseChunkingTokenizer {

    public static final int MIN_CHUNK_CHARS = 4;   // 最小切片字符数
    public static final int MAX_CHUNK_CHARS = 10;  // 标点未出现时的硬保底切片门限

    // 自然停顿与呼吸边界标点符号
    private static final String DELIMITERS = "，,。！？!?；;\n";

    private final Map<String, StringBuilder> sessionBuffers = new ConcurrentHashMap<>();

    /**
     * 流式喂入 Token，若达到短语标点或长度边界，即刻切块返回
     */
    public List<String> feedToken(String sessionId, String token) {
        List<String> chunks = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return chunks;
        }

        StringBuilder buffer = sessionBuffers.computeIfAbsent(sessionId, k -> new StringBuilder());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            buffer.append(c);

            boolean isDelimiter = DELIMITERS.indexOf(c) != -1;
            boolean lengthExceeded = buffer.length() >= MAX_CHUNK_CHARS;

            if ((isDelimiter && buffer.length() >= MIN_CHUNK_CHARS) || lengthExceeded) {
                String chunk = buffer.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                buffer.setLength(0);
            }
        }

        return chunks;
    }

    /**
     * 句子结束冲刷剩余缓冲区
     */
    public String flush(String sessionId) {
        StringBuilder buffer = sessionBuffers.remove(sessionId);
        if (buffer != null && buffer.length() > 0) {
            String remaining = buffer.toString().trim();
            return remaining.isEmpty() ? null : remaining;
        }
        return null;
    }

    public void clear(String sessionId) {
        sessionBuffers.remove(sessionId);
    }
}
