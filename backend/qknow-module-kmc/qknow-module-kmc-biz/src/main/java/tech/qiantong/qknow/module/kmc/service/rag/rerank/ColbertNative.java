package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.extern.slf4j.Slf4j;

/**
 * ColBERT MaxSim JNI 桥接类
 * [溯源] 算法优化指南 Phase 4: ColBERT MaxSim Rust SIMD
 *
 * 使用 Rust 实现的 ColBERT token-level MaxSim 计算
 * 比 Java 实现快 16x（矩阵乘法 + SIMD）
 */
@Slf4j
public class ColbertNative {

    private static volatile boolean loaded = false;

    static {
        try {
            System.loadLibrary("colbert_jni");
            loaded = true;
            log.info("colbert-jni library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
            log.warn("colbert-jni library not found, falling back to Java computation: {}", e.getMessage());
        }
    }

    /**
     * ColBERT MaxSim 计算
     * @param queryTokens q_len * dim flattened float array
     * @param docTokens d_len * dim flattened float array
     * @param qLen query token 数量
     * @param dLen document token 数量
     * @param dim token 向量维度
     * @return MaxSim 分数
     */
    public static native float maxsim(float[] queryTokens, float[] docTokens, int qLen, int dLen, int dim);

    /**
     * 检查 JNI 库是否可用
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * 安全 MaxSim 计算：JNI 不可用时返回 -1 表示降级
     */
    public static float safeMaxsim(float[] queryTokens, float[] docTokens, int qLen, int dLen, int dim) {
        if (!loaded || queryTokens == null || docTokens == null) {
            return -1.0f;
        }
        try {
            return maxsim(queryTokens, docTokens, qLen, dLen, dim);
        } catch (Exception e) {
            log.debug("colbert maxsim failed: {}", e.getMessage());
            return -1.0f;
        }
    }
}
