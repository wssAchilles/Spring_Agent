package tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 文件操作日志 创建/修改 Request VO kg_knowledge_document_log
 *
 * @author qknow
 * @date 2025-10-22
 */
@Schema(description = "文件操作日志 Response VO")
@Data
public class KgKnowledgeDocumentLogSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    @Schema(description = "用户id", example = "")
    private Long userId;

    @Schema(description = "用户名", example = "")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 256, message = "用户名长度不能超过256个字符")
    private String userName;

    @Schema(description = "文件id", example = "")
    private Long documentId;

    @Schema(description = "文件名", example = "")
    @NotBlank(message = "文件名不能为空")
    @Size(max = 256, message = "文件名长度不能超过256个字符")
    private String documentName;

    @Schema(description = "操作类型", example = "")
    @NotBlank(message = "操作类型不能为空")
    private Integer type;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
