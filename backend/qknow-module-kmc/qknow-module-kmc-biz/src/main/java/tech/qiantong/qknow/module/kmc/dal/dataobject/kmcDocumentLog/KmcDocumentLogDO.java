package tech.qiantong.qknow.module.kmc.dal.dataobject.kmcDocumentLog;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 文件操作日志 DO 对象 kmc_document_log
 *
 * @author qknow
 * @date 2025-03-24
 */
@Data
@TableName(value = "kmc_document_log")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kmc_document_log_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KmcDocumentLogDO extends BaseEntity {
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

    /** 操作类型（0：预览，1：下载） */
    private Integer type;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
