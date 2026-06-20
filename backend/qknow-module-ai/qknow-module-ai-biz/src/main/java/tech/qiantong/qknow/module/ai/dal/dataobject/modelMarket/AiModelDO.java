package tech.qiantong.qknow.module.ai.dal.dataobject.modelMarket;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * AI 模型 DO 对象 ai_model
 *
 * @author qknow
 * @date 2025-12-23
 */
@Data
@TableName(value = "ai_model")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("ai_model_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiModelDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 秘钥id */
    private Long keyId;

    /** 模型名称 */
    private String name;

    /** 模型标志 */
    private String model;

    /** 平台 */
    private String platform;

    /** 类型 */
    private Integer type;

    /** 排序值 */
    private Long sort;

    /** 状态 */
    private Integer status;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
