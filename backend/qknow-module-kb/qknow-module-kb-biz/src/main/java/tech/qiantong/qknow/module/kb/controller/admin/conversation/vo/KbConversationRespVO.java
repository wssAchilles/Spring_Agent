package tech.qiantong.qknow.module.kb.controller.admin.conversation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Schema(description = "对话 Response VO")
@Data
public class KbConversationRespVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Bot ID")
    private Long botId;

    @Schema(description = "工作区ID")
    private Long workspaceId;

    @Schema(description = "对话标题")
    private String title;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
