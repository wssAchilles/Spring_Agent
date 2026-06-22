package tech.qiantong.qknow.module.app.dal.dataobject.kac;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 应用管理 DO 对象 kac_apply
 *
 * @author qknow
 * @date 2026-06-22
 */
@Data
@TableName(value = "kac_apply")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KacApplyDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 图标 */
    private String icon;

    /** 状态 */
    private Integer status;

    /** 类型 */
    private String type;

    /** 标签 */
    private String tags;

    /** 工作区id */
    private Long workspaceId;

    /** 配置 */
    private String config;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;
}
