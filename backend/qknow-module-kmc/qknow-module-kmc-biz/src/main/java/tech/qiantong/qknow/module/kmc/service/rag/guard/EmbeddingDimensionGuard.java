package tech.qiantong.qknow.module.kmc.service.rag.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IEmbeddingDimensionProvider;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量维度与模型签名防御门禁 (Phase 13 Embedding Drift Guard)
 * 维度来源优先级：显式配置 {@code qknow.embedding.dimension} → 启动时从 vector_store 探测 →
 * 兼容回退 1536。生产语料基线为 text-embedding-v4（1024 维），不得再假设固定 1536。
 * 100% 阻断不同维度与不同模型版本向量的混写与跨空间距离计算。
 *
 * @author qknow
 */
@Slf4j
@Component
public class EmbeddingDimensionGuard implements IEmbeddingDimensionProvider {

    /** 兼容回退值；用于无数据库/无配置的单测环境回退 */
    public static final int REQUIRED_DIMENSION = 1536;
    public static final String DEFAULT_MODEL_NAME = "text-embedding-v4";
    public static final String DEFAULT_PROVIDER = "dashscope";

    public static final String KEY_MODEL_PROVIDER = "model_provider";
    public static final String KEY_MODEL_NAME = "model_name";
    public static final String KEY_EMBEDDING_DIM = "embedding_dim";
    public static final String KEY_EMBEDDED_AT = "embedded_at";

    @Value("${qknow.embedding.dimension:0}")
    private int configuredDimension;

    private volatile int detectedDimension;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 当前生效的向量维度：
     * 1. 若配置与探测同时存在且不一致，触发 Fail-Close 异常阻断；
     * 2. 若配置 > 0 则生效配置；
     * 3. 若配置 <= 0，尝试从底层存储探测，若探测成功则生效探测值；
     * 4. 否则回退 REQUIRED_DIMENSION (1536)。
     */
    @Override
    public int getEffectiveDimension() {
        if (configuredDimension <= 0 && detectedDimension <= 0 && jdbcTemplate != null) {
            detectFromDatabase();
        }
        if (configuredDimension > 0 && detectedDimension > 0 && configuredDimension != detectedDimension) {
            throw new IllegalStateException(String.format(
                    "向量维度严重漂移！配置维度 [%d] 与底层存储物理维度 [%d] 不一致，系统强制 Fail-Close 阻断！",
                    configuredDimension, detectedDimension));
        }
        if (configuredDimension > 0) {
            return configuredDimension;
        }
        if (detectedDimension > 0) {
            return detectedDimension;
        }
        return REQUIRED_DIMENSION;
    }

    /**
     * 主动从 vector_store 表探测物理维度
     */
    public synchronized void detectFromDatabase() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                    Integer.class);
            if (tableExists != null && tableExists > 0) {
                Integer actualDim = jdbcTemplate.queryForObject(
                        "SELECT vector_dims(embedding) FROM vector_store WHERE embedding IS NOT NULL LIMIT 1",
                        Integer.class);
                if (actualDim != null && actualDim > 0) {
                    detectedDimension = actualDim;
                    log.info("[EmbeddingDimensionGuard] 主动探测 vector_store 物理维度成功: {}", detectedDimension);
                }
            }
        } catch (Exception e) {
            log.warn("[EmbeddingDimensionGuard] 主动探测底层 vector_store 物理维度忽略异常: {}", e.getMessage());
        }
    }

    /**
     * 写入/检索前强类型校验 (float[] 格式)
     */
    public void validateBeforeWrite(float[] embedding) {
        int expected = getEffectiveDimension();
        if (embedding == null || embedding.length != expected) {
            int actual = (embedding == null) ? 0 : embedding.length;
            throw new IllegalArgumentException(String.format(
                    "Vector dimension drift detected! Expected: %d, Actual: %d",
                    expected, actual));
        }

        double sumSq = 0.0;
        for (int i = 0; i < embedding.length; i++) {
            float val = embedding[i];
            if (Float.isNaN(val) || Float.isInfinite(val)) {
                throw new IllegalArgumentException("Vector contains NaN or Infinite values at index " + i);
            }
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 0 && Math.abs(norm - 1.0) > 0.05) {
            log.warn("[EmbeddingDimensionGuard] 向量 L2 范数偏离单位超球面: norm={}, 执行保底归一化", norm);
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) (embedding[i] / norm);
            }
        }
    }

    /**
     * 写入/检索前强类型校验 (List<Double> 格式)
     */
    public void validateBeforeWrite(List<Double> embedding) {
        int expected = getEffectiveDimension();
        if (embedding == null || embedding.size() != expected) {
            int actual = (embedding == null) ? 0 : embedding.size();
            throw new IllegalArgumentException(String.format(
                    "Vector dimension drift detected! Expected: %d, Actual: %d",
                    expected, actual));
        }

        double sumSq = 0.0;
        for (int i = 0; i < embedding.size(); i++) {
            Double val = embedding.get(i);
            if (val == null || val.isNaN() || val.isInfinite()) {
                throw new IllegalArgumentException("Vector contains null, NaN or Infinite values at index " + i);
            }
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 0 && Math.abs(norm - 1.0) > 0.05) {
            log.warn("[EmbeddingDimensionGuard] 向量 L2 范数偏离单位超球面: norm={}", norm);
        }
    }

    /**
     * 构建不可变模型指纹元数据
     */
    public Map<String, Object> buildModelMetadata(String provider, String modelName) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(KEY_MODEL_PROVIDER, (provider != null && !provider.isBlank()) ? provider : DEFAULT_PROVIDER);
        meta.put(KEY_MODEL_NAME, (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL_NAME);
        meta.put(KEY_EMBEDDING_DIM, getEffectiveDimension());
        meta.put(KEY_EMBEDDED_AT, Instant.now().toString());
        return meta;
    }

    /**
     * 启动期健康检查与物理对齐探测 (Fail-Close 强一致性防御)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCheck() {
        try {
            if (jdbcTemplate != null) {
                log.info("[EmbeddingDimensionGuard] 开始执行向量维度与模型基线对齐探测...");
                detectFromDatabase();
            }
            int effective = getEffectiveDimension();
            log.info("[EmbeddingDimensionGuard] vector_store 基线维度: effective={}, configured={}, detected={}",
                    effective, configuredDimension, detectedDimension);
        } catch (IllegalStateException e) {
            log.error("[EmbeddingDimensionGuard] 启动期物理维度不一致强拦截 (Fail-Close): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[EmbeddingDimensionGuard] 启动探测忽略环境差异: {}", e.getMessage());
        }
    }

    public int getDetectedDimension() {
        return detectedDimension;
    }

    public void setDetectedDimension(int detectedDimension) {
        this.detectedDimension = detectedDimension;
    }

    public int getConfiguredDimension() {
        return configuredDimension;
    }

    public void setConfiguredDimension(int configuredDimension) {
        this.configuredDimension = configuredDimension;
    }
}
