package tech.qiantong.qknow.module.kb.controller.admin.conversation;

import com.alibaba.cloud.ai.graph.GraphRunnerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
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
        // 保存用户消息
        chatMessageService.saveMessage(reqVO.getConversationId(), "user", reqVO.getQuestion());

        // 获取 Agent 配置
        KbAgentConfigDO config = agentConfigService.getKbAgentConfigByBotId(reqVO.getBotId());
        if (config == null) {
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

        // 获取历史消息并注入
        List<KbChatMessageDO> history = chatMessageService.getMessagesByConversationId(reqVO.getConversationId());
        agentReq.setHistoryMessages(history);

        // 调用 Agent (流式)
        try {
            return agentConfigService.chatMessage(agentReq)
                    .doOnComplete(() -> {
                        // 流完成后保存助手消息 (从最后一个 receive 消息获取内容)
                        // 注意: 流式输出是增量的，完整内容需要前端拼接后保存
                    });
        } catch (GraphRunnerException e) {
            throw new RuntimeException("Agent 调用失败: " + e.getMessage());
        }
    }
}
