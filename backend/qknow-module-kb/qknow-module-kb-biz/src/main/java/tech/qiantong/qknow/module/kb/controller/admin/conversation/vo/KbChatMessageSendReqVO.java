package tech.qiantong.qknow.module.kb.controller.admin.conversation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "发送消息 Request VO")
@Data
public class KbChatMessageSendReqVO {

    @Schema(description = "对话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conversationId;

    @Schema(description = "Bot ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long botId;

    @Schema(description = "工作区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workspaceId;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "入参")
    private String input;
}
