package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class LongTermRecallH11GateTest {

    @Test
    @DisplayName("H11 默认开启长期记忆空会话召回 topK=3")
    void longTermRecallDefaultsEnabled() throws Exception {
        var ctor = AgentOrchestrator.class.getConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        AgentOrchestrator orchestrator = (AgentOrchestrator) ctor.newInstance(args);
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.getField(orchestrator, "longTermRecallOnEmpty")));
        assertEquals(3, ReflectionTestUtils.getField(orchestrator, "longTermRecallTopK"));
    }
}
