package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.guard.EmbeddingDimensionGuard;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Embedding 维度配置/探测回归：生产语料基线为 text-embedding-v4（1024 维），
 * 维度不得再写死为 1536，且必须能从 vector_store 自动探测。
 */
class EmbeddingDimensionConfigTest {

    @Test
    @DisplayName("显式配置 1024 维时按 1024 校验，1536 维向量被拒绝")
    void configuredDimensionTakesPrecedence() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        ReflectionTestUtils.setField(guard, "configuredDimension", 1024);

        assertEquals(1024, guard.getEffectiveDimension());
        assertDoesNotThrow(() -> guard.validateBeforeWrite(new float[1024]));
        assertDoesNotThrow(() -> guard.validateBeforeWrite(java.util.Collections.nCopies(1024, 0.0d)));
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(new float[1536]));

        Map<String, Object> meta = guard.buildModelMetadata("TongYi", "text-embedding-v4");
        assertEquals(1024, meta.get(EmbeddingDimensionGuard.KEY_EMBEDDING_DIM));
    }

    @Test
    @DisplayName("未配置时从 vector_store 自动探测实际维度 1024")
    void autoDetectDimensionFromVectorStore() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(contains("information_schema.tables"), eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("vector_dims"), eq(Integer.class)))
                .thenReturn(1024);
        ReflectionTestUtils.setField(guard, "jdbcTemplate", jdbcTemplate);

        guard.onStartupCheck();

        assertEquals(1024, guard.getEffectiveDimension());
        assertDoesNotThrow(() -> guard.validateBeforeWrite(new float[1024]));
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(new float[1536]));
    }

    @Test
    @DisplayName("无数据库时兼容回退 1536，不阻塞既有链路")
    void fallbackDimensionWhenNoDatabase() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        assertEquals(1536, guard.getEffectiveDimension());
        assertDoesNotThrow(() -> guard.validateBeforeWrite(new float[1536]));
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(new float[1024]));
        assertEquals(1536, guard.buildModelMetadata(null, null)
                .get(EmbeddingDimensionGuard.KEY_EMBEDDING_DIM));
    }

    @Test
    @DisplayName("List 校验与 float 校验使用同一生效维度")
    void listValidationUsesEffectiveDimension() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        ReflectionTestUtils.setField(guard, "configuredDimension", 1024);
        List<Double> valid = java.util.Collections.nCopies(1024, 0.0d);
        List<Double> invalid = java.util.Collections.nCopies(1536, 0.0d);
        assertDoesNotThrow(() -> guard.validateBeforeWrite(valid));
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(invalid));
    }

    @Test
    @DisplayName("配置维度与底库实际维度冲突时触发 Fail-Close 阻断")
    void mismatchBetweenConfiguredAndDetectedThrowsFailClose() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        guard.setConfiguredDimension(1536);
        guard.setDetectedDimension(1024);

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::getEffectiveDimension);
        assertTrue(ex.getMessage().contains("系统强制 Fail-Close 阻断"));

        assertThrows(IllegalStateException.class, guard::onStartupCheck);
    }

    @Test
    @DisplayName("未配置 (0) 时从底层存储探测维度成功并生效")
    void zeroConfigUsesDetectedDimension() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        guard.setConfiguredDimension(0);
        guard.setDetectedDimension(1024);

        assertEquals(1024, guard.getEffectiveDimension());
        assertDoesNotThrow(() -> guard.validateBeforeWrite(new float[1024]));
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(new float[1536]));
    }
}
