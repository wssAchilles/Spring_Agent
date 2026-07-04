package tech.qiantong.qknow.module.kmc.service.rag.sim;

import lombok.extern.slf4j.Slf4j;

/**
 * Rust SIMD 向量计算 JNI 桥接类
 * [溯源] 算法优化指南 Phase 3: 向量相似度 Rust SIMD
 *
 * 使用 Rust SIMD 优化的批量向量相似度计算
 * 降级策略：JNI 加载失败时回退到 Java 计算
 */
@Slf4j
public class VecSimNative {

    private static volatile boolean loaded = false;

    static {
        try {
            System.loadLibrary("vecsim_jni");
            loaded = true;
            log.info("vecsim-jni library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
            log.warn("vecsim-jni library not found, falling back to Java computation: {}", e.getMessage());
        }
    }

    /**
     * 批量 cosine 相似度计算
     * @param query 1536-dim query vector
     * @param corpus N * 1536 flattened corpus vectors
     * @param dim 向量维度
     * @return N 个相似度分数
     */
    public static native float[] cosineBatch(float[] query, float[] corpus, int dim);

    /**
     * 批量 inner product 计算
     * @param query 1536-dim query vector
     * @param corpus N * 1536 flattened corpus vectors
     * @param dim 向量维度
     * @return N 个内积分数
     */
    public static native float[] innerProductBatch(float[] query, float[] corpus, int dim);

    /**
     * 检查 JNI 库是否可用
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * 安全 cosine 计算：JNI 不可用时返回 null
     */
    public static float[] safeCosineBatch(float[] query, float[] corpus, int dim) {
        if (!loaded || query == null || corpus == null) {
            return null;
        }
        try {
            return cosineBatch(query, corpus, dim);
        } catch (Exception e) {
            log.debug("vecsim cosine failed: {}", e.getMessage());
            return null;
        }
    }
}
