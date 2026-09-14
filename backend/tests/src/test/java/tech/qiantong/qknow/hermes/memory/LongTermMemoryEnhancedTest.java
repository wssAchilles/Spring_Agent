package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LongTermMemoryEnhanced 多目标 Pareto 排序与动态强化衰减测试 (Theorem 3.1 & 3.2)")
class LongTermMemoryEnhancedTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private UserMemoryGraphService graphService;

    private LongTermMemory longTermMemory;

    @BeforeEach
    void setUp() {
        longTermMemory = new LongTermMemory(vectorStore, embeddingModel, null, graphService);
    }

    private Document createDoc(String text, double score, long createdAt, int recallCount, double strength, double importance, List<String> entities) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sessionId", "101");
        meta.put("userId", "201");
        meta.put("scope", "workspace:1:bot:2");
        meta.put("created_at", createdAt);
        meta.put("last_retrieved_at", createdAt);
        meta.put("recall_count", recallCount);
        meta.put("memory_strength", strength);
        meta.put("importance", importance);
        if (entities != null) {
            meta.put("entities", entities);
        }
        return Document.builder().text(text).metadata(meta).score(score).build();
    }

    @Test
    @DisplayName("Theorem 3.1 验证：多目标重排单调可分离性，高关联核心历史记忆击败低相关新琐碎记忆")
    void testMultiObjectiveRankingMonotonicity() {
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - 30L * 86400000L;

        // docA: 30天前的核心偏好，高相似度 0.92，多次唤醒强化 k=5, S_k=90天, 重要度 0.9, 绑定实体 "Spring Boot"
        Document docA = createDoc("核心偏好: 必须采用 Spring Boot 3 与 Java 21", 0.92, thirtyDaysAgo, 5, 90.0, 0.9, List.of("Spring Boot"));

        // docB: 刚刚产生的低相关临时对话，相似度 0.72, 未唤醒 k=0, 默认强度 30天, 重要度 0.3, 无实体
        Document docB = createDoc("临时对话: 今天天气不错", 0.72, now, 0, 30.0, 0.3, List.of());

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docB, docA));
        // 图服务激活扩散返回 "Spring Boot" 具备高激活度 0.85
        when(graphService.spreadActivation(anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(Map.of("Spring Boot", 0.85));

        List<Document> ranked = longTermMemory.recall("请给出 Spring Boot 方案", 2, "workspace:1:bot:2");

        assertNotNull(ranked);
        assertEquals(2, ranked.size());
        // 核心历史记忆 docA 凭借高相似度、强化留存率、高重要性与图谱激活度，应当逆转排在第 1 位
        assertEquals(docA.getText(), ranked.get(0).getText(), "高价值核心记忆必须排在第一位，彻底消除时近偏见");
    }

    @Test
    @DisplayName("SHA-256 内容签名幂等防重：完全相同内容不重复存储")
    void testContentHashIdempotency() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sessionId", "102");
        meta.put("userId", "202");
        meta.put("scope", "workspace:1:bot:2");

        String content = "用户偏好代码采用 4 空格缩进";

        // 第一次存储
        longTermMemory.store(content, new HashMap<>(meta));
        verify(vectorStore, times(1)).add(anyList());

        // 模拟已存在相同内容
        Document existing = Document.builder().text(content).metadata(Map.of("sessionId", "102", "userId", "202", "scope", "workspace:1:bot:2", "content_hash", longTermMemory.computeSha256(content))).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existing));

        // 第二次存储相同内容，应触发幂等阻断
        longTermMemory.store(content, new HashMap<>(meta));
        // 不再重复向 vectorStore.add 写入新记录
        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("向下兼容测试：在无 UserMemoryGraphService 注入时，图谱激活项优雅回退，不阻断向量召回")
    void testFallbackWithoutGraphService() {
        LongTermMemory fallbackMemory = new LongTermMemory(vectorStore, embeddingModel);
        Document doc = createDoc("普通记忆", 0.8, System.currentTimeMillis(), 0, 30.0, 0.5, List.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = fallbackMemory.recall("query", 1, "workspace:1:bot:2");
        assertFalse(results.isEmpty());
        assertEquals("普通记忆", results.get(0).getText());
    }
}
