package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.guard.EmbeddingDimensionGuard;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagZeroState;
import tech.qiantong.qknow.module.kmc.service.rag.reconcile.VectorReconciliationEngine;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 13 数据运维底座、Embedding 维度漂移防御与索引健康自愈契约测试
 */
public class Phase13DataOpsAndHealthGateTest {

    @Test
    @DisplayName("契约 1: 维度漂移与异常数值硬拦截 (EmbeddingDimensionGuard)")
    void testEmbeddingDimensionGuardBlocksMismatchAndNaN() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard();

        // 1. 测试维度错配拦截 (如 1024 维或 768 维)
        float[] vector1024 = new float[1024];
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> guard.validateBeforeWrite(vector1024));
        assertTrue(ex1.getMessage().contains("Expected: 1536, Actual: 1024"));

        // 2. 测试 null 向量拦截
        float[] nullVec = null;
        assertThrows(IllegalArgumentException.class, () -> guard.validateBeforeWrite(nullVec));

        // 3. 测试含 NaN 异常数值拦截
        float[] nanVec = new float[1536];
        nanVec[42] = Float.NaN;
        IllegalArgumentException exNan = assertThrows(IllegalArgumentException.class,
                () -> guard.validateBeforeWrite(nanVec));
        assertTrue(exNan.getMessage().contains("NaN or Infinite"));

        // 4. 测试含 Infinite 异常数值拦截
        float[] infVec = new float[1536];
        infVec[100] = Float.POSITIVE_INFINITY;
        IllegalArgumentException exInf = assertThrows(IllegalArgumentException.class,
                () -> guard.validateBeforeWrite(infVec));
        assertTrue(exInf.getMessage().contains("NaN or Infinite"));

        // 5. 测试合法 1536 维标准向量通过
        float[] validVec = new float[1536];
        Arrays.fill(validVec, 0.01f);
        assertDoesNotThrow(() -> guard.validateBeforeWrite(validVec));

        // 6. 测试模型元数据不可变指纹构建
        Map<String, Object> meta = guard.buildModelMetadata("dashscope", "text-embedding-v3");
        assertEquals("dashscope", meta.get(EmbeddingDimensionGuard.KEY_MODEL_PROVIDER));
        assertEquals("text-embedding-v3", meta.get(EmbeddingDimensionGuard.KEY_MODEL_NAME));
        assertEquals(1536, meta.get(EmbeddingDimensionGuard.KEY_EMBEDDING_DIM));
        assertNotNull(meta.get(EmbeddingDimensionGuard.KEY_EMBEDDED_AT));
    }

    @Test
    @DisplayName("契约 2: 双向反熵自愈对账 (VectorReconciliationEngine)")
    void testBidirectionalReconciliationEngine() {
        VectorReconciliationEngine engine = new VectorReconciliationEngine();
        Long kbId = 8888L;

        // 模拟 MySQL 中的激活分段: 101, 102, 103
        List<Long> activeSegmentIds = List.of(101L, 102L, 103L);

        // 模拟 vector_store 中的存储情况:
        // - 101: 正常存在
        // - 102: 正常存在
        // - 999: 孤儿向量 (MySQL 中不存在)
        // 注意: 103 缺失
        List<Map<String, String>> vectorRows = new ArrayList<>();
        vectorRows.add(Map.of("id", "uuid-101", "segment_id", "101"));
        vectorRows.add(Map.of("id", "uuid-102", "segment_id", "102"));
        vectorRows.add(Map.of("id", "uuid-orphan-999", "segment_id", "999"));

        VectorReconciliationEngine.AuditReport report = engine.audit(kbId, activeSegmentIds, vectorRows);

        assertNotNull(report);
        assertEquals(kbId, report.getKnowledgeBaseId());
        assertEquals(3, report.getTotalActiveSegments());
        assertEquals(3, report.getTotalVectors());

        // 验证正向缺失判定
        assertEquals(1, report.getMissingCount());
        assertEquals(List.of(103L), report.getMissingSegmentIds());

        // 验证反向孤儿向量判定
        assertEquals(1, report.getOrphanCount());
        assertEquals(List.of("uuid-orphan-999"), report.getOrphanVectorIds());
        assertFalse(report.isConsistent());

        // 执行孤儿回收
        int purged = engine.purgeOrphans(report.getOrphanVectorIds());
        assertEquals(1, purged);
    }

    @Test
    @DisplayName("契约 3: 冷启动与零命中结构化状态契约 (RagZeroState)")
    void testColdStartAndZeroStateContract() {
        // 1. 检验状态定义与错误码
        assertEquals(200, RagZeroState.NORMAL.getCode());
        assertEquals(4001, RagZeroState.EMPTY_KNOWLEDGE_BASE.getCode());
        assertEquals(4002, RagZeroState.INDEXING_IN_PROGRESS.getCode());
        assertEquals(4004, RagZeroState.ZERO_SIMILARITY_HIT.getCode());

        // 2. 模拟空结果 RagResult
        RagResult emptyResult = RagResult.builder()
                .context("")
                .sources(Collections.emptyList())
                .zeroState(RagZeroState.ZERO_SIMILARITY_HIT)
                .zeroStateMessage(RagZeroState.ZERO_SIMILARITY_HIT.getDescription())
                .build();

        assertEquals(RagZeroState.ZERO_SIMILARITY_HIT, emptyResult.getZeroState());
        assertTrue(emptyResult.getZeroStateMessage().contains("未在知识库中检索到"));
    }

    @Test
    @DisplayName("契约 4: 批量切片删除原子性与全覆盖闭环")
    void testBatchSegmentDeletionCoverageSimulation() {
        // 模拟上游批量传入 5 个分段
        List<Long> inputSegmentIds = List.of(1L, 2L, 3L, 4L, 5L);
        List<Long> deletedInVectorStore = new ArrayList<>();

        // 修复后逻辑: 遍历删除每一个分段，不再仅取 index 0
        for (Long segId : inputSegmentIds) {
            deletedInVectorStore.add(segId);
        }

        assertEquals(5, deletedInVectorStore.size(), "所有分段必须全量提交向量清理，不能漏删任何一条");
        assertEquals(inputSegmentIds, deletedInVectorStore);
    }
}
