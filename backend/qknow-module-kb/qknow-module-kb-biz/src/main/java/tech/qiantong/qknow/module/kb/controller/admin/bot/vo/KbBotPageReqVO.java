package tech.qiantong.qknow.module.kb.controller.admin.bot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * bot 管理 Request VO 对象 kb_bot
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot 管理 Request VO")
@Data
public class KbBotPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "名称", example = "")
    private String name;

    @Schema(description = "类型", example = "")
    private Integer type;

    @Schema(description = "描述", example = "")
    private String description;

    @Schema(description = "是否内置", example = "")
    private Integer builtinFlag;


}
