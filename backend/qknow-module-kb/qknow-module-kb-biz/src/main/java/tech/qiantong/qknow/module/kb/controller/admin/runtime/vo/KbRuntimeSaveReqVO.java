package tech.qiantong.qknow.module.kb.controller.admin.runtime.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * bot运行 创建/修改 Request VO kb_runtime
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot运行 Response VO")
@Data
public class KbRuntimeSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    @NotNull(message = "botId不能为空")
    private Long botId;

    @Schema(description = "输入问题", example = "")
    @NotBlank(message = "输入问题不能为空")
    private String input;

    @Schema(description = "输出结果", example = "")
    @NotBlank(message = "输出结果不能为空")
    private String output;

    @Schema(description = "运行状态", example = "")
    private Integer status;

    @Schema(description = "运行时间(毫秒)", example = "")
    private Long runtime;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
