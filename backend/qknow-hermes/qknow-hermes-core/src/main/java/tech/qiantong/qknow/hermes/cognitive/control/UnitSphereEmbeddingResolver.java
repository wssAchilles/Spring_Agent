package tech.qiantong.qknow.hermes.cognitive.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 1024 维单位超球面 {@code S^1023} 向量解析器（千问 {@code text-embedding-v4} 为唯一在线通道）。
 * <p>
 * 设计契约：
 * 1. 优先调用注入的千问 {@link EmbeddingModel} 生成 1024 维向量并严格执行 {@code L2} 单位归一化；
 * 2. 提供有界请求级 LRU 缓存与 1.5s 超时保护；
 * 3. 当且仅当 {@code embeddingModel == null}（离线契约单测）或在线通道超时/异常时，
 *    确定性回退至 {@link LocalitySensitiveFeatureHasher#projectToUnitSphereFloat}，并记录解析来源遥测。
 * </p>
 */
public class UnitSphereEmbeddingResolver {

    private static final Logger log = LoggerFactory.getLogger(UnitSphereEmbeddingResolver.class);
    public static final int EMBEDDING_DIM = 1024;
    public static final long DEFAULT_TIMEOUT_MS = 1_500L;
    private static final int MAX_CACHE_ENTRIES = 512;

    public enum ResolutionSource {
        QWEN_EMBEDDING_V4,
        OFFLINE_LSH_FALLBACK
    }

    public record ResolutionResult(float[] unitVector1024, ResolutionSource source) {
    }

    private final EmbeddingModel embeddingModel;
    private final long timeoutMs;
    private final Map<String, ResolutionResult> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResolutionResult> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public UnitSphereEmbeddingResolver() {
        this(null, DEFAULT_TIMEOUT_MS);
    }

    public UnitSphereEmbeddingResolver(EmbeddingModel embeddingModel) {
        this(embeddingModel, DEFAULT_TIMEOUT_MS);
    }

    public UnitSphereEmbeddingResolver(EmbeddingModel embeddingModel, long timeoutMs) {
        this.embeddingModel = embeddingModel;
        this.timeoutMs = Math.max(100L, timeoutMs);
    }

    /**
     * 将输入文本投影至 1024 维单位超球面 {@code S^1023}。
     */
    public float[] resolveUnit1024(String text) {
        return resolveWithProvenance(text).unitVector1024();
    }

    /**
     * 解析 1024 维单位向量并返回来源证明（在线千问 {@code text-embedding-v4} 或离线 LSH 兜底）。
     */
    public ResolutionResult resolveWithProvenance(String text) {
        String safeText = (text == null || text.isBlank()) ? "empty_query" : text.trim();
        String cacheKey = sha256Hex(safeText);
        synchronized (cache) {
            ResolutionResult cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        ResolutionResult computed = computeInternal(safeText);
        synchronized (cache) {
            cache.put(cacheKey, computed);
        }
        return computed;
    }

    private ResolutionResult computeInternal(String safeText) {
        if (embeddingModel != null) {
            try {
                float[] raw = CompletableFuture
                        .supplyAsync(() -> embeddingModel.embed(safeText))
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
                float[] normalized = normalizeExact1024(raw);
                if (normalized != null) {
                    return new ResolutionResult(normalized, ResolutionSource.QWEN_EMBEDDING_V4);
                }
            } catch (Exception e) {
                log.debug("千问 text-embedding-v4 调用异常或超时({}ms)，降级至离线 LSH 超球面投影: {}",
                        timeoutMs, e.getMessage());
            }
        }
        float[] fallback = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(safeText, EMBEDDING_DIM);
        return new ResolutionResult(fallback, ResolutionSource.OFFLINE_LSH_FALLBACK);
    }

    /**
     * 将任意合法输出校验并 {@code L2} 归一化到 {@code S^1023}；若维度不匹配或为零向量则返回 {@code null}。
     */
    public static float[] normalizeExact1024(float[] raw) {
        if (raw == null || raw.length != EMBEDDING_DIM) {
            return null;
        }
        double normSq = 0.0;
        for (float v : raw) {
            if (!Float.isFinite(v)) {
                return null;
            }
            normSq += (double) v * v;
        }
        double norm = Math.sqrt(normSq);
        if (norm <= 1e-12) {
            return null;
        }
        float[] out = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            out[i] = (float) (raw[i] / norm);
        }
        return out;
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
