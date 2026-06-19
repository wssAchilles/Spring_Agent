package tech.qiantong.qknow.module.kb.controller.admin.conversation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Schema(description = "聊天消息 Response VO")
@Data
public class KbChatMessageRespVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "对话ID")
    private Long conversationId;

    @Schema(description = "角色 (user/assistant)")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "Token 数量")
    private Integer tokenCount;

    @Schema(description = "创建时间")
    private Date createTime;
}
