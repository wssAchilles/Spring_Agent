package tech.qiantong.qknow.module.kb.controller.admin.conversation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "对话创建 Request VO")
@Data
public class KbConversationSaveReqVO {

    @Schema(description = "Bot ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long botId;

    @Schema(description = "工作区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workspaceId;

    @Schema(description = "对话标题")
    private String title;
}
