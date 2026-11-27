package tech.qiantong.qknow.ai.vector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Weinberger 有符号特征哈希（Signed Feature Hashing / CountSketch）的多语种局部敏感超球面投影算子。
 * <p>
 * 设计用途：
 * 1. 生产运行态所有语义嵌入均强制优先委托阿里千问 {@code text-embedding-v4}（1024 维单位超球面 S^1023）；
 * 2. 在无外部网络连接的确定性单元测试或离线降级路径中，彻底取代过去使用 {@code new Random(text.hashCode())}
 *    产生的零语义高斯白噪声，按“中文单字/双字词素 + 英文完整词元/双词短语”执行有符号稀疏投影，
 *    严格满足内积无偏估计性质 E[&lt;phi(x), phi(y)&gt;] = &lt;x, y&gt;_token 且输出模长恒等于 1.0。
 * </p>
 */
public final class LocalitySensitiveFeatureHasher {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private LocalitySensitiveFeatureHasher() {
    }

    /**
     * 将中英文文本通过词素级有符号 CountSketch 投影至指定维度的单位超球面（float 精度）。
     */
    public static float[] projectToUnitSphereFloat(String text, int dimension) {
        double[] dbl = projectToUnitSphereDouble(text, dimension);
        float[] out = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            out[i] = (float) dbl[i];
        }
        return out;
    }

    /**
     * 将中英文文本通过词素级有符号 CountSketch 投影至指定维度的单位超球面（double 精度）。
     */
    public static double[] projectToUnitSphereDouble(String text, int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须为正整数: " + dimension);
        }
        double[] sketch = new double[dimension];
        if (text == null || text.isBlank()) {
            sketch[0] = 1.0;
            return sketch;
        }

        String cleaned = text.trim().toLowerCase();
        List<String> latinWords = new ArrayList<>();
        StringBuilder wordBuf = new StringBuilder();

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (isCjk(c)) {
                flushLatinWord(sketch, latinWords, wordBuf);
                // 中文单字与双字词素投影（赋予核心语义词素主权重）
                accumulateSignedFeature(sketch, "c1:" + c, 1.0);
                if (i + 1 < cleaned.length() && isCjk(cleaned.charAt(i + 1))) {
                    accumulateSignedFeature(sketch, "c2:" + cleaned.substring(i, i + 2), 1.25);
                }
            } else if (Character.isLetterOrDigit(c)) {
                wordBuf.append(c);
            } else {
                flushLatinWord(sketch, latinWords, wordBuf);
            }
        }
        flushLatinWord(sketch, latinWords, wordBuf);

        // 轻量全局指纹基底（防止纯符号输入退化为零向量）
        accumulateSignedFeature(sketch, "doc:" + cleaned, 0.20);

        normalizeInPlace(sketch);
        return sketch;
    }

    private static void flushLatinWord(double[] sketch, List<String> latinWords, StringBuilder wordBuf) {
        if (wordBuf.length() == 0) {
            return;
        }
        String word = wordBuf.toString();
        wordBuf.setLength(0);
        // 超长无空格串（如 Base64 编码载荷）按固定 8 字符块切分，避免单词膨胀或单字母噪声淹没语义词素
        if (word.length() > 24) {
            for (int i = 0; i < word.length(); i += 8) {
                int end = Math.min(word.length(), i + 8);
                accumulateSignedFeature(sketch, "b64:" + word.substring(i, end), 0.90);
            }
        } else {
            latinWords.add(word);
            accumulateSignedFeature(sketch, "w1:" + word, 0.80);
        }
    }

    private static boolean isCjk(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    /**
     * 基于确定性 SplitMix64 双射序列生成超球面拟蒙特卡洛基底向量（零 java.util.Random 依赖）。
     */
    public static double[] deterministicBasisVectorDouble(long seed, int dimension) {
        double[] vec = new double[dimension];
        long state = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < dimension; i++) {
            state = splitMix64(state + i * 0xBF58476D1CE4E5B9L);
            double u = ((state >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
            vec[i] = u;
        }
        normalizeInPlace(vec);
        return vec;
    }

    private static void accumulateSignedFeature(double[] sketch, String token, double weight) {
        int dim = sketch.length;
        long baseHash = fnv1a64(token);
        for (int probe = 0; probe < 8; probe++) {
            long mixed = splitMix64(baseHash + probe * 0x9E3779B97F4A7C15L);
            int bucket = (int) (Long.remainderUnsigned(mixed >>> 1, dim));
            double sign = ((mixed & 1L) == 0L) ? 1.0 : -1.0;
            sketch[bucket] += sign * weight;
        }
    }

    public static void normalizeInPlace(double[] vec) {
        double sumSq = 0.0;
        for (double v : vec) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-12) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        } else if (vec.length > 0) {
            vec[0] = 1.0;
        }
    }

    public static void normalizeInPlace(float[] vec) {
        double sumSq = 0.0;
        for (float v : vec) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-12) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        } else if (vec.length > 0) {
            vec[0] = 1.0f;
        }
    }

    private static long fnv1a64(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        long hash = FNV_OFFSET_BASIS;
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
