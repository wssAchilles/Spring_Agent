package tech.qiantong.qknow.module.kb.controller.admin.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Schema(description = "管理后台 - AI 聊天消息发送 Response VO")
@Data
public class KbChatMessageSendRespVO {

    @Schema(description = "发送消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Message send;

    @Schema(description = "接收消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Message receive;

    @Schema(description = "消息")
    @Data
    public static class Message {

        @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        private Long id;

        @Schema(description = "消息类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "role")
        private Integer type; // 参见 MessageType 枚举类

        @Schema(description = "事件类型", example = "text")
        private String eventType; // text, tool_call, memory_recall

        @Schema(description = "工具名称")
        private String toolName;

        @Schema(description = "工具状态")
        private String toolStatus;

        @Schema(description = "聊天内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，你好啊")
        private String content;

        @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
        private Date createTime;

        @Schema(description = "引用的文章id", example = "")
        private List<String> documentIdList;

        @Schema(description = "引用的文章名称", example = "")
        private List<String> documentNameList;
    }

}
