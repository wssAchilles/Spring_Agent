package tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 召回记录 DO 对象 kmc_knowledge_recall_log
 *
 * @author qknow
 * @date 2025-07-24
 */
@Data
@TableName(value = "kmc_knowledge_recall_log")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kmc_knowledge_recall_log_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KmcKnowledgeRecallLogDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 知识库id */
    @TableField("knowledge_base_id")
    private Long knowledgeId;

    /** 问题 */
    private String query;

    /** 是否有效 */
    private Integer validFlag;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;


}
