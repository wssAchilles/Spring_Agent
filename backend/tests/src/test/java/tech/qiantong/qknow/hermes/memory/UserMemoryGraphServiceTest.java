package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.TransientException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMemoryGraphService Neo4j 实体偏好图与 2-Hop 扩散测试")
class UserMemoryGraphServiceTest {

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    private UserMemoryGraphService graphService;

    @BeforeEach
    void setUp() {
        lenient().when(driver.session(any(SessionConfig.class))).thenReturn(session);
        lenient().when(driver.session()).thenReturn(session);
        graphService = new UserMemoryGraphService(driver);
    }

    @Test
    @DisplayName("偏好实体原子 Upsert 参数完备性与正常执行")
    void testUpsertPreferenceSuccess() {
        when(session.run(anyString(), any(Value.class))).thenReturn(result);

        assertDoesNotThrow(() -> {
            graphService.upsertPreference("user_1001", "workspace:1:bot:2", "Java 21", "Technology", 0.9, "POSITIVE");
        });

        verify(session, atLeastOnce()).run(anyString(), any(Value.class));
    }

    @Test
    @DisplayName("2-Hop 激活扩散检索与结果提取")
    void testSpreadActivationSuccess() {
        org.neo4j.driver.Record record1 = mock(org.neo4j.driver.Record.class);
        when(record1.get("entityName")).thenReturn(Values.value("Java 21"));
        when(record1.get("activation")).thenReturn(Values.value(0.85));

        org.neo4j.driver.Record record2 = mock(org.neo4j.driver.Record.class);
        when(record2.get("entityName")).thenReturn(Values.value("Spring Boot 3"));
        when(record2.get("activation")).thenReturn(Values.value(0.62));

        when(result.hasNext()).thenReturn(true, true, false);
        when(result.next()).thenReturn(record1, record2);
        when(session.run(anyString(), anyMap(), any(TransactionConfig.class))).thenReturn(result);

        Map<String, Double> activations = graphService.spreadActivation("user_1001", "workspace:1:bot:2", List.of("Java 21"), 0.4, 10, 5, 0.02);

        assertNotNull(activations);
        assertEquals(2, activations.size());
        assertEquals(0.85, activations.get("Java 21"), 1e-4);
        assertEquals(0.62, activations.get("Spring Boot 3"), 1e-4);
    }

    @Test
    @DisplayName("Neo4j 故障或超时安全降级熔断测试：返回空 Map，杜绝抛异常阻断上游")
    void testSpreadActivationTimeoutFallback() {
        when(session.run(anyString(), anyMap(), any(TransactionConfig.class)))
                .thenThrow(new TransientException("Client.Transaction.Timeout", "Transaction timeout after 3000ms"));

        Map<String, Double> activations = graphService.spreadActivation("user_1001", "workspace:1:bot:2", List.of("Java 21"), 0.4, 10, 5, 0.02);

        assertNotNull(activations);
        assertTrue(activations.isEmpty(), "超时时必须安全降级返回空映射");
    }
}
