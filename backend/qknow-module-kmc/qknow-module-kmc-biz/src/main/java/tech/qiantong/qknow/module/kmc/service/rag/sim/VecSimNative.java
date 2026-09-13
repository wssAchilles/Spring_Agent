package tech.qiantong.qknow.module.kmc.service.rag.sim;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Path;

/**
 * Rust 向量计算 JNI 桥接类
 * [溯源] Phase 18: 原生加速深水区 (N1+N2) - Rust SIMD 批量向量计算核
 *
 * 核心特性：
 * 1. 堆内 float[] 与堆外 DirectByteBuffer 零拷贝双模支持；
 * 2. AVX2 / NEON 硬件级 FMA 1536 维点积加速；
 * 3. 严格浮点精度稳定性保障与单调性保持；
 * 4. JNI 加载失败或计算异常时无缝 Fail-Open 降级至 Java 高性能标量计算。
 */
@Slf4j
public class VecSimNative {

    private static volatile boolean loaded = false;

    static {
        loaded = loadNativeLibrary("vecsim_jni");
    }

    private static boolean loadNativeLibrary(String libraryName) {
        String nativeLibDir = System.getProperty("qknow.native.lib.dir");
        if (nativeLibDir != null && !nativeLibDir.isBlank()) {
            try {
                System.load(Path.of(nativeLibDir, System.mapLibraryName(libraryName)).toString());
                log.info("vecsim-jni library loaded from qknow.native.lib.dir");
                return true;
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                log.debug("vecsim-jni library not loaded from qknow.native.lib.dir: {}", e.getMessage());
            }
        }
        try {
            System.loadLibrary(libraryName);
            log.info("vecsim-jni library loaded via System.loadLibrary");
            return true;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim load failed: " + e.getMessage());
            log.warn("vecsim-jni library not found: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 批量 cosine 相似度计算（堆内数组）
     */
    public static native float[] cosineBatch(float[] query, float[] corpus, int dim);

    /**
     * 批量 inner product 计算（堆内数组）
     */
    public static native float[] innerProductBatch(float[] query, float[] corpus, int dim);

    /**
     * 批量 cosine 相似度计算（堆外 DirectByteBuffer 零拷贝）
     */
    public static native float[] cosineBatchDirect(ByteBuffer queryBuf, ByteBuffer corpusBuf, int dim, int n);

    /**
     * 批量 inner product 计算（堆外 DirectByteBuffer 零拷贝）
     */
    public static native float[] innerProductBatchDirect(ByteBuffer queryBuf, ByteBuffer corpusBuf, int dim, int n);

    /**
     * 检查 JNI 原生库是否已加载可用
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * 安全 cosine 计算（堆内数组）：JNI 不可用时返回 null 以便上层感知
     */
    public static float[] safeCosineBatch(float[] query, float[] corpus, int dim) {
        if (!loaded || query == null || corpus == null) {
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim native unavailable");
            }
            log.debug("[JNI] VecSimNative not available (loaded={}), using fallback", loaded);
            return null;
        }
        if (dim <= 0 || query.length != dim || corpus.length % dim != 0) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "invalid vecsim dimensions");
            return null;
        }
        try {
            float[] result = cosineBatch(query, corpus, dim);
            log.debug("[JNI] VecSimNative.cosineBatch called, {} vectors scored", result != null ? result.length : 0);
            return result;
        } catch (LinkageError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim cosine failed: " + safeMessage(e));
            log.debug("vecsim cosine failed: {}", safeMessage(e));
            return null;
        }
    }

    /**
     * 安全 inner product 计算（堆内数组）
     */
    public static float[] safeInnerProductBatch(float[] query, float[] corpus, int dim) {
        if (!loaded || query == null || corpus == null) {
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim native unavailable");
            }
            return null;
        }
        if (dim <= 0 || query.length != dim || corpus.length % dim != 0) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "invalid vecsim dimensions");
            return null;
        }
        try {
            return innerProductBatch(query, corpus, dim);
        } catch (LinkageError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim inner_product failed: " + safeMessage(e));
            return null;
        }
    }

    /**
     * 安全 cosine 计算（DirectByteBuffer 堆外内存）
     */
    public static float[] safeCosineBatchDirect(ByteBuffer queryBuf, ByteBuffer corpusBuf, int dim, int n) {
        if (!loaded || queryBuf == null || corpusBuf == null) {
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim native direct unavailable");
            }
            return null;
        }
        if (dim <= 0 || n <= 0 || !queryBuf.isDirect() || !corpusBuf.isDirect()) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "invalid direct buffer arguments");
            return null;
        }
        try {
            return cosineBatchDirect(queryBuf, corpusBuf, dim, n);
        } catch (LinkageError | RuntimeException e) {
            RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim cosine direct failed: " + safeMessage(e));
            return null;
        }
    }

    /**
     * 生产级带 Java 自动保底的 Cosine 计算
     */
    public static float[] cosineBatchWithFallback(float[] query, float[] corpus, int dim) {
        float[] nativeResult = safeCosineBatch(query, corpus, dim);
        if (nativeResult != null && nativeResult.length > 0) {
            return nativeResult;
        }
        return javaFallbackCosineBatch(query, corpus, dim);
    }

    /**
     * Java 纯标量余弦相似度兜底计算（无 JNI 依赖，用于离线/单元测试与应急降级）
     */
    public static float[] javaFallbackCosineBatch(float[] query, float[] corpus, int dim) {
        if (dim <= 0 || query == null || corpus == null) {
            return new float[0];
        }
        int n = corpus.length / dim;
        if (n == 0) {
            return new float[0];
        }

        float[] scores = new float[n];
        float qNormSq = 0.0f;
        for (int d = 0; d < dim; d++) {
            qNormSq += query[d] * query[d];
        }
        float qNorm = (float) Math.sqrt(qNormSq);
        boolean isUnitQuery = Math.abs(qNormSq - 1.0f) < 1e-4f;

        for (int i = 0; i < n; i++) {
            int offset = i * dim;
            float dot = 0.0f;
            for (int d = 0; d < dim; d++) {
                dot += query[d] * corpus[offset + d];
            }
            if (isUnitQuery) {
                float cNormSq = 0.0f;
                for (int d = 0; d < dim; d++) {
                    float val = corpus[offset + d];
                    cNormSq += val * val;
                }
                if (Math.abs(cNormSq - 1.0f) < 1e-4f) {
                    scores[i] = dot;
                } else {
                    float cNorm = (float) Math.sqrt(cNormSq);
                    scores[i] = cNorm > 0.0f ? dot / cNorm : 0.0f;
                }
            } else {
                float cNormSq = 0.0f;
                for (int d = 0; d < dim; d++) {
                    float val = corpus[offset + d];
                    cNormSq += val * val;
                }
                float cNorm = (float) Math.sqrt(cNormSq);
                scores[i] = (qNorm > 0.0f && cNorm > 0.0f) ? dot / (qNorm * cNorm) : 0.0f;
            }
        }
        return scores;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
