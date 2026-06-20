package tech.qiantong.qknow.module.kg.dal.dataobject.knowledge;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 文件操作日志 DO 对象 kg_knowledge_document_log
 *
 * @author qknow
 * @date 2025-10-22
 */
@Data
@TableName(value = "kg_knowledge_document_log")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kg_knowledge_document_log_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KgKnowledgeDocumentLogDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 用户id */
    private Long userId;

    /** 用户名 */
    private String userName;

    /** 文件id */
    private Long documentId;

    /** 文件名 */
    private String documentName;

    /** 操作类型 */
    private Integer type;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
