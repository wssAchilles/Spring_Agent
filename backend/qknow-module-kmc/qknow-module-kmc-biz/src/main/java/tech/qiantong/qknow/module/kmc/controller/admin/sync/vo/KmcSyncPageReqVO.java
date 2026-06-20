package tech.qiantong.qknow.module.kmc.controller.admin.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 文件同步 Request VO 对象 kmc_sync
 *
 * @author qknow
 * @date 2025-03-18
 */
@Schema(description = "文件同步 Request VO")
@Data
public class KmcSyncPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "文件id", example = "")
    private Long documentId;

    @Schema(description = "灵桐知识库id", example = "")
    private String qmDatasetId;

    @Schema(description = "灵桐的文件id", example = "")
    private String qmDocumentId;




}
