package tech.qiantong.qknow.module.ext.dal.dataobject.extTaskLog;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 抽取任务执行日志详情 DO 对象 ext_task_log_detail
 *
 * @author qknow
 * @date 2025-12-03
 */
@Data
@TableName(value = "ext_task_log_detail")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExtTaskLogDetailDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 执行日志id */
    private Long logId;

    /** 任务执行步骤 */
    private String step;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
