package tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

import java.util.Date;

/**
 * 抽取任务执行日志 Request VO 对象 ext_task_log
 *
 * @author qknow
 * @date 2025-12-03
 */
@Schema(description = "抽取任务执行日志 Request VO")
@Data
public class ExtTaskLogPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "任务id", example = "")
    private Long taskId;

    @Schema(description = "任务类型", example = "")
    private String taskType;

    @Schema(description = "任务名称", example = "")
    private String taskName;

    @Schema(description = "状态", example = "")
    private String status;

    @Schema(description = "错误消息", example = "")
    private String errorMsg;

    @Schema(description = "执行开始时间", example = "")
    private Date startTime;

    @Schema(description = "执行结束时间", example = "")
    private Date endTime;




}
