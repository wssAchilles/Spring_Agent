package tech.qiantong.qknow.module.kb.controller.admin.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbChatMessageSendRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.conversation.vo.*;
import tech.qiantong.qknow.module.kb.dal.dataobject.agent.KbAgentConfigDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbChatMessageDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbConversationDO;
import tech.qiantong.qknow.module.kb.service.agent.IKbAgentConfigService;
import tech.qiantong.qknow.module.kb.service.conversation.IKbChatMessageService;
import tech.qiantong.qknow.module.kb.service.conversation.IKbConversationService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Tag(name = "Agent 对话管理")
@RestController
@RequestMapping("/kb/conversation")
@Validated
public class KbConversationController extends BaseController {

    @Resource
    private IKbConversationService conversationService;

    @Resource
    private IKbChatMessageService chatMessageService;

    @Resource
    private IKbAgentConfigService agentConfigService;

    @Operation(summary = "获取对话列表")
    @GetMapping("/list")
    public CommonResult<List<KbConversationRespVO>> list(
            @RequestParam Long botId,
            @RequestParam Long workspaceId) {
        List<KbConversationDO> conversations = conversationService.getConversationsByBotId(botId, workspaceId);
        return CommonResult.success(BeanUtils.toBean(conversations, KbConversationRespVO.class));
    }

    @Operation(summary = "创建对话")
    @PostMapping
    public CommonResult<KbConversationRespVO> create(@Valid @RequestBody KbConversationSaveReqVO reqVO) {
        KbConversationDO conversation = conversationService.createConversation(
                reqVO.getBotId(), reqVO.getWorkspaceId(), reqVO.getTitle());
        return CommonResult.success(BeanUtils.toBean(conversation, KbConversationRespVO.class));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        chatMessageService.deleteByConversationId(id);
        return CommonResult.success(true);
    }

    @Operation(summary = "获取对话消息列表")
    @GetMapping("/{conversationId}/messages")
    public CommonResult<List<KbChatMessageRespVO>> getMessages(@PathVariable Long conversationId) {
        List<KbChatMessageDO> messages = chatMessageService.getMessagesByConversationId(conversationId);
        return CommonResult.success(BeanUtils.toBean(messages, KbChatMessageRespVO.class));
    }

    @Operation(summary = "发送消息 (SSE 流式)")
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<KbChatMessageSendRespVO> sendMessage(@Valid @RequestBody KbChatMessageSendReqVO reqVO) {
        // 获取历史消息，避免把本轮问题重复注入 history 和 question
        List<KbChatMessageDO> history = chatMessageService.getMessagesByConversationId(reqVO.getConversationId());

        // 保存用户消息
        chatMessageService.saveMessage(reqVO.getConversationId(), "user", reqVO.getQuestion());
        KbChatMessageDO assistantMessage = chatMessageService.saveMessage(reqVO.getConversationId(), "assistant", "思考中");

        // 获取 Agent 配置
        KbAgentConfigDO config = agentConfigService.getKbAgentConfigByBotId(reqVO.getBotId());
        if (config == null) {
            updateAssistantMessage(assistantMessage, "错误：Agent 配置不存在");
            throw new RuntimeException("Agent 配置不存在");
        }

        // 构建请求 - 包含对话历史
        KbAgentConfigReqVO agentReq = new KbAgentConfigReqVO();
        agentReq.setBotId(reqVO.getBotId());
        agentReq.setWorkspaceId(reqVO.getWorkspaceId());
        agentReq.setQuestion(reqVO.getQuestion());
        agentReq.setInput(reqVO.getInput());
        agentReq.setModelConfig(config.getModelConfig());
        agentReq.setPrePrompt(config.getPrePrompt());
        agentReq.setParameters(config.getParameters());
        agentReq.setKnowledgeIds(config.getKnowledgeIds());
        agentReq.setGraphIds(config.getGraphIds());
        agentReq.setToolMethodIds(config.getToolMethodIds());

        // 注入历史消息
        agentReq.setHistoryMessages(history);

        StringBuilder assistantContent = new StringBuilder();
        AtomicReference<String> assistantStatus = new AtomicReference<>("思考中");

        // 调用 Agent (流式)
        try {
            Sinks.Many<KbChatMessageSendRespVO> sink = Sinks.many().multicast().onBackpressureBuffer();
            AtomicBoolean clientConnected = new AtomicBoolean(true);
            agentConfigService.chatMessage(agentReq)
                    .subscribe(resp -> {
                        KbChatMessageSendRespVO.Message receive = resp.getReceive();
                        if (receive != null && receive.getContent() != null) {
                            String eventType = receive.getEventType();
                            if ("memory_recall".equals(eventType)) {
                                assistantStatus.set(receive.getContent() + "，正在生成回答");
                                updateAssistantMessage(assistantMessage, assistantStatus.get());
                            } else if ("tool_call".equals(eventType)) {
                                assistantStatus.set(receive.getContent());
                                updateAssistantMessage(assistantMessage, assistantStatus.get());
                            } else {
                                assistantContent.append(receive.getContent());
                                assistantStatus.set(assistantContent.toString());
                                updateAssistantMessage(assistantMessage, assistantStatus.get());
                            }
                        }
                        if (clientConnected.get()) {
                            sink.tryEmitNext(resp);
                        }
                    }, error -> {
                        String errorMessage = "回答生成失败：" + (error.getMessage() != null ? error.getMessage() : "未知异常");
                        if (!assistantContent.isEmpty()) {
                            errorMessage = assistantContent + "\n\n" + errorMessage;
                        }
                        updateAssistantMessage(assistantMessage, errorMessage);
                        if (clientConnected.get()) {
                            sink.tryEmitError(error);
                        }
                    }, () -> {
                        if (assistantContent.isEmpty()) {
                            String fallback = assistantStatus.get();
                            if (fallback == null || fallback.isBlank() || "思考中".equals(fallback)) {
                                fallback = "暂未生成文本回答，请稍后重试或检查模型调用配置。";
                            } else if (!fallback.contains("正在生成回答")) {
                                fallback = fallback + "，但模型未返回最终文本回答。";
                            } else {
                                fallback = fallback.replace("，正在生成回答", "，但模型未返回最终文本回答。");
                            }
                            updateAssistantMessage(assistantMessage, fallback);
                        }
                        if (clientConnected.get()) {
                            sink.tryEmitComplete();
                        }
                    });
            return sink.asFlux().doOnCancel(() -> clientConnected.set(false));
        } catch (Exception e) {
            throw new RuntimeException("Agent 调用失败: " + e.getMessage());
        }
    }

    private void updateAssistantMessage(KbChatMessageDO assistantMessage, String content) {
        if (assistantMessage == null || assistantMessage.getId() == null || content == null) {
            return;
        }
        assistantMessage.setContent(content);
        chatMessageService.updateById(assistantMessage);
    }
}
