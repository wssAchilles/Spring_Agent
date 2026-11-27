package tech.qiantong.qknow.module.kb;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.ai.enums.model.AiPlatformEnum;
import tech.qiantong.qknow.ai.gateway.circuitbreaker.AdaptiveCircuitBreaker;
import tech.qiantong.qknow.ai.gateway.model.ChannelStatus;
import tech.qiantong.qknow.ai.transformer.SemanticSplitter;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.mcp.server.config.McpServerAutoConfiguration;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;
import tech.qiantong.qknow.module.ai.api.modelMarket.AiModelApiServiceImpl;
import tech.qiantong.qknow.module.ai.dal.dataobject.modelMarket.AiApiKeyDO;
import tech.qiantong.qknow.module.ai.dal.mapper.modelMarket.AiApiKeyMapper;
import tech.qiantong.qknow.module.app.controller.admin.appGraph.vo.AppGraphRelationshipSaveReqVO;
import tech.qiantong.qknow.module.app.service.appGraph.impl.AppGraphServiceImpl;
import tech.qiantong.qknow.module.kb.dal.dataobject.feedback.KbChatFeedbackDO;
import tech.qiantong.qknow.module.kb.service.feedback.FeedbackStreamQueueService;
import tech.qiantong.qknow.module.kb.service.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kb.tool.function.TimeToolComponent;
import tech.qiantong.qknow.neo4j.domain.DynamicEntity;
import tech.qiantong.qknow.neo4j.repository.DynamicRepository;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 战役四（4.1 ~ 4.4）：MCP 自动装配防线、KB 工作流/反馈流、App 图谱与 AI 框架回归测试
 */
@ExtendWith(MockitoExtension.class)
class ControlPlaneAndAiFrameworkBugFixTest {

    @Test
    @DisplayName("4.1 McpServerAutoConfiguration: 装配 McpServerRegistry 时必须注入非空的 QuadDefenseSecurityPipeline")
    void mcpServerAutoConfiguration_injectsNonNullQuadDefenseSecurityPipeline() throws Exception {
        McpServerAutoConfiguration config = new McpServerAutoConfiguration();
        McpServerRegistry registry = config.mcpServerRegistry();
        Field pipelineField = McpServerRegistry.class.getDeclaredField("securityPipeline");
        pipelineField.setAccessible(true);
        Object pipeline = pipelineField.get(registry);
        assertNotNull(pipeline, "McpServerRegistry 必须注入 QuadDefenseSecurityPipeline，严禁安全防线轮空");
    }

    @Test
    @DisplayName("4.2 FeedbackStreamQueueService 有界冷备归档、BaseNodeBO 含 $ 模板替换与 TimeToolComponent 默认格式")
    @SuppressWarnings("unchecked")
    void kbWorkflowAndFeedback_fixUnboundedArchiveDollarReplacementAndTimeTool() throws Exception {
        // 1. FeedbackStreamQueueService 归档必须有界（<= 1024）防 OOM
        FeedbackStreamQueueService queueService = new FeedbackStreamQueueService();
        for (int i = 0; i < 1500; i++) {
            KbChatFeedbackDO fb = new KbChatFeedbackDO();
            fb.setMessageId("m-" + i);
            queueService.enqueueFeedback(fb);
        }
        Field archiveField = FeedbackStreamQueueService.class.getDeclaredField("inMemoryArchive");
        archiveField.setAccessible(true);
        Collection<?> archive = (Collection<?>) archiveField.get(queueService);
        assertTrue(archive.size() <= 1024,
                "FeedbackStreamQueueService.inMemoryArchive 必须为定长有界集合，实际大小=" + archive.size());

        // 2. BaseNodeBO.format 处理含 $ 与反斜杠变量值不得抛 IllegalArgumentException
        BaseNodeBO dummyNode = new BaseNodeBO(null, List.of()) {
            @Override
            protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
                return null;
            }
        };
        RuntimeContextBO ctx = new RuntimeContextBO();
        JSONObject vars = new JSONObject();
        vars.put("price", "$100.00\\unit");
        ctx.setVariables(vars);
        String formatted = assertDoesNotThrow(() -> dummyNode.format("Total: {{price}}", ctx));
        assertEquals("Total: $100.00\\unit", formatted);

        // 3. TimeToolComponent.FormatTimeTool 当 formatPattern 为 null 时不得崩溃
        TimeToolComponent timeComponent = new TimeToolComponent();
        TimeToolComponent.FormatTimeRequest req = new TimeToolComponent.FormatTimeRequest();
        req.setTimeStr("2026-11-18 12:30:45");
        req.setFormatPattern(null);
        String formattedTime = assertDoesNotThrow(() -> timeComponent.formatTimeTool().apply(req));
        assertEquals("2026-11-18 12:30:45", formattedTime);
    }

    @Test
    @DisplayName("4.3 AppGraphServiceImpl: addNode 正确匹配 Long 主键，addTripletRel 删除旧关系而非误删节点")
    void appGraphService_matchesLongNodeIdAndDeletesRelationshipsNotNodes() {
        AppGraphServiceImpl appGraphService = new AppGraphServiceImpl();
        DynamicRepository repository = mock(DynamicRepository.class);
        ReflectionTestUtils.setField(appGraphService, "dynamicRepository", repository);

        DynamicEntity existingNode = new DynamicEntity();
        existingNode.setId(501L);
        existingNode.setName("旧名称");
        when(repository.findAllById(any())).thenReturn(List.of(existingNode));

        JSONObject updateNodeJson = new JSONObject();
        updateNodeJson.put("id", 501L);
        updateNodeJson.put("name", "新名称");
        updateNodeJson.put("entityType", 1);

        assertDoesNotThrow(() -> appGraphService.addNode(new JSONArray(List.of(updateNodeJson))),
                "addNode 更新已有节点时必须按 Long ID 命中 entityMap，不得因 String/Long 键错配抛 NPE");
        assertEquals("新名称", existingNode.getName());

        // 验证关系更新调用 deleteRelationshipsByIds 而非 deleteNodeByIds
        DynamicEntity e1 = new DynamicEntity(); e1.setId(1L);
        DynamicEntity e2 = new DynamicEntity(); e2.setId(2L);
        when(repository.findAllById(any())).thenReturn(List.of(e1, e2));

        AppGraphRelationshipSaveReqVO relVO = new AppGraphRelationshipSaveReqVO();
        relVO.setId(901L);
        relVO.setEntityId1(1L);
        relVO.setEntityId2(2L);
        relVO.setRelationshipType("BELONGS_TO");

        appGraphService.addTripletRel(List.of(relVO));
        verify(repository, never()).deleteNodeByIds(anyList());
        verify(repository, times(1)).deleteRelationshipsByIds(List.of(901L));
    }

    @Test
    @DisplayName("4.4a DeepSeekCompatibleChatModel: reasoningEffort 开启思考时拦截 temperature 并对齐 deepseek-flash")
    void deepSeekChatModelAndPlatform_enforcesThinkingModeRulesAndFlashBaseline() {
        DeepSeekCompatibleChatModel model = new DeepSeekCompatibleChatModel(
                "https://api.deepseek.com", "sk-test", "deepseek-flash", 0.7, 2048, null);
        DeepSeekChatOptions opts = new DeepSeekChatOptions();
        opts.setReasoningEffort("high");
        opts.setTemperature(0.6);
        opts.setTopP(0.5);
        opts.setStopSequences(List.of("<END>"));

        Map<String, Object> body = model.buildRequestBody(new Prompt(List.of(new UserMessage("hi")), opts), false);
        assertFalse(body.containsKey("temperature"),
                "设置 reasoning_effort 进入思考模式时严禁携带 temperature 参数");
        double topP = ((Number) body.get("top_p")).doubleValue();
        assertTrue(topP >= 0.95 && topP <= 1.0,
                "思考模式下 top_p 必须钳位在官方要求的 [0.95, 1.0] 范围内，实际=" + topP);
        assertEquals(List.of("<END>"), body.get("stop"));

        assertTrue(AiPlatformEnum.DEEP_SEEK.getDescription().contains("deepseek-flash"),
                "AiPlatformEnum.DEEP_SEEK 描述应指向活跃模型 deepseek-flash");
    }

    @Test
    @DisplayName("4.4b-c AiModelApiServiceImpl 禁止跨平台错配回退、SemanticSplitter 保护版本号且强制切超长块、AdaptiveCircuitBreaker 半开计数精确")
    void aiModelApiSplitterAndCircuitBreaker_behaveCorrectly() {
        // 1. AiModelApiServiceImpl: 仅存在 DeepSeek Key 时，请求 TongYi Embedding 不得错配回退 DeepSeek Key
        AiModelApiServiceImpl apiService = new AiModelApiServiceImpl();
        AiApiKeyMapper keyMapper = mock(AiApiKeyMapper.class);
        ReflectionTestUtils.setField(apiService, "apiKeyMapper", keyMapper);
        AiApiKeyDO dsKey = new AiApiKeyDO();
        dsKey.setId(1L);
        dsKey.setPlatform("DeepSeek");
        dsKey.setApiKey("sk-deepseek-only");
        when(keyMapper.selectList(any())).thenReturn(List.of(dsKey));

        assertThrows(ServiceException.class,
                () -> apiService.getEmbeddingModelByPlatform("TongYi", "text-embedding-v4"),
                "缺少 TongYi 向量平台 Key 时必须明确报错，不得盲目拿异构 DeepSeek Key 调 Embedding");

        // 2. SemanticSplitter: 保护版本号小数点不碎裂，且相似度恒为 1.0 时超 maxChunkSize 仍强制切块
        EmbeddingModel mockEmb = mock(EmbeddingModel.class);
        when(mockEmb.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest r = inv.getArgument(0);
            float[] unit = new float[1024];
            unit[0] = 1.0f;
            List<Embedding> out = new java.util.ArrayList<>();
            for (int i = 0; i < r.getInstructions().size(); i++) {
                out.add(new Embedding(unit, i));
            }
            return new EmbeddingResponse(out);
        });
        SemanticSplitter splitter = new SemanticSplitter(mockEmb, 45, 10, 0.5);
        List<Document> docs = splitter.split(List.of(new Document(
                "Release v2.2.1 supports Python 3.11 smoothly. Another sentence keeps similarity high for chunk limit check.")));
        assertTrue(docs.size() >= 2, "总长超 maxChunkSize=45 时即使相似度高也必须强制切分为多块，实际块数=" + docs.size());
        assertTrue(docs.get(0).getText().contains("v2.2.1"), "版本号 v2.2.1 不得在小数点处被误切碎");

        // 3. AdaptiveCircuitBreaker: permittedNumberOfCallsInHalfOpenState=2 时仅放行 2 次探针，第 3 次必须拒绝
        AdaptiveCircuitBreaker cb = new AdaptiveCircuitBreaker("ch_test", 2, 0.5, 1L, 2, 1);
        cb.getState().set(ChannelStatus.OPEN);
        cb.getLastStateChangeTimestamp().set(System.currentTimeMillis() - 100L);
        assertTrue(cb.tryAcquirePermission(), "第 1 次探针切换至 HALF_OPEN 放行");
        assertTrue(cb.tryAcquirePermission(), "第 2 次探针放行");
        assertFalse(cb.tryAcquirePermission(), "第 3 次探针超出半开配额 2，必须拒绝（消除 N+1 放行漏洞）");
    }
}
