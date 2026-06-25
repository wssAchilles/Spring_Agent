package tech.qiantong.qknow.hermes.agent;

import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanAndSolveOrchestratorTest {

    @Test
    void complexQuestionRuleMatchesKeywordsAndLength() throws Exception {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        Method method = AgentOrchestrator.class.getDeclaredMethod("isComplexQuestion", String.class);
        method.setAccessible(true);

        assertEquals(Boolean.FALSE, method.invoke(orchestrator, "什么是RAG？"));
        assertEquals(Boolean.TRUE, method.invoke(orchestrator, "请对比 A 公司和 B 公司财报并最后写成邮件"));
        assertEquals(Boolean.TRUE, method.invoke(orchestrator, "x".repeat(101)));
    }

    @Test
    void extractsFencedPlanJson() throws Exception {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        Method method = AgentOrchestrator.class.getDeclaredMethod("extractJsonArray", String.class);
        method.setAccessible(true);

        JSONArray array = (JSONArray) method.invoke(orchestrator, "```json\n[{\"taskId\":\"t1\"}]\n```");

        assertEquals(1, array.size());
        assertEquals("t1", array.getJSONObject(0).getString("taskId"));
    }

    @Test
    void detectsDependencyCycle() throws Exception {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        Class<?> taskClass = Class.forName("tech.qiantong.qknow.hermes.agent.AgentOrchestrator$PlanTask");
        var ctor = taskClass.getDeclaredConstructor(String.class, String.class, String.class, List.class);
        ctor.setAccessible(true);
        Object t1 = ctor.newInstance("t1", "one", "rag_worker", List.of("t2"));
        Object t2 = ctor.newInstance("t2", "two", "rag_worker", List.of("t1"));

        Method method = AgentOrchestrator.class.getDeclaredMethod("hasDependencyCycle", List.class);
        method.setAccessible(true);

        assertEquals(Boolean.TRUE, method.invoke(orchestrator, List.of(t1, t2)));
    }

    @Test
    void configDefaultsDisabled() {
        PlanSolveConfig config = new PlanSolveConfig();

        assertFalse(config.isEnabled());
        assertEquals(5, config.getMaxTasks());
        assertEquals(1, config.getMaxReflectionRetries());
    }
}
