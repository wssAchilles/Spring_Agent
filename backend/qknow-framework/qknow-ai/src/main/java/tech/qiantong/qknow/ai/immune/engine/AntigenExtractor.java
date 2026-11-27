package tech.qiantong.qknow.ai.immune.engine;

import org.springframework.ai.embedding.EmbeddingModel;
import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 抗原多维特征提取引擎
 * <p>
 * 提取输入文本的千问 1024 维超球面单位向量、64 位 SimHash 词法位图与语法控制风险得分。
 *
 * @author Achilles
 * @since Phase 45
 */
public class AntigenExtractor {

    public static final int EMBEDDING_DIM = 1024;

    private final EmbeddingModel embeddingModel;

    public AntigenExtractor() {
        this(null);
    }

    public AntigenExtractor(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    private static final Pattern BASE64_PATTERN = Pattern.compile("([A-Za-z0-9+/]{4}){6,}={0,2}");
    private static final Pattern INJECTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(ignore\\\\s+previous\\\\s+instructions|system\\\\s+prompt|roleplay|hypnosis|jailbreak|bypass\\\\s+rules|管理员指令|忽略上述指令|越狱|开发者模式)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\\\s\\\\S]*?```");

    /**
     * 提取多维抗原特征
     *
     * @param rawText 待检测输入文本
     * @return 结构化抗原特征 DTO
     */
    public AntigenDTO extractAntigen(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new AntigenDTO("", new float[EMBEDDING_DIM], 0L, 0.0);
        }

        float[] embedding = computeDeterministicQwenEmbedding(rawText);
        long simHash = computeSimHash64(rawText);
        double riskScore = evaluateSyntaxRisk(rawText);

        return new AntigenDTO(rawText, embedding, simHash, riskScore);
    }

    /**
     * 计算千问 1024 维单位超球面 S^1023 嵌入向量（保证模长精确为 1.0）。
     * 若已注入真实千问 {@link EmbeddingModel} 则优先远程调用；离线测试下采用 Weinberger 有符号特征哈希投影。
     */
    public float[] computeDeterministicQwenEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIM];
        }
        if (embeddingModel != null) {
            float[] raw = embeddingModel.embed(text);
            if (raw != null && raw.length == EMBEDDING_DIM) {
                float[] copy = Arrays.copyOf(raw, EMBEDDING_DIM);
                LocalitySensitiveFeatureHasher.normalizeInPlace(copy);
                return copy;
            }
        }
        return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(text, EMBEDDING_DIM);
    }

    /**
     * 计算 64 位 SimHash 词法位图
     */
    public long computeSimHash64(String text) {
        int[] v = new int[64];
        List<String> grams = extractNgrams(text);
        for (String gram : grams) {
            long hash = fnv1a64(gram);
            for (int i = 0; i < 64; i++) {
                if (((hash >> i) & 1L) == 1L) {
                    v[i]++;
                } else {
                    v[i]--;
                }
            }
        }
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (v[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 提取中英文混排的词素与双字特征列表
     */
    public List<String> extractNgrams(String text) {
        List<String> grams = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return grams;
        }
        String cleaned = text.toLowerCase();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5) {
                if (word.length() > 0) {
                    grams.add(word.toString());
                    word.setLength(0);
                }
                grams.add(String.valueOf(c));
                if (i + 1 < cleaned.length() && cleaned.charAt(i + 1) >= 0x4e00 && cleaned.charAt(i + 1) <= 0x9fa5) {
                    grams.add(cleaned.substring(i, i + 2));
                }
            } else if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else {
                if (word.length() > 0) {
                    grams.add(word.toString());
                    word.setLength(0);
                }
            }
        }
        if (word.length() > 0) {
            grams.add(word.toString());
        }
        return grams;
    }

    /**
     * 评估语法与越狱指令风险得分
     */
    public double evaluateSyntaxRisk(String text) {
        double score = 0.0;
        if (INJECTION_KEYWORD_PATTERN.matcher(text).find()) {
            score += 0.50;
        }
        if (BASE64_PATTERN.matcher(text).find()) {
            score += 0.30;
        }
        if (CODE_BLOCK_PATTERN.matcher(text).find() && text.toLowerCase().contains("eval(")) {
            score += 0.30;
        }
        if (text.contains("[REDACTED]") || text.contains("<!-- hidden")) {
            score += 0.20;
        }
        return Math.min(1.0, score);
    }

    private long fnv1a64(String str) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < str.length(); i++) {
            hash ^= str.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
