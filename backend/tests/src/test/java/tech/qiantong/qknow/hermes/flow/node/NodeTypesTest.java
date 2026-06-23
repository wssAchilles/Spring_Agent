package tech.qiantong.qknow.hermes.flow.node;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.flow.bo.*;
import tech.qiantong.qknow.hermes.flow.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.flow.expression.ExpressionEngine;
import tech.qiantong.qknow.hermes.flow.factory.NodeFactory;
import tech.qiantong.qknow.hermes.flow.rag.RagRetrievalService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeTypesTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ExpressionEngine expressionEngine;

    @Mock
    private RagRetrievalService ragRetrievalService;

    private NodeFactory nodeFactory;

    @BeforeEach
    void setUp() {
        nodeFactory = new NodeFactory();
        var chatField = org.springframework.util.ReflectionUtils.findField(NodeFactory.class, "chatModelFactory");
        chatField.setAccessible(true);
        org.springframework.util.ReflectionUtils.setField(chatField, nodeFactory, chatModelFactory);

        var ragField = org.springframework.util.ReflectionUtils.findField(NodeFactory.class, "ragRetrievalService");
        ragField.setAccessible(true);
        org.springframework.util.ReflectionUtils.setField(ragField, nodeFactory, ragRetrievalService);
    }

    private KbFlowNodeDO buildNodeDef(String uuid, String name, Integer type, String config) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid(uuid);
        node.setName(name);
        node.setType(type);
        node.setConfig(config);
        node.setInput("[]");
        return node;
    }

    private RuntimeContextBO buildContext() {
        KbRuntimeDO runtime = new KbRuntimeDO();
        return new RuntimeContextBO(runtime, new JSONObject());
    }

    @Nested
    @DisplayName("HttpNodeBO 测试")
    class HttpNodeBOTest {

        @Test
        @DisplayName("HttpNodeBO 执行成功并存储响应")
        void executeStoresResponseInContext() throws Exception {
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/get", exchange -> {
                byte[] response = "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (java.io.OutputStream body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();

            JSONObject config = new JSONObject();
            config.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/get");
            config.put("method", "GET");

            KbFlowNodeDO nodeDef = buildNodeDef("http-1", "HTTP请求", FlowNodeTypeEnums.HTTP.getCode(), config.toJSONString());
            List<KbFlowEdgeDO> edges = List.of();

            HttpNodeBO httpNode = new HttpNodeBO(nodeDef, edges);
            RuntimeContextBO context = buildContext();

            try {
                NodeRunResultBO result = httpNode.execute(context);

                assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
                assertNotNull(result.getOutput().get("http-1.response"));
            } finally {
                server.stop(0);
            }
        }
    }

    @Nested
    @DisplayName("KnowledgeNodeBO 测试")
    class KnowledgeNodeBOTest {

        @Test
        @DisplayName("KnowledgeNodeBO 检索成功并存储文档")
        void executeStoresRetrievedDocs() {
            JSONObject config = new JSONObject();
            config.put("knowledgeBaseId", "kb-001");
            config.put("query", "什么是知识图谱");

            KbFlowNodeDO nodeDef = buildNodeDef("kb-1", "知识库检索", FlowNodeTypeEnums.KNOWLEDGE.getCode(), config.toJSONString());
            List<KbFlowEdgeDO> edges = List.of();

            when(ragRetrievalService.retrieve("kb-001", "什么是知识图谱"))
                    .thenReturn(List.of("知识图谱是一种...", "图数据库..."));

            KnowledgeNodeBO kbNode = new KnowledgeNodeBO(nodeDef, edges, ragRetrievalService);
            RuntimeContextBO context = buildContext();

            NodeRunResultBO result = kbNode.execute(context);

            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
            Object docs = result.getOutput().get("kb-1.context");
            assertNotNull(docs);
            assertTrue(docs instanceof String);
            String docsStr = (String) docs;
            assertTrue(docsStr.contains("知识图谱"));
            assertTrue(docsStr.contains("图数据库"));
            verify(ragRetrievalService).retrieve("kb-001", "什么是知识图谱");
        }

        @Test
        @DisplayName("KnowledgeNodeBO 知识库ID为空时抛出异常")
        void executeThrowsWhenKnowledgeBaseIdBlank() {
            JSONObject config = new JSONObject();
            config.put("knowledgeBaseId", "");
            config.put("query", "test");

            KbFlowNodeDO nodeDef = buildNodeDef("kb-2", "知识库检索", FlowNodeTypeEnums.KNOWLEDGE.getCode(), config.toJSONString());

            KnowledgeNodeBO kbNode = new KnowledgeNodeBO(nodeDef, List.of(), ragRetrievalService);
            RuntimeContextBO context = buildContext();

            NodeRunResultBO result = kbNode.execute(context);
            assertEquals(RuntimeStatusEnums.ERROR.getCode(), result.getStatus());
        }
    }

    @Nested
    @DisplayName("AggregatorNodeBO 测试")
    class AggregatorNodeBOTest {

        @Test
        @DisplayName("AggregatorNodeBO concat 策略合并多个输入")
        void concatStrategyMergesInputs() {
            JSONObject config = new JSONObject();
            config.put("strategy", "concat");
            config.put("inputKeys", List.of("branch-a.text", "branch-b.text"));

            KbFlowNodeDO nodeDef = buildNodeDef("agg-1", "聚合节点", FlowNodeTypeEnums.AGGREGATOR.getCode(), config.toJSONString());
            List<KbFlowEdgeDO> edges = List.of();

            AggregatorNodeBO aggNode = new AggregatorNodeBO(nodeDef, edges);
            RuntimeContextBO context = buildContext();
            context.getVariables().put("branch-a.text", "结果A");
            context.getVariables().put("branch-b.text", "结果B");

            NodeRunResultBO result = aggNode.execute(context);

            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
            String merged = (String) result.getOutput().get("agg-1.merged");
            assertTrue(merged.contains("结果A"));
            assertTrue(merged.contains("结果B"));
        }

        @Test
        @DisplayName("AggregatorNodeBO highest_score 策略选择最高分")
        void highestScoreStrategyPicksBest() {
            JSONObject config = new JSONObject();
            config.put("strategy", "highest_score");
            config.put("inputKeys", List.of("branch-a.text", "branch-b.text"));

            KbFlowNodeDO nodeDef = buildNodeDef("agg-2", "聚合节点", FlowNodeTypeEnums.AGGREGATOR.getCode(), config.toJSONString());

            AggregatorNodeBO aggNode = new AggregatorNodeBO(nodeDef, List.of());
            RuntimeContextBO context = buildContext();
            context.getVariables().put("branch-a.text", "低分结果");
            context.getVariables().put("branch-a.text.score", 0.3);
            context.getVariables().put("branch-b.text", "高分结果");
            context.getVariables().put("branch-b.text.score", 0.9);

            NodeRunResultBO result = aggNode.execute(context);

            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
            String merged = (String) result.getOutput().get("agg-2.merged");
            assertEquals("高分结果", merged);
        }
    }

    @Nested
    @DisplayName("NodeFactory 测试")
    class NodeFactoryTest {

        @Test
        @DisplayName("NodeFactory 能创建所有 7 种节点类型")
        void createsAllSevenNodeTypes() {
            List<KbFlowEdgeDO> edges = List.of();

            BaseNodeBO start = nodeFactory.createNode(
                    buildNodeDef("1", "开始", FlowNodeTypeEnums.START.getCode(), "{}"), edges);
            assertTrue(start instanceof StartNodeBO);

            BaseNodeBO llm = nodeFactory.createNode(
                    buildNodeDef("2", "LLM", FlowNodeTypeEnums.LLM.getCode(), "{}"), edges);
            assertTrue(llm instanceof LLMNodeBO);

            BaseNodeBO reply = nodeFactory.createNode(
                    buildNodeDef("3", "回复", FlowNodeTypeEnums.REPLY.getCode(), "{}"), edges);
            assertTrue(reply instanceof ReplyNodeBO);

            BaseNodeBO condition = nodeFactory.createNode(
                    buildNodeDef("4", "条件", FlowNodeTypeEnums.CONDITION.getCode(), "{}"), edges);
            assertTrue(condition instanceof ConditionNodeBO);

            BaseNodeBO http = nodeFactory.createNode(
                    buildNodeDef("5", "HTTP", FlowNodeTypeEnums.HTTP.getCode(), "{}"), edges);
            assertTrue(http instanceof HttpNodeBO);

            BaseNodeBO knowledge = nodeFactory.createNode(
                    buildNodeDef("6", "知识库", FlowNodeTypeEnums.KNOWLEDGE.getCode(), "{}"), edges);
            assertTrue(knowledge instanceof KnowledgeNodeBO);

            BaseNodeBO aggregator = nodeFactory.createNode(
                    buildNodeDef("7", "聚合", FlowNodeTypeEnums.AGGREGATOR.getCode(), "{}"), edges);
            assertTrue(aggregator instanceof AggregatorNodeBO);
        }

        @Test
        @DisplayName("NodeFactory 不支持的节点类型抛出异常")
        void throwsForUnsupportedNodeType() {
            KbFlowNodeDO unknownNode = buildNodeDef("x", "未知", 999, "{}");
            assertThrows(Exception.class, () -> nodeFactory.createNode(unknownNode, List.of()));
        }
    }
}
