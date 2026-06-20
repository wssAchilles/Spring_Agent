package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 文件分段 Request VO 对象 kmc_document_segment
 *
 * @author qknow
 * @date 2025-08-28
 */
@Schema(description = "文件分段 Request VO")
@Data
public class KmcDocumentSegmentPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "分段内容文本", example = "")
    private String content;

    @Schema(description = "分段添加dify状态", example = "")
    private Integer syncStatus;

    private Long documentId;

    private Boolean parent;


}
