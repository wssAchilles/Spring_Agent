package tech.qiantong.qknow.module.kmc.service.rag.sim;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;

import java.nio.file.Path;

/**
 * Rust 向量计算 JNI 桥接类
 * [溯源] 算法优化指南 Phase 3: 批量向量相似度 native kernel
 *
 * 使用 Rust 批量计算向量相似度，减少 Java 热路径中的逐项循环与 JNI 调用次数。
 * 降级策略：JNI 加载失败时回退到 Java 计算
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
            if (!loaded) {
                RagFallbackMonitor.record("jni", "java_vector_similarity", "vecsim native unavailable");
            }
            log.debug("[JNI] VecSimNative not available (loaded={}), using Java fallback", loaded);
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

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
