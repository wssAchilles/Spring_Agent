package tech.qiantong.qknow.hermes.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiAgentTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolCallbackResolver resolver;

    @Mock
    private ReactAgent reactAgent;

    @Mock
    private ToolCallback toolCallback;

    private WorkerAgent ragWorker;
    private WorkerAgent searchWorker;

    @BeforeEach
    void setUp() {
        ragWorker = new WorkerAgent(
                "RAGAgent", "知识库检索 Agent", "你是知识库检索专家",
                List.of(), chatModel, resolver);

        searchWorker = new WorkerAgent(
                "SearchAgent", "网络搜索 Agent", "你是网络搜索专家",
                List.of(), chatModel, resolver);
    }

    // ===== 测试1: 单个 Worker 执行 =====

    @Test
    void singleWorkerExecution() throws Exception {
        WorkerAgent spyWorker = spy(ragWorker);
        doReturn(reactAgent).when(spyWorker).createReactAgent();

        doReturn(mockAssistantResponse("知识库检索结果: AI是人工智能的缩写"))
                .when(reactAgent).call(anyList());

        String result = spyWorker.chat("什么是AI?", null);

        assertNotNull(result);
        assertTrue(result.contains("AI是人工智能"));
        verify(reactAgent).call(anyList());
    }

    @Test
    void workerExecutionWithHistory() throws Exception {
        WorkerAgent spyWorker = spy(ragWorker);
        doReturn(reactAgent).when(spyWorker).createReactAgent();

        doReturn(mockAssistantResponse("基于上下文的回答"))
                .when(reactAgent).call(anyList());

        Map<String, Object> context = new HashMap<>();
        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("之前的问题"));
        history.add(new AssistantMessage("之前的回答"));
        context.put("history", history);

        String result = spyWorker.chat("继续讨论", context);

        assertNotNull(result);
        verify(reactAgent).call(argThat((List<Message> messages) ->
                messages.size() == 3)); // 2 history + 1 user
    }

    @Test
    void workerHandlesExecutionError() throws Exception {
        WorkerAgent spyWorker = spy(ragWorker);
        doReturn(reactAgent).when(spyWorker).createReactAgent();

        doThrow(new RuntimeException("模型调用失败"))
                .when(reactAgent).call(anyList());

        String result = spyWorker.chat("测试错误处理", null);

        assertNotNull(result);
        assertTrue(result.contains("执行失败"));
        assertTrue(result.contains("RAGAgent"));
    }

    // ===== 测试2: Supervisor 分解任务为子任务 =====

    @Test
    void supervisorDecomposesTaskIntoSubtasks() throws Exception {
        String decompositionJson = """
                [
                  {"worker": "RAGAgent", "subtask": "从知识库检索AI相关信息"},
                  {"worker": "SearchAgent", "subtask": "搜索AI最新进展"}
                ]""";
        doReturn(mockChatResponse(decompositionJson))
                .when(chatModel).call(any(Prompt.class));
        doReturn(mockChatResponse("最终聚合结果"))
                .when(chatModel).call(any(Prompt.class));

        WorkerAgent spyRag = spy(ragWorker);
        doReturn(reactAgent).when(spyRag).createReactAgent();
        doReturn(mockAssistantResponse("AI知识库结果"))
                .when(reactAgent).call(anyList());

        WorkerAgent spySearch = spy(searchWorker);
        ReactAgent searchReactAgent = mock(ReactAgent.class);
        doReturn(searchReactAgent).when(spySearch).createReactAgent();
        doReturn(mockAssistantResponse("AI搜索结果"))
                .when(searchReactAgent).call(anyList());

        SupervisorAgent supervisor = new SupervisorAgent(
                "Supervisor", "主管", "你是任务主管",
                List.of(), chatModel, List.of(spyRag, spySearch));

        String result = supervisor.chat("帮我了解AI的全面信息", null);

        assertNotNull(result);
        assertEquals("最终聚合结果", result);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void supervisorHandlesEmptyWorkerList() throws Exception {
        SupervisorAgent supervisor = new SupervisorAgent(
                "Supervisor", "主管", "你是主管",
                List.of(), chatModel, List.of());

        doReturn(mockChatResponse("[]"))
                .when(chatModel).call(any(Prompt.class));
        doReturn(mockChatResponse("直接回答"))
                .when(chatModel).call(any(Prompt.class));

        String result = supervisor.chat("测试问题", null);

        assertNotNull(result);
    }

    // ===== 测试3: 多个 Worker 并行执行 =====

    @Test
    void multipleWorkersExecuteInParallel() throws Exception {
        WorkerAgent spyRag = spy(ragWorker);
        doReturn(reactAgent).when(spyRag).createReactAgent();
        doAnswer(invocation -> {
            Thread.sleep(50);
            return mockAssistantResponse("RAG结果");
        }).when(reactAgent).call(anyList());

        WorkerAgent spySearch = spy(searchWorker);
        ReactAgent searchReactAgent = mock(ReactAgent.class);
        doReturn(searchReactAgent).when(spySearch).createReactAgent();
        doAnswer(invocation -> {
            Thread.sleep(50);
            return mockAssistantResponse("Search结果");
        }).when(searchReactAgent).call(anyList());

        String decompositionJson = """
                [
                  {"worker": "RAGAgent", "subtask": "子任务1"},
                  {"worker": "SearchAgent", "subtask": "子任务2"}
                ]""";
        doReturn(mockChatResponse(decompositionJson))
                .when(chatModel).call(any(Prompt.class));
        doReturn(mockChatResponse("聚合结果"))
                .when(chatModel).call(any(Prompt.class));

        SupervisorAgent supervisor = new SupervisorAgent(
                "Supervisor", "主管", "你是主管",
                List.of(), chatModel, List.of(spyRag, spySearch));

        long start = System.currentTimeMillis();
        String result = supervisor.chat("并行测试", null);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(elapsed < 200, "并行执行应比串行快, 实际耗时: " + elapsed + "ms");
    }

    @Test
    void supervisorHandlesWorkerNotFound() throws Exception {
        WorkerAgent spyRag = spy(ragWorker);
        doReturn(reactAgent).when(spyRag).createReactAgent();
        doReturn(mockAssistantResponse("RAG结果"))
                .when(reactAgent).call(anyList());

        String decompositionJson = """
                [
                  {"worker": "RAGAgent", "subtask": "有效子任务"},
                  {"worker": "NonExistentAgent", "subtask": "无效子任务"}
                ]""";
        doReturn(mockChatResponse(decompositionJson))
                .when(chatModel).call(any(Prompt.class));
        doReturn(mockChatResponse("聚合结果"))
                .when(chatModel).call(any(Prompt.class));

        SupervisorAgent supervisor = new SupervisorAgent(
                "Supervisor", "主管", "你是主管",
                List.of(), chatModel, List.of(spyRag));

        String result = supervisor.chat("测试", null);

        assertNotNull(result);
    }

    // ===== 测试4: Supervisor 聚合结果 =====

    @Test
    void supervisorAggregatesWorkerResults() throws Exception {
        WorkerAgent spyRag = spy(ragWorker);
        doReturn(reactAgent).when(spyRag).createReactAgent();
        doReturn(mockAssistantResponse("知识库: AI是人工智能"))
                .when(reactAgent).call(anyList());

        WorkerAgent spySearch = spy(searchWorker);
        ReactAgent searchAgent = mock(ReactAgent.class);
        doReturn(searchAgent).when(spySearch).createReactAgent();
        doReturn(mockAssistantResponse("搜索: AI在2024年取得重大突破"))
                .when(searchAgent).call(anyList());

        String decompositionJson = """
                [
                  {"worker": "RAGAgent", "subtask": "检索AI定义"},
                  {"worker": "SearchAgent", "subtask": "搜索AI最新动态"}
                ]""";
        String expectedAggregation = "AI（人工智能）是计算机科学的一个分支。2024年AI取得了重大突破。";
        doReturn(mockChatResponse(decompositionJson))
                .when(chatModel).call(any(Prompt.class));
        doReturn(mockChatResponse(expectedAggregation))
                .when(chatModel).call(any(Prompt.class));

        SupervisorAgent supervisor = new SupervisorAgent(
                "Supervisor", "主管", "你是主管",
                List.of(), chatModel, List.of(spyRag, spySearch));

        String result = supervisor.chat("AI全面介绍", null);

        assertEquals(expectedAggregation, result);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ===== 测试5: AgentRegistry CRUD =====

    @Test
    void registryRegisterAndGetAgent() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        WorkerAgent customWorker = new WorkerAgent(
                "CustomAgent", "自定义Agent", "自定义提示词",
                List.of(), chatModel, resolver);

        registry.registerAgent(customWorker);

        Optional<BaseAgent> found = registry.getAgent("CustomAgent");
        assertTrue(found.isPresent());
        assertEquals("CustomAgent", found.get().getName());
        assertEquals("自定义Agent", found.get().getDescription());
    }

    @Test
    void registryUnregisterAgent() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        WorkerAgent tempWorker = new WorkerAgent(
                "TempAgent", "临时Agent", "临时",
                List.of(), chatModel, resolver);
        registry.registerAgent(tempWorker);

        assertTrue(registry.getAgent("TempAgent").isPresent());

        BaseAgent removed = registry.unregisterAgent("TempAgent");
        assertNotNull(removed);
        assertEquals("TempAgent", removed.getName());
        assertFalse(registry.getAgent("TempAgent").isPresent());
    }

    @Test
    void registryReturnsEmptyForUnknownAgent() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        Optional<BaseAgent> result = registry.getAgent("UnknownAgent");
        assertFalse(result.isPresent());
    }

    @Test
    void registryGetAllAgents() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        // Default agents: RAGAgent, SearchAgent, CodeAgent
        List<BaseAgent> allAgents = registry.getAllAgents();
        assertEquals(3, allAgents.size());

        // Register one more
        registry.registerAgent(new WorkerAgent(
                "ExtraAgent", "额外Agent", "额外",
                List.of(), chatModel, resolver));

        assertEquals(4, registry.getAllAgents().size());
    }

    @Test
    void registryGetWorkersOnly() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        // All default agents are Workers
        List<WorkerAgent> workers = registry.getWorkers();
        assertEquals(3, workers.size());
        assertTrue(workers.stream().allMatch(w -> w instanceof WorkerAgent));
    }

    @Test
    void registryCreateSupervisor() {
        AgentRegistry registry = new AgentRegistry(null, chatModel);

        SupervisorAgent supervisor = registry.createSupervisor(
                "MainSupervisor", "主管提示词", chatModel);

        assertNotNull(supervisor);
        assertEquals("MainSupervisor", supervisor.getName());
        assertEquals(3, supervisor.getWorkers().size()); // RAG, Search, Code
        assertTrue(registry.getAgent("MainSupervisor").isPresent());
    }

    @Test
    void registryOverwriteAgentOnDuplicateName() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        WorkerAgent agent1 = new WorkerAgent(
                "SameName", "第一个", "提示词1",
                List.of(), chatModel, resolver);
        WorkerAgent agent2 = new WorkerAgent(
                "SameName", "第二个", "提示词2",
                List.of(), chatModel, resolver);

        registry.registerAgent(agent1);
        registry.registerAgent(agent2);

        Optional<BaseAgent> found = registry.getAgent("SameName");
        assertTrue(found.isPresent());
        assertEquals("第二个", found.get().getDescription());
        assertEquals(3 + 1, registry.getAllAgents().size()); // 3 default + 1
    }

    @Test
    void registryUnregisterNonExistentReturnsNull() {
        AgentRegistry registry = new AgentRegistry(null, resolver);

        BaseAgent removed = registry.unregisterAgent("NonExistent");
        assertNull(removed);
    }

    // ===== 辅助方法 =====

    private ChatResponse mockChatResponse(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        return response;
    }

    private AssistantMessage mockAssistantResponse(String text) {
        return new AssistantMessage(text);
    }
}
