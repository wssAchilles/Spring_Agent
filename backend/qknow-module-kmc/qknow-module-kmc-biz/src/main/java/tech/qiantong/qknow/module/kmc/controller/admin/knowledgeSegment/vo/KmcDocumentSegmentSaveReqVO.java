package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.annotation.Excel;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文件分段 创建/修改 Request VO kmc_document_segment
 *
 * @author qknow
 * @date 2025-08-28
 */
@Schema(description = "文件分段 Response VO")
@Data
public class KmcDocumentSegmentSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "分段内容文本", example = "")
    @NotBlank(message = "分段内容文本不能为空")
    @Size(message = "分段内容文本长度不能超过256个字符")
    private String content;

    @Schema(description = "答案内容(如果有)", example = "")
    private String answer;

    @Schema(description = "关键词", example = "")
    private String keywords;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;

    private Long documentId;

    private String parentId;

    @Excel(name = "工作空间id")
    @Schema(description = "工作空间id", example = "")
    private Long workspaceId;

    @Excel(name = "dify段落id")
    @Schema(description = "dify段落id", example = "")
    private String qmSegmentId;

}
