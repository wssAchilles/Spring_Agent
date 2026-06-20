package tech.qiantong.qknow.module.kb.controller.admin.bot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * bot访问密钥 创建/修改 Request VO kb_bot_apikey
 *
 * @author qknow
 * @date 2026-04-24
 */
@Schema(description = "bot访问密钥 Response VO")
@Data
public class KbBotApikeySaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    @NotNull(message = "botId不能为空")
    private Long botId;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;
}
