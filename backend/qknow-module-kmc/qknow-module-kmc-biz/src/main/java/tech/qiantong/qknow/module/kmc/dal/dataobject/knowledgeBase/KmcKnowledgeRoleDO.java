package tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 知识库角色关联 DO 对象 kmc_knowledge_role
 *
 * @author qknow
 * @date 2025-07-24
 */
@Data
@TableName(value = "kmc_knowledge_role")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kmc_knowledge_role_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KmcKnowledgeRoleDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 知识库id */
    private Long knowledgeId;

    /** 角色id */
    private Long roleId;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
