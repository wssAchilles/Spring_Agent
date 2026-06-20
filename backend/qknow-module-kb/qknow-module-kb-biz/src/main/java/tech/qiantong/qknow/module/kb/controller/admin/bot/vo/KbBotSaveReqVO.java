package tech.qiantong.qknow.module.kb.controller.admin.bot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * bot 管理 创建/修改 Request VO kb_bot
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot 管理 Response VO")
@Data
public class KbBotSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "名称", example = "")
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "类型", example = "")
    @NotNull(message = "类型不能为空")
    private Integer type;

    @Schema(description = "描述", example = "")
    @Size(max = 256, message = "描述长度不能超过256个字符")
    private String description;

    @Schema(description = "是否内置", example = "")
    private Integer builtinFlag;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
