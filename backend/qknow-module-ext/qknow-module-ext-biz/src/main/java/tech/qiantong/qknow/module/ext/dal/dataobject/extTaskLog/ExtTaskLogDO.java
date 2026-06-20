package tech.qiantong.qknow.module.ext.dal.dataobject.extTaskLog;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 抽取任务执行日志 DO 对象 ext_task_log
 *
 * @author qknow
 * @date 2025-12-03
 */
@Data
@TableName(value = "ext_task_log")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExtTaskLogDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 任务id */
    private Long taskId;

    /** 任务类型 */
    private Integer taskType;

    /** 任务名称 */
    private String taskName;

    /** 状态 */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 执行开始时间 */
    private Date startTime;

    /** 执行结束时间 */
    private Date endTime;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
