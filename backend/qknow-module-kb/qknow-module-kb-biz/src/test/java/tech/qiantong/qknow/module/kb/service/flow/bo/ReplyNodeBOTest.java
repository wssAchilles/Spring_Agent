package tech.qiantong.qknow.module.kb.service.flow.bo;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.BotExecuteModeEnum;
import tech.qiantong.qknow.module.kb.dal.enums.BotTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReplyNodeBO 契约单元测试（覆盖 Chatflow 空模板兜底与防空保护）
 */
class ReplyNodeBOTest {

    /**
     * 构建基础节点定义、上游 LLM 节点及连线上下文
     */
    private RuntimeContextBO createFlowContext(BotTypeEnums botType,
                                               Map<String, Flux<String>> fluxMap,
                                               JSONObject variables) {
        KbFlowNodeDO llmNode = KbFlowNodeDO.builder()
                .uuid("llm_1")
                .name("DeepSeek 提纲与要点摘要")
                .type(FlowNodeTypeEnums.LLM.getCode())
                .build();

        RuntimeContextBO context = new RuntimeContextBO(null, variables);
        context.setBotType(botType);
        context.setExecuteMode(BotExecuteModeEnum.STREAM);
        context.setNodeMap(Map.of("llm_1", llmNode));
        context.setFluxMap(fluxMap);
        return context;
    }

    /**
     * 提取流式结果中非占位空对象的 JSON 载荷列表
     */
    private List<JSONObject> collectPayloads(Flux<CommonResult<String>> resultFlux) {
        List<CommonResult<String>> items = resultFlux.collectList().block();
        assertNotNull(items);
        return items.stream()
                .map(CommonResult::getData)
                .filter(Objects::nonNull)
                .filter(raw -> !"{}".equals(raw.trim()))
                .map(JSONObject::parseObject)
                .toList();
    }

    @Test
    void chatFlowWithoutContentShouldFallbackToUpstreamLlmFlux() {
        // 模拟 Bot 4 原始配置：缺少 content 字段的 reply_1 节点
        KbFlowNodeDO replyNodeDef = KbFlowNodeDO.builder()
                .uuid("reply_1")
                .name("回复")
                .type(FlowNodeTypeEnums.REPLY.getCode())
                .config("{\"label\":\"流式摘要答复输出\",\"replyType\":\"text\"}")
                .output("[]")
                .build();
        KbFlowEdgeDO edge = KbFlowEdgeDO.builder()
                .sourceNodeUuid("llm_1")
                .targetNodeUuid("reply_1")
                .build();

        RuntimeContextBO context = createFlowContext(
                BotTypeEnums.CHAT_FLOW,
                Map.of("llm_1", Flux.just("核心", "摘要")),
                new JSONObject()
        );

        ReplyNodeBO replyNode = new ReplyNodeBO(replyNodeDef, List.of(edge));
        NodeRunResultBO runResult = replyNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), runResult.getStatus());
        assertNotNull(context.getResultFlux());

        List<String> texts = collectPayloads(context.getResultFlux()).stream()
                .map(json -> json.getString("text"))
                .toList();
        assertEquals(List.of("核心", "摘要"), texts);
    }

    @Test
    void chatFlowWithExplicitTemplateShouldStreamLlmOutput() {
        // 显式配置 {{llm_1.text}} 模板时直接复用上游 LLM 流式切片
        KbFlowNodeDO replyNodeDef = KbFlowNodeDO.builder()
                .uuid("reply_1")
                .name("回复")
                .type(FlowNodeTypeEnums.REPLY.getCode())
                .config("{\"label\":\"流式摘要答复输出\",\"replyType\":\"text\",\"content\":\"{{llm_1.text}}\"}")
                .output("[]")
                .build();
        KbFlowEdgeDO edge = KbFlowEdgeDO.builder()
                .sourceNodeUuid("llm_1")
                .targetNodeUuid("reply_1")
                .build();

        RuntimeContextBO context = createFlowContext(
                BotTypeEnums.CHAT_FLOW,
                Map.of("llm_1", Flux.just("第一段", "第二段")),
                new JSONObject()
        );

        ReplyNodeBO replyNode = new ReplyNodeBO(replyNodeDef, List.of(edge));
        NodeRunResultBO runResult = replyNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), runResult.getStatus());
        assertNotNull(context.getResultFlux());

        List<String> texts = collectPayloads(context.getResultFlux()).stream()
                .map(json -> json.getString("text"))
                .toList();
        assertEquals(List.of("第一段", "第二段"), texts);
    }

    @Test
    void chatFlowWithUnknownPlaceholderShouldNotThrowNpe() {
        // 模板中出现未知占位符或无对应节点时，安全跳过缺失变量且不抛 NPE
        KbFlowNodeDO replyNodeDef = KbFlowNodeDO.builder()
                .uuid("reply_1")
                .name("回复")
                .type(FlowNodeTypeEnums.REPLY.getCode())
                .config("{\"content\":\"前缀 {{missing_var}} 后缀\"}")
                .output("[]")
                .build();

        RuntimeContextBO context = createFlowContext(
                BotTypeEnums.CHAT_FLOW,
                Map.of(),
                new JSONObject()
        );

        ReplyNodeBO replyNode = new ReplyNodeBO(replyNodeDef, List.of());
        NodeRunResultBO runResult = replyNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), runResult.getStatus());
        assertNotNull(context.getResultFlux());

        List<String> texts = collectPayloads(context.getResultFlux()).stream()
                .map(json -> json.getString("text"))
                .toList();
        assertEquals(List.of("前缀", "后缀"), texts);
    }

    @Test
    void workFlowWithEmptyOutputDefShouldFallbackToUpstreamLlmFlux() {
        // Workflow 模式下回复节点 output=[] 时，自动消费上游流并标记 name="output"
        KbFlowNodeDO replyNodeDef = KbFlowNodeDO.builder()
                .uuid("reply_1")
                .name("回复")
                .type(FlowNodeTypeEnums.REPLY.getCode())
                .config("{\"label\":\"结构化综合检索报告\",\"replyType\":\"text\"}")
                .output("[]")
                .build();
        KbFlowEdgeDO edge = KbFlowEdgeDO.builder()
                .sourceNodeUuid("llm_1")
                .targetNodeUuid("reply_1")
                .build();

        RuntimeContextBO context = createFlowContext(
                BotTypeEnums.WORK_FLOW,
                Map.of("llm_1", Flux.just("综合结论A", "综合结论B")),
                new JSONObject()
        );

        ReplyNodeBO replyNode = new ReplyNodeBO(replyNodeDef, List.of(edge));
        NodeRunResultBO runResult = replyNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), runResult.getStatus());
        assertNotNull(context.getResultFlux());

        List<JSONObject> payloads = collectPayloads(context.getResultFlux());
        assertEquals(3, payloads.size());
        assertEquals("output", payloads.get(0).getString("name"));
        assertEquals("综合结论A", payloads.get(0).getString("text"));
        assertEquals("output", payloads.get(1).getString("name"));
        assertEquals("综合结论B", payloads.get(1).getString("text"));
        assertEquals("output", payloads.get(2).getString("name"));
        assertEquals("\n", payloads.get(2).getString("text"));
    }
}
