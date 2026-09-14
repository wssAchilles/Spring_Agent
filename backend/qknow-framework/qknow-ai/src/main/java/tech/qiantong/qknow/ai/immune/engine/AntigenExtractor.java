package tech.qiantong.qknow.ai.immune.engine;

import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 抗原多维特征提取引擎
 * <p>
 * 提取输入文本的千问 1536 维超球面单位向量、64 位 SimHash 词法位图与语法控制风险得分。
 *
 * @author Achilles
 * @since Phase 45
 */
public class AntigenExtractor {

    public static final int EMBEDDING_DIM = 1536;

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
     * 计算确定性局部敏感千问 1536 维超球面单位嵌入（保证模长精确为 1.0）
     * 采用词袋与局部敏感正态基底叠加，同源文本天然具备高余弦相似度
     */
    public float[] computeDeterministicQwenEmbedding(String text) {
        float[] vector = new float[EMBEDDING_DIM];
        if (text == null || text.trim().isEmpty()) {
            return vector;
        }

        // 提取中英文混排与双字 n-grams
        List<String> grams = extractNgrams(text);

        // 1. 局部敏感词汇/字叠加
        for (String gram : grams) {
            long tokenSeed = fnv1a64(gram);
            java.util.Random tokenRng = new java.util.Random(tokenSeed);
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                vector[i] += (float) tokenRng.nextGaussian();
            }
        }

        // 2. 混合少量全局文本指纹基底 (权重 0.2)，提供唯一性支撑
        long globalSeed = fnv1a64(text.toLowerCase());
        java.util.Random globalRng = new java.util.Random(globalSeed);
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            vector[i] += (float) (0.2 * globalRng.nextGaussian());
        }

        // 3. L2 范数精确归一化至 1535 维超球面
        double normSq = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            normSq += vector[i] * vector[i];
        }
        double norm = Math.sqrt(normSq);
        if (norm > 1e-12) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
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
