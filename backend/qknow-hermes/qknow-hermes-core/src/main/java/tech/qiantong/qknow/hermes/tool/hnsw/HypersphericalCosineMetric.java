package tech.qiantong.qknow.hermes.tool.hnsw;

/**
 * 阿里千问 1536 维超球面单位向量余弦度量与 SIMD 优化器
 * 满足 ||v||_2 = 1.0 几何恒等式，计算角距离 D = 1.0 - (u · v)
 */
public final class HypersphericalCosineMetric {

    public static final int VECTOR_DIMENSION = 1536;

    private HypersphericalCosineMetric() {}

    /**
     * 计算两个已归一化千问超球面单位向量的角距离
     *
     * @param u 1536 维单位向量
     * @param v 1536 维单位向量
     * @return 角距离 [0.0, 2.0]，值越小越相似 (0 表示完全共线重合)
     */
    public static float angularDistance(float[] u, float[] v) {
        if (u == null || v == null || u.length != VECTOR_DIMENSION || v.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("向量必须为严格 1536 维千问单位超球面向量");
        }

        // 8 路循环展开优化 (1536 / 8 = 192 次严格整数循环，充分榨干 CPU 超标量流水线)
        float dot = 0.0f;
        for (int i = 0; i < VECTOR_DIMENSION; i += 8) {
            dot += u[i] * v[i]
                 + u[i + 1] * v[i + 1]
                 + u[i + 2] * v[i + 2]
                 + u[i + 3] * v[i + 3]
                 + u[i + 4] * v[i + 4]
                 + u[i + 5] * v[i + 5]
                 + u[i + 6] * v[i + 6]
                 + u[i + 7] * v[i + 7];
        }

        // 防截断保护 clamp 到 [-1.0, 1.0]
        float clampedDot = Math.max(-1.0f, Math.min(1.0f, dot));
        return 1.0f - clampedDot;
    }

    /**
     * 将 1536 维 float32 单位向量量化为 int8 标量 (SQ8 压缩 75% 内存)
     */
    public static byte[] quantizeSq8(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("向量必须为严格 1536 维千问单位超球面向量");
        }
        byte[] quantized = new byte[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            float val = Math.max(-1.0f, Math.min(1.0f, vector[i]));
            quantized[i] = (byte) Math.round(val * 127.0f);
        }
        return quantized;
    }

    /**
     * 从 SQ8 int8 标量快速解量化为 float32
     */
    public static float[] dequantizeSq8(byte[] quantized) {
        if (quantized == null || quantized.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("量化字节必须为严格 1536 字节");
        }
        float[] vector = new float[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            vector[i] = quantized[i] / 127.0f;
        }
        return vector;
    }

    /**
     * 在 SQ8 压缩态下计算非对称内积与角距离
     */
    public static float asymmetricAngularDistance(float[] queryFloat, byte[] targetSq8) {
        float dot = 0.0f;
        for (int i = 0; i < VECTOR_DIMENSION; i += 8) {
            dot += queryFloat[i] * (targetSq8[i] / 127.0f)
                 + queryFloat[i + 1] * (targetSq8[i + 1] / 127.0f)
                 + queryFloat[i + 2] * (targetSq8[i + 2] / 127.0f)
                 + queryFloat[i + 3] * (targetSq8[i + 3] / 127.0f)
                 + queryFloat[i + 4] * (targetSq8[i + 4] / 127.0f)
                 + queryFloat[i + 5] * (targetSq8[i + 5] / 127.0f)
                 + queryFloat[i + 6] * (targetSq8[i + 6] / 127.0f)
                 + queryFloat[i + 7] * (targetSq8[i + 7] / 127.0f);
        }
        float clampedDot = Math.max(-1.0f, Math.min(1.0f, dot));
        return 1.0f - clampedDot;
    }
}
