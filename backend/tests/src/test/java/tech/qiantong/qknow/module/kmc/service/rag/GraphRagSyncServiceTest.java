package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GraphRagSyncServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private GraphRagSyncService service;

    @BeforeEach
    void setUp() {
        service = new GraphRagSyncService();
        GraphRagProperties properties = new GraphRagProperties();
        properties.setEnabled(false);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    @DisplayName("Neo4j不可用时仍写入Postgres节点-段落映射")
    void syncRows_writesPostgresNodeSegmentRelationsWithoutNeo4j() {
        service.syncRows(Collections.singletonList(new Object[]{
                9L,
                101L,
                "qm-segment-1",
                "[\"Flutter\",\"Dart\"]",
                "[]"
        }));

        verify(jdbcTemplate).update(startsWith("DELETE FROM kg_node_segment_rel WHERE document_id IN"), eq(9L));
        verify(jdbcTemplate).update(contains("INSERT INTO kg_node_segment_rel"), eq(101L), eq(9L), eq("Flutter"));
        verify(jdbcTemplate).update(contains("INSERT INTO kg_node_segment_rel"), eq(101L), eq(9L), eq("Dart"));
    }
}
