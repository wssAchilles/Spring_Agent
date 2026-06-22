package tech.qiantong.qknow.module.app.dal.dataobject.kac;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 解决方案应用关联 DO 对象 kac_solution_apply
 *
 * @author qknow
 * @date 2026-06-22
 */
@Data
@TableName(value = "kac_solution_apply")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KacSolutionApplyDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 解决方案id */
    private Long solutionId;

    /** 应用id */
    private Long applyId;

    /** 工作区id */
    private Long workspaceId;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;
}
