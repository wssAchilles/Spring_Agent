package tech.qiantong.qknow.module.kmc.dal.dataobject.sync;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 文件同步 DO 对象 kmc_sync
 *
 * @author qknow
 * @date 2025-03-18
 */
@Data
@TableName(value = "kmc_sync")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kmc_sync_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KmcSyncDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 文件id */
    private Long documentId;

    /** 灵桐知识库id */
    private String qmDatasetId;

    /** 灵桐的文件id */
    private String qmDocumentId;

    /** 灵桐的文件上传批次号 */
    private String qmBatch;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
