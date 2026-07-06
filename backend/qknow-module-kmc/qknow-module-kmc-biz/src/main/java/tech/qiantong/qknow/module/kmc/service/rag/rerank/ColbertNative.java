package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;

import java.nio.file.Path;

/**
 * ColBERT MaxSim JNI 桥接类
 * [溯源] 算法优化指南 Phase 4: ColBERT MaxSim batch native kernel
 *
 * 使用 Rust 实现 ColBERT token-level MaxSim 批量计算。
 * 性能收益主要来自批量 JNI 边界和连续数组输入；是否需要 SIMD 需由后续基准确认。
 */
@Slf4j
public class ColbertNative {

    private static volatile boolean loaded = false;

    static {
        loaded = loadNativeLibrary("colbert_jni");
    }

    private static boolean loadNativeLibrary(String libraryName) {
        String nativeLibDir = System.getProperty("qknow.native.lib.dir");
        if (nativeLibDir != null && !nativeLibDir.isBlank()) {
            try {
                System.load(Path.of(nativeLibDir, System.mapLibraryName(libraryName)).toString());
                log.info("colbert-jni library loaded from qknow.native.lib.dir");
                return true;
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                log.debug("colbert-jni library not loaded from qknow.native.lib.dir: {}", e.getMessage());
            }
        }
        try {
            System.loadLibrary(libraryName);
            log.info("colbert-jni library loaded via System.loadLibrary");
            return true;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_colbert", "colbert load failed: " + e.getMessage());
            log.warn("colbert-jni library not found: {}", e.getMessage());
            return false;
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
     * Batch ColBERT MaxSim 计算。
     * docOffsets 是 docTokens 中的 float 偏移，docLens 是每个文档的 token 数。
     */
    public static native float[] maxsimBatch(float[] queryTokens, float[] docTokens, int[] docOffsets,
                                             int[] docLens, int qLen, int dim);

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
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_colbert", "colbert native unavailable");
            }
            return -1.0f;
        }
        if (qLen <= 0 || dLen <= 0 || dim <= 0) {
            RagFallbackMonitor.record("jni", "java_colbert", "invalid colbert dimensions");
            return -1.0f;
        }
        long qExpected = (long) qLen * dim;
        long dExpected = (long) dLen * dim;
        if (qExpected != queryTokens.length || dExpected != docTokens.length) {
            RagFallbackMonitor.record("jni", "java_colbert", "colbert token length mismatch");
            return -1.0f;
        }
        try {
            return maxsim(queryTokens, docTokens, qLen, dLen, dim);
        } catch (LinkageError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_colbert", "colbert maxsim failed: " + safeMessage(e));
            log.debug("colbert maxsim failed: {}", safeMessage(e));
            return -1.0f;
        }
    }

    public static float[] safeMaxsimBatch(float[] queryTokens, float[] docTokens, int[] docOffsets,
                                          int[] docLens, int qLen, int dim) {
        if (!loaded || queryTokens == null || docTokens == null || docOffsets == null || docLens == null) {
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_colbert", "colbert native unavailable");
            }
            return null;
        }
        if (qLen <= 0 || dim <= 0 || queryTokens.length != (long) qLen * dim
                || docOffsets.length != docLens.length) {
            RagFallbackMonitor.record("jni", "java_colbert", "invalid colbert batch dimensions");
            return null;
        }
        try {
            return maxsimBatch(queryTokens, docTokens, docOffsets, docLens, qLen, dim);
        } catch (LinkageError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_colbert", "colbert batch maxsim failed: " + safeMessage(e));
            log.debug("colbert batch maxsim failed: {}", safeMessage(e));
            return null;
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
