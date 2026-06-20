package tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 抽取任务执行日志详情 创建/修改 Request VO ext_task_log_detail
 *
 * @author qknow
 * @date 2025-12-03
 */
@Schema(description = "抽取任务执行日志详情 Response VO")
@Data
public class ExtTaskLogDetailSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    @Schema(description = "执行日志id", example = "")
    @NotNull(message = "执行日志id不能为空")
    private Long logId;

    @Schema(description = "任务执行步骤", example = "")
    @NotBlank(message = "任务执行步骤不能为空")
    private String step;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    private String remark;


}
