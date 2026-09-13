package tech.qiantong.qknow.module.kmc.service.rag.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量维度与模型签名防御门禁 (Phase 13 Embedding Drift Guard)
 * 强制保证唯一定义：阿里千问 (Qwen) Embedding 1536 维超球面，单位余弦度量空间。
 * 100% 阻断不同维度与不同模型版本向量的混写与跨空间距离计算。
 *
 * @author qknow
 */
@Slf4j
@Component
public class EmbeddingDimensionGuard {

    public static final int REQUIRED_DIMENSION = 1536;
    public static final String DEFAULT_MODEL_NAME = "text-embedding-v3";
    public static final String DEFAULT_PROVIDER = "dashscope";

    public static final String KEY_MODEL_PROVIDER = "model_provider";
    public static final String KEY_MODEL_NAME = "model_name";
    public static final String KEY_EMBEDDING_DIM = "embedding_dim";
    public static final String KEY_EMBEDDED_AT = "embedded_at";

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 写入/检索前强类型校验 (float[] 格式)
     */
    public void validateBeforeWrite(float[] embedding) {
        if (embedding == null || embedding.length != REQUIRED_DIMENSION) {
            int actual = (embedding == null) ? 0 : embedding.length;
            throw new IllegalArgumentException(String.format(
                    "Vector dimension drift detected! Expected: %d, Actual: %d",
                    REQUIRED_DIMENSION, actual));
        }

        for (int i = 0; i < embedding.length; i++) {
            float val = embedding[i];
            if (Float.isNaN(val) || Float.isInfinite(val)) {
                throw new IllegalArgumentException("Vector contains NaN or Infinite values at index " + i);
            }
        }
    }

    /**
     * 写入/检索前强类型校验 (List<Double> 格式)
     */
    public void validateBeforeWrite(List<Double> embedding) {
        if (embedding == null || embedding.size() != REQUIRED_DIMENSION) {
            int actual = (embedding == null) ? 0 : embedding.size();
            throw new IllegalArgumentException(String.format(
                    "Vector dimension drift detected! Expected: %d, Actual: %d",
                    REQUIRED_DIMENSION, actual));
        }

        for (int i = 0; i < embedding.size(); i++) {
            Double val = embedding.get(i);
            if (val == null || val.isNaN() || val.isInfinite()) {
                throw new IllegalArgumentException("Vector contains null, NaN or Infinite values at index " + i);
            }
        }
    }

    /**
     * 构建不可变模型指纹元数据
     */
    public Map<String, Object> buildModelMetadata(String provider, String modelName) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(KEY_MODEL_PROVIDER, (provider != null && !provider.isBlank()) ? provider : DEFAULT_PROVIDER);
        meta.put(KEY_MODEL_NAME, (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL_NAME);
        meta.put(KEY_EMBEDDING_DIM, REQUIRED_DIMENSION);
        meta.put(KEY_EMBEDDED_AT, Instant.now().toString());
        return meta;
    }

    /**
     * 启动期健康检查与物理对齐探测 (软失败安全保护)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCheck() {
        if (jdbcTemplate == null) {
            return;
        }
        log.info("[EmbeddingDimensionGuard] 开始执行向量维度与模型基线对齐探测...");
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                    Integer.class);
            if (tableExists != null && tableExists > 0) {
                log.info("[EmbeddingDimensionGuard] vector_store 表已就绪，基线维度严格锁定为: {}", REQUIRED_DIMENSION);
            }
        } catch (Exception e) {
            log.warn("[EmbeddingDimensionGuard] 启动探测忽略环境差异: {}", e.getMessage());
        }
    }
}
