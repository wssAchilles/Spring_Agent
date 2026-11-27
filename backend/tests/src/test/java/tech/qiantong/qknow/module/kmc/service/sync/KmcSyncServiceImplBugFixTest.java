package tech.qiantong.qknow.module.kmc.service.sync;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.document.enums.DocumentSyncStatus;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.sync.KmcSyncMapper;
import tech.qiantong.qknow.module.kmc.service.kmcDocument.IKmcDocumentService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeBaseService;
import tech.qiantong.qknow.module.kmc.service.knowledgeSegment.IKmcDocumentSegmentService;
import tech.qiantong.qknow.module.kmc.service.rag.guard.EmbeddingDimensionGuard;
import tech.qiantong.qknow.module.kmc.service.rag.guard.GuardedEmbeddingModel;
import tech.qiantong.qknow.module.kmc.service.rag.RagCacheService;
import tech.qiantong.qknow.module.kmc.service.rag.SemanticCacheService;
import tech.qiantong.qknow.module.kmc.service.sync.impl.KmcSyncServiceImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KmcSyncServiceImpl 向量门禁、按文档 ID 删除与同步状态回归测试
 */
@ExtendWith(MockitoExtension.class)
class KmcSyncServiceImplBugFixTest {

    @Mock
    private IKmcDocumentService kmcDocumentService;
    @Mock
    private IKmcKnowledgeBaseService kmcKnowledgeBaseService;
    @Mock
    private IKmcDocumentSegmentService kmcDocumentSegmentService;
    @Mock
    private IAiModelApiService aiModelService;
    @Mock
    private IVectorStoreService vectorStoreService;
    @Mock
    private ILuceneService luceneService;
    @Mock
    private RagCacheService ragCacheService;
    @Mock
    private SemanticCacheService semanticCacheService;
    @Mock
    private KmcSyncMapper kmcSyncMapper;
    @Mock
    private EmbeddingModel rawEmbeddingModel;
    @Mock
    private VectorStore vectorStore;

    private KmcSyncServiceImpl service;
    private EmbeddingDimensionGuard dimensionGuard;

    @BeforeEach
    void setUp() {
        service = new KmcSyncServiceImpl();
        dimensionGuard = new EmbeddingDimensionGuard();
        ReflectionTestUtils.setField(service, "kmcDocumentService", kmcDocumentService);
        ReflectionTestUtils.setField(service, "kmcKnowledgeBaseService", kmcKnowledgeBaseService);
        ReflectionTestUtils.setField(service, "iKmcDocumentSegmentService", kmcDocumentSegmentService);
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);
        ReflectionTestUtils.setField(service, "vectorStoreService", vectorStoreService);
        ReflectionTestUtils.setField(service, "luceneService", luceneService);
        ReflectionTestUtils.setField(service, "ragCacheService", ragCacheService);
        ReflectionTestUtils.setField(service, "semanticCacheService", semanticCacheService);
        ReflectionTestUtils.setField(service, "baseMapper", kmcSyncMapper);
    }

    @Test
    @DisplayName("save2VectorStore 必须通过 GuardedEmbeddingModel 包裹向量维度与范数门禁")
    void save2VectorStore_wrapsEmbeddingModelWithDimensionGuard() {
        // 若已声明 embeddingDimensionGuard 字段则注入，用于验证 GuardedEmbeddingModel 门禁包裹
        java.util.Arrays.stream(KmcSyncServiceImpl.class.getDeclaredFields())
                .filter(f -> "embeddingDimensionGuard".equals(f.getName()))
                .findFirst()
                .ifPresent(f -> ReflectionTestUtils.setField(service, "embeddingDimensionGuard", dimensionGuard));

        KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                .id(10L)
                .embeddingModelProvider("1")
                .embeddingModel("text-embedding-v4")
                .build();
        KmcDocumentDO doc = new KmcDocumentDO();
        doc.setId(99L);
        doc.setKnowledgeBaseId(10L);
        doc.setName("Day1_guide.md");

        when(aiModelService.getEmbeddingModel(1L, "text-embedding-v4")).thenReturn(rawEmbeddingModel);
        when(vectorStoreService.getVectorStore(any(EmbeddingModel.class))).thenReturn(vectorStore);

        Document segment = new Document("seg-1", "测试切片内容", new java.util.HashMap<>());
        service.save2VectorStore(Map.of("seg-1", 501L), kb, doc, List.of(segment));

        ArgumentCaptor<EmbeddingModel> modelCaptor = ArgumentCaptor.forClass(EmbeddingModel.class);
        verify(vectorStoreService).getVectorStore(modelCaptor.capture());
        assertInstanceOf(GuardedEmbeddingModel.class, modelCaptor.getValue(),
                "save2VectorStore 必须使用 GuardedEmbeddingModel 拦截维度错配");
    }

    @Test
    @DisplayName("syncToRemove 必须按 documentId 条件删除 kmc_sync 记录，不得误按主键 deleteById 删除")
    void syncToRemove_deletesByDocumentIdConditionInsteadOfPrimaryKey() {
        KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                .id(10L)
                .embeddingModelProvider("1")
                .embeddingModel("text-embedding-v4")
                .build();
        KmcDocumentDO doc = new KmcDocumentDO();
        doc.setId(888L);
        doc.setKnowledgeBaseId(10L);

        when(kmcKnowledgeBaseService.getKmcKnowledgeBaseById(10L)).thenReturn(kb);
        when(aiModelService.getEmbeddingModel(1L, "text-embedding-v4")).thenReturn(rawEmbeddingModel);
        when(vectorStoreService.getVectorStore(any(EmbeddingModel.class))).thenReturn(vectorStore);

        Boolean removed = service.syncToRemove(doc);

        assertTrue(removed);
        verify(kmcSyncMapper, never()).deleteById(any(Serializable.class));
        verify(kmcSyncMapper, times(1)).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("syncToCreate 无需在本地临时目录无意义拷贝不存在的路径即可标记同步中状态")
    void syncToCreate_marksSyncInWithoutRedundantTempCopy() {
        KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                .id(10L)
                .indexingTechnique("high_quality")
                .build();
        KmcDocumentDO doc = new KmcDocumentDO();
        doc.setId(777L);
        doc.setKnowledgeBaseId(10L);
        doc.setName("spec.pdf");
        doc.setPath("/non-existent-for-temp-copy/spec.pdf");

        when(kmcKnowledgeBaseService.getKmcKnowledgeBaseById(10L)).thenReturn(kb);

        Boolean ok = service.syncToCreate(doc);

        assertTrue(ok);
        assertEquals(DocumentSyncStatus.IN.code, doc.getSyncStatus(),
                "syncToCreate 不应因无用的临时文件复制失败而将状态置为 ERROR");
    }
}
