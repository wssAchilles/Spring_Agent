package tech.qiantong.qknow.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.hermes.memory.LongTermMemory;
import tech.qiantong.qknow.hermes.memory.MemoryManager;
import tech.qiantong.qknow.hermes.memory.ShortTermMemory;
import tech.qiantong.qknow.hermes.memory.SleepTimeMemoryAgent;
import tech.qiantong.qknow.hermes.memory.WorkingMemory;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.GraphRagResult;
import tech.qiantong.qknow.module.kmc.service.rag.CypherSafetyValidator;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagProperties;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagRetriever;
import tech.qiantong.qknow.redis.service.impl.RedisServiceImpl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiveBackendIntegrationTest {

    private static void requireLiveTests() {
        Assumptions.assumeTrue(Boolean.getBoolean("liveTests"), "live backend tests disabled");
    }

    @Test
    @DisplayName("Docker Redis: ShortTermMemory 写入、扫描、Sleep-time 打捞与清理")
    void redisShortTermMemoryAndSleepAgent_roundTrip() {
        requireLiveTests();
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        redisConfig.setDatabase(15);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();

            RedisServiceImpl redis = new RedisServiceImpl();
            ReflectionTestUtils.setField(redis, "stringRedisTemplate", template);

            cleanupCodexSessions(redis);
            String sessionId = "codex-live-" + UUID.randomUUID();
            ShortTermMemory shortTerm = new ShortTermMemory(null, redis);
            shortTerm.clearSession(sessionId);

            shortTerm.addMessage(sessionId, new UserMessage("Redis live memory question"));
            shortTerm.addMessage(sessionId, new AssistantMessage("Redis live memory answer"));
            shortTerm.touchSession(sessionId, "live-user", "live-scope");

            assertEquals(2, shortTerm.size(sessionId));
            assertEquals("live-user", shortTerm.getSessionUserId(sessionId));
            assertEquals("live-scope", shortTerm.getSessionScope(sessionId));
            assertTrue(shortTerm.listSessionIds(1000).contains(sessionId));

            redis.set("memory:short-meta:last:" + sessionId, String.valueOf(System.currentTimeMillis() - 3_600_000L));
            AtomicInteger consolidated = new AtomicInteger();
            MemoryManager manager = new MemoryManager(shortTerm, new LongTermMemory(null, null), new WorkingMemory(redis)) {
                @Override
                public void onConversationEnd(String currentSessionId, String userId, String scope) {
                    assertEquals(sessionId, currentSessionId);
                    assertEquals("live-user", userId);
                    assertEquals("live-scope", scope);
                    consolidated.incrementAndGet();
                }
            };

            SleepTimeMemoryAgent agent = new SleepTimeMemoryAgent(manager, 1L, 1000);
            agent.consolidateIdleConversations();

            assertEquals(1, consolidated.get());
            assertEquals(0, shortTerm.size(sessionId));
            assertFalse(shortTerm.listSessionIds(1000).contains(sessionId));
            cleanupCodexSessions(redis);
        } finally {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("Postgres: 关键表存在、事务写读回滚、GraphRAG JSONB 检索")
    void postgresSchemaAndGraphRag_roundTrip() throws Exception {
        requireLiveTests();
        String username = getenv("POSTGRESQL_USERNAME", "postgres");
        String password = getenv("POSTGRESQL_PASSWORD", "postgres");
        String url = getenv("POSTGRESQL_URL", "jdbc:postgresql://127.0.0.1:5432/ai_agent");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            try {
                assertEquals("ai_agent", jdbcTemplate.queryForObject("select current_database()", String.class));
                assertTableExists(jdbcTemplate, "chat_message");
                assertTableExists(jdbcTemplate, "vector_store");
                assertTableExists(jdbcTemplate, "kmc_document");
                assertTableExists(jdbcTemplate, "kmc_document_segment");
                assertTableExists(jdbcTemplate, "kmc_segment_entity_metadata");
                assertTableExists(jdbcTemplate, "kg_node");
                assertTableExists(jdbcTemplate, "kg_edge");

                long workspaceId = 9_900_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000L);
                long knowledgeBaseId = 8_800_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000L);
                String entity = "CodexLiveEntity" + UUID.randomUUID().toString().replace("-", "");

                Long documentId = jdbcTemplate.queryForObject("""
                        INSERT INTO kmc_document(workspace_id, category_id, knowledge_base_id, name, path, valid_flag, del_flag)
                        VALUES (?, ?, ?, ?, ?, true, 0)
                        RETURNING id
                        """, Long.class, workspaceId, 1L, knowledgeBaseId, "codex-live-doc", "/tmp/codex-live.md");
                Long segmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO kmc_document_segment(workspace_id, document_name, document_id, content, valid_flag, del_flag)
                        VALUES (?, ?, ?, ?, 0, 0)
                        RETURNING id
                        """, Long.class, workspaceId, "codex-live-doc", documentId,
                        "GraphRAG live segment about " + entity);
                jdbcTemplate.update("""
                        INSERT INTO kmc_segment_entity_metadata(document_id, segment_id, entities, relations)
                        VALUES (?, ?, ?::jsonb, ?::jsonb)
                        """, documentId, segmentId,
                        "[\"" + entity + "\"]",
                        "[{\"source\":\"" + entity + "\",\"relation\":\"mentions\",\"target\":\"GraphRAG\"}]");

                GraphRagProperties properties = new GraphRagProperties();
                properties.setEnabled(true);
                GraphRagRetriever retriever = new GraphRagRetriever();
                ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbcTemplate);
                ReflectionTestUtils.setField(retriever, "properties", properties);
                ReflectionTestUtils.setField(retriever, "cypherSafetyValidator", new CypherSafetyValidator());

                List<GraphRagResult> results = retriever.graphSearch(knowledgeBaseId, List.of(entity), 5);
                assertEquals(1, results.size());
                assertEquals(segmentId, results.get(0).getSegmentId());

                Long sourceNode = jdbcTemplate.queryForObject("""
                        INSERT INTO kg_node(workspace_id, label, type, properties)
                        VALUES (?, ?, ?, ?::jsonb)
                        RETURNING id
                        """, Long.class, workspaceId, "codex-source", "test", "{\"live\":true}");
                Long targetNode = jdbcTemplate.queryForObject("""
                        INSERT INTO kg_node(workspace_id, label, type, properties)
                        VALUES (?, ?, ?, ?::jsonb)
                        RETURNING id
                        """, Long.class, workspaceId, "codex-target", "test", "{\"live\":true}");
                Long edgeCount = jdbcTemplate.queryForObject("""
                        WITH inserted AS (
                            INSERT INTO kg_edge(workspace_id, source_id, target_id, label, properties)
                            VALUES (?, ?, ?, ?, ?::jsonb)
                            RETURNING id
                        )
                        SELECT count(*) FROM inserted
                        """, Long.class, workspaceId, sourceNode, targetNode, "codex-live", "{\"weight\":1}");
                assertEquals(1L, edgeCount);
            } finally {
                connection.rollback();
            }
        }
    }

    private static void assertTableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select to_regclass('public.' || ?) is not null", Boolean.class, tableName);
        assertEquals(Boolean.TRUE, exists, "missing table " + tableName);
    }

    private static void cleanupCodexSessions(RedisServiceImpl redis) {
        for (String key : redis.scanKeys("memory:short:codex-live-*", 1000)) {
            String sessionId = key.substring("memory:short:".length());
            redis.delete(key);
            redis.delete("memory:short-meta:last:" + sessionId);
            redis.delete("memory:short-meta:user:" + sessionId);
            redis.delete("memory:short-meta:scope:" + sessionId);
        }
    }

    private static String getenv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
