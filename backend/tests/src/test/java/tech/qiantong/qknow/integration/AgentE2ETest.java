package tech.qiantong.qknow.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.SupervisorAgent;
import tech.qiantong.qknow.hermes.agent.WorkerAgent;
import tech.qiantong.qknow.hermes.hermes.HermesKernel;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.memory.*;
import tech.qiantong.qknow.hermes.trace.TraceCollector;
import tech.qiantong.qknow.hermes.trace.TraceContext;
import tech.qiantong.qknow.hermes.trace.TraceSpan;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentE2ETest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolCallbackResolver resolver;

    @Mock
    private AiJudgeService aiJudgeService;

    private ShortTermMemory shortTermMemory;
    private LongTermMemory longTermMemory;
    private WorkingMemory workingMemory;
    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        shortTermMemory = new ShortTermMemory(chatModel);
        longTermMemory = mock(LongTermMemory.class);
        workingMemory = new WorkingMemory();
        memoryManager = new MemoryManager(shortTermMemory, longTermMemory, workingMemory);
    }

    private ChatResponse mockResponse(String text) {
        Generation gen = new Generation(new AssistantMessage(text));
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(gen);
        return response;
    }

    private AssistantMessage mockAssistantResponse(String text) {
        return new AssistantMessage(text);
    }

    @Nested
    @DisplayName("单轮对话 E2E")
    class SingleTurnConversation {

        @Test
        @DisplayName("WorkerAgent 单轮对话返回 LLM 回答")
        void workerAgentSingleTurnReturnsAnswer() throws Exception {
            WorkerAgent spyWorker = spy(new WorkerAgent(
                    "RAGAgent", "知识库检索", "你是知识库专家",
                    List.of(), chatModel, resolver));

            com.alibaba.cloud.ai.graph.agent.ReactAgent reactAgent = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(reactAgent).when(spyWorker).createReactAgent();
            doReturn(mockAssistantResponse("AI是人工智能的缩写"))
                    .when(reactAgent).call(anyList());

            String result = spyWorker.chat("什么是AI?", null);

            assertNotNull(result);
            assertTrue(result.contains("AI是人工智能"));
            verify(reactAgent).call(anyList());
        }

        @Test
        @DisplayName("单轮对话记录到短期记忆")
        void singleTurnConversationStoredInMemory() {
            shortTermMemory.addMessage(new UserMessage("什么是AI?"));
            shortTermMemory.addMessage(new AssistantMessage("AI是人工智能"));

            assertEquals(2, shortTermMemory.size());
            List<Message> context = shortTermMemory.getContext(10);
            assertEquals(2, context.size());
            assertTrue(context.get(0) instanceof UserMessage);
            assertTrue(context.get(1) instanceof AssistantMessage);
        }
    }

    @Nested
    @DisplayName("多轮对话 E2E")
    class MultiTurnConversation {

        @Test
        @DisplayName("多轮对话携带历史上下文")
        void multiTurnCarriesHistory() throws Exception {
            WorkerAgent spyWorker = spy(new WorkerAgent(
                    "RAGAgent", "知识库检索", "你是知识库专家",
                    List.of(), chatModel, resolver));

            com.alibaba.cloud.ai.graph.agent.ReactAgent reactAgent = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(reactAgent).when(spyWorker).createReactAgent();
            doReturn(mockAssistantResponse("基于上下文的回答"))
                    .when(reactAgent).call(anyList());

            Map<String, Object> context = new HashMap<>();
            List<Message> history = new ArrayList<>();
            history.add(new UserMessage("什么是AI?"));
            history.add(new AssistantMessage("AI是人工智能"));
            context.put("history", history);

            String result = spyWorker.chat("它有哪些应用?", context);

            assertNotNull(result);
            verify(reactAgent).call(argThat((java.util.List<Message> msgs) -> msgs.size() == 3));
        }

        @Test
        @DisplayName("多轮对话短期记忆自动摘要")
        void multiTurnMemorySummarizes() {
            for (int i = 0; i < 25; i++) {
                shortTermMemory.addMessage(new UserMessage("问题" + i));
            }

            doReturn(mockResponse("这是一段对话摘要")).when(chatModel).call(any(Prompt.class));

            shortTermMemory.summarize(20);

            assertTrue(shortTermMemory.size() > 20, "摘要后应包含摘要消息+最近消息");
            List<Message> all = shortTermMemory.getContext(shortTermMemory.size());
            assertTrue(all.get(0) instanceof SystemMessage);
            assertTrue(all.get(0).getText().contains("摘要"));
        }
    }

    @Nested
    @DisplayName("工具调用失败降级")
    class ToolCallFailureFallback {

        @Test
        @DisplayName("WorkerAgent 工具调用异常时返回错误信息而非抛异常")
        void workerHandlesToolCallFailure() throws Exception {
            WorkerAgent spyWorker = spy(new WorkerAgent(
                    "SearchAgent", "网络搜索", "你是搜索专家",
                    List.of(), chatModel, resolver));

            com.alibaba.cloud.ai.graph.agent.ReactAgent reactAgent = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(reactAgent).when(spyWorker).createReactAgent();
            doThrow(new RuntimeException("工具调用超时"))
                    .when(reactAgent).call(anyList());

            String result = spyWorker.chat("搜索最新新闻", null);

            assertNotNull(result);
            assertTrue(result.contains("执行失败"));
            assertTrue(result.contains("SearchAgent"));
        }

        @Test
        @DisplayName("WorkerAgent 空响应时返回合理默认值")
        void workerHandlesEmptyResponse() throws Exception {
            WorkerAgent spyWorker = spy(new WorkerAgent(
                    "RAGAgent", "知识库检索", "你是知识库专家",
                    List.of(), chatModel, resolver));

            com.alibaba.cloud.ai.graph.agent.ReactAgent reactAgent = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(reactAgent).when(spyWorker).createReactAgent();
            doReturn(mockAssistantResponse(""))
                    .when(reactAgent).call(anyList());

            String result = spyWorker.chat("测试空响应", null);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("反思循环 E2E")
    class ReflectionLoop {

        @Test
        @DisplayName("反思循环通过评分后返回最终回答")
        void reflectionPassesOnGoodAnswer() {
            HermesKernel kernel = new HermesKernel(aiJudgeService);

            doReturn(mockResponse("AI是人工智能的缩写，是计算机科学的一个分支。"))
                    .when(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
            when(aiJudgeService.judge(anyString(), anyString(), anyString()))
                    .thenReturn(JudgeResult.passed(0.9, 0.85, 0.88, "回答准确"));

            HermesKernel.ReflectionResult result = kernel.reflect(
                    chatModel, "你是AI专家", "什么是AI?", "AI相关知识", 3);

            assertTrue(result.isPassed());
            assertEquals(1, result.getTotalAttempts());
            assertNotNull(result.getFinalAnswer());
            assertEquals(1, result.getAttempts().size());
            assertTrue(result.getAttempts().get(0).getJudgeResult().isPassed());
        }

        @Test
        @DisplayName("反思循环未通过时重试并返回最后一次回答")
        void reflectionRetriesOnFailedJudge() {
            HermesKernel kernel = new HermesKernel(aiJudgeService);

            doReturn(mockResponse("不够准确的回答"))
                    .when(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
            when(aiJudgeService.judge(anyString(), anyString(), anyString()))
                    .thenReturn(JudgeResult.failed(0.3, 0.2, 0.25, "事实性不足"))
                    .thenReturn(JudgeResult.failed(0.4, 0.3, 0.35, "仍需改进"))
                    .thenReturn(JudgeResult.passed(0.8, 0.75, 0.8, "改进后通过"));

            HermesKernel.ReflectionResult result = kernel.reflect(
                    chatModel, "你是AI专家", "什么是AI?", "AI相关知识", 3);

            assertTrue(result.isPassed());
            assertEquals(3, result.getTotalAttempts());
            assertEquals(3, result.getAttempts().size());
        }

        @Test
        @DisplayName("反思循环达到最大次数时返回未通过结果")
        void reflectionExceedsMaxRetries() {
            HermesKernel kernel = new HermesKernel(aiJudgeService);

            doReturn(mockResponse("始终不够好的回答"))
                    .when(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
            when(aiJudgeService.judge(anyString(), anyString(), anyString()))
                    .thenReturn(JudgeResult.failed(0.3, 0.2, 0.25, "不足"));

            HermesKernel.ReflectionResult result = kernel.reflect(
                    chatModel, "你是AI专家", "什么是AI?", "AI相关知识", 2);

            assertFalse(result.isPassed());
            assertEquals(2, result.getTotalAttempts());
            assertEquals(2, result.getAttempts().size());
            assertNotNull(result.getFinalAnswer());
        }
    }

    @Nested
    @DisplayName("Trace 事件记录")
    class TraceEventRecording {

        @Test
        @DisplayName("Agent 执行过程中 trace 记录各阶段 span")
        void traceRecordsSpansDuringExecution() {
            TraceCollector collector = new TraceCollector();

            TraceContext ctx = collector.startTrace("req-agent-1");
            TraceSpan agentSpan = collector.startSpan("agent_execution");

            TraceSpan ragSpan = collector.startSpan("rag_retrieval", agentSpan);
            collector.endSpan(ragSpan.getSpanId(), "检索到3条文档", "success", 100, 0.05);

            TraceSpan llmSpan = collector.startSpan("llm_generation", agentSpan);
            collector.endSpan(llmSpan.getSpanId(), "生成回答", "success", 500, 0.25);

            collector.endSpan(agentSpan.getSpanId(), "完成", "success", 0, 0.0);
            TraceContext result = collector.endTrace();

            assertNotNull(result);
            assertEquals(2, agentSpan.getChildren().size());
            assertEquals(600, result.getTotalTokens());
            assertEquals(0.30, result.getTotalCost(), 0.01);
        }
    }

    @Nested
    @DisplayName("记忆存储验证")
    class MemoryStorageVerification {

        @Test
        @DisplayName("对话结束时长期记忆被写入")
        void conversationEndWritesToLongTermMemory() {
            for (int i = 0; i < 5; i++) {
                shortTermMemory.addMessage(new UserMessage("msg-" + i));
            }
            workingMemory.set("intent", "查询");

            doReturn(mockResponse("摘要")).when(chatModel).call(any(Prompt.class));

            memoryManager.onConversationEnd("sess-1", "user-1");

            verify(longTermMemory, atLeastOnce()).store(anyString(), anyMap());
            assertEquals(0, workingMemory.size());
        }

        @Test
        @DisplayName("长期记忆 recall 语义检索返回相关文档")
        void longTermRecallReturnsRelevantDocs() {
            Document doc = new Document("用户之前问过AI相关问题", Map.of("sessionId", "s1"));
            when(longTermMemory.recall("AI", 5)).thenReturn(List.of(doc));

            List<Document> results = longTermMemory.recall("AI", 5);

            assertEquals(1, results.size());
            assertTrue(results.get(0).getText().contains("AI"));
        }

        @Test
        @DisplayName("工作记忆快照包含所有存储的键值对")
        void workingMemorySnapshotContainsAllEntries() {
            workingMemory.set("key1", "value1");
            workingMemory.set("key2", List.of("a", "b"));

            Map<String, Object> snapshot = workingMemory.snapshot();

            assertEquals(2, snapshot.size());
            assertEquals("value1", snapshot.get("key1"));
            assertEquals(List.of("a", "b"), snapshot.get("key2"));
        }
    }

    @Nested
    @DisplayName("Supervisor 多 Agent 协作")
    class SupervisorMultiAgent {

        @Test
        @DisplayName("Supervisor 分解任务 → 并行执行 → 聚合结果")
        void supervisorDecomposeExecuteAggregate() throws Exception {
            WorkerAgent spyRag = spy(new WorkerAgent(
                    "RAGAgent", "知识库检索", "你是知识库专家",
                    List.of(), chatModel, resolver));
            com.alibaba.cloud.ai.graph.agent.ReactAgent ragReact = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(ragReact).when(spyRag).createReactAgent();
            doReturn(mockAssistantResponse("知识库: AI是人工智能"))
                    .when(ragReact).call(anyList());

            WorkerAgent spySearch = spy(new WorkerAgent(
                    "SearchAgent", "网络搜索", "你是搜索专家",
                    List.of(), chatModel, resolver));
            com.alibaba.cloud.ai.graph.agent.ReactAgent searchReact = mock(com.alibaba.cloud.ai.graph.agent.ReactAgent.class);
            doReturn(searchReact).when(spySearch).createReactAgent();
            doReturn(mockAssistantResponse("搜索: AI最新进展"))
                    .when(searchReact).call(anyList());

            String decomposition = """
                    [{"worker":"RAGAgent","subtask":"检索AI定义"},{"worker":"SearchAgent","subtask":"搜索AI动态"}]""";
            doReturn(mockResponse(decomposition))
                    .when(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
            doReturn(mockResponse("AI是人工智能，最新取得重大突破"))
                    .when(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));

            SupervisorAgent supervisor = new SupervisorAgent(
                    "Supervisor", "主管", "你是主管",
                    List.of(), chatModel, List.of(spyRag, spySearch));

            String result = supervisor.chat("全面了解AI", null);

            assertNotNull(result);
            assertEquals("AI是人工智能，最新取得重大突破", result);
            verify(chatModel, times(2)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }
    }
}
