package tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 抽取任务执行日志详情 Request VO 对象 ext_task_log_detail
 *
 * @author qknow
 * @date 2025-12-03
 */
@Schema(description = "抽取任务执行日志详情 Request VO")
@Data
public class ExtTaskLogDetailPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "执行日志id", example = "")
    private Long logId;

    @Schema(description = "任务执行步骤", example = "")
    private String step;




}
