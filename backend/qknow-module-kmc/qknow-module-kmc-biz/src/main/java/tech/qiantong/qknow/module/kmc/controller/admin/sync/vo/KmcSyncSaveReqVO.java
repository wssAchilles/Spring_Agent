package tech.qiantong.qknow.module.kmc.controller.admin.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 文件同步 创建/修改 Request VO kmc_sync
 *
 * @author qknow
 * @date 2025-03-18
 */
@Schema(description = "文件同步 Response VO")
@Data
public class KmcSyncSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "文件id", example = "")
    @NotNull(message = "文件id不能为空")
    private Long documentId;

    @Schema(description = "灵桐知识库id", example = "")
    @NotBlank(message = "灵桐知识库id不能为空")
    private String qmDatasetId;

    @Schema(description = "灵桐的文件id", example = "")
    @NotBlank(message = "灵桐的文件id不能为空")
    private String qmDocumentId;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
