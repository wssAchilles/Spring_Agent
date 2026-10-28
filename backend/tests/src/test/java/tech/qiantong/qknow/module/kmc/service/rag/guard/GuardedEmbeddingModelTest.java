package tech.qiantong.qknow.module.kmc.service.rag.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 GuardedEmbeddingModel 的单次计算与就地拦截机制
 */
class GuardedEmbeddingModelTest {

    @Test
    @DisplayName("正常 1024 维向量通过且只调用底层模型一次")
    void normalVectorPassesAndDelegatesOnce() {
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        guard.setConfiguredDimension(1024);

        float[] vector1024 = new float[1024];
        when(delegate.embed("hello")).thenReturn(vector1024);

        GuardedEmbeddingModel guarded = new GuardedEmbeddingModel(delegate, guard);
        float[] result = guarded.embed("hello");

        assertSame(vector1024, result);
        verify(delegate, times(1)).embed("hello");
    }

    @Test
    @DisplayName("维度错配 (如 1536 维) 立即被拦截抛出异常")
    void mismatchedDimensionThrowsException() {
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        guard.setConfiguredDimension(1024);

        float[] vector1536 = new float[1536];
        when(delegate.embed("hello")).thenReturn(vector1536);

        GuardedEmbeddingModel guarded = new GuardedEmbeddingModel(delegate, guard);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> guarded.embed("hello"));
        assertTrue(ex.getMessage().contains("Vector dimension drift detected"));
    }

    @Test
    @DisplayName("批量 Document 嵌入被就地校验")
    void batchDocumentEmbeddingValidatedInPlace() {
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();
        guard.setConfiguredDimension(1024);

        float[] v1 = new float[1024];
        float[] v2 = new float[1536]; // 错配
        when(delegate.embed(anyList(), any(), any())).thenReturn(List.of(v1, v2));

        GuardedEmbeddingModel guarded = new GuardedEmbeddingModel(delegate, guard);
        assertThrows(IllegalArgumentException.class, () ->
                guarded.embed(List.of(new Document("d1"), new Document("d2")), null, null));
    }
}
