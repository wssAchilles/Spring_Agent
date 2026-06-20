package tech.qiantong.qknow.module.kmc.api.sync.dto;

import lombok.Data;

/**
 * 文件同步 DTO 对象 kmc_sync
 *
 * @author qknow
 * @date 2025-03-18
 */
@Data
public class KmcSyncRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 文件id */
    private Long documentId;

    /** 灵桐知识库id */
    private String qmDatasetId;

    /** 灵桐的文件id */
    private String qmDocumentId;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
