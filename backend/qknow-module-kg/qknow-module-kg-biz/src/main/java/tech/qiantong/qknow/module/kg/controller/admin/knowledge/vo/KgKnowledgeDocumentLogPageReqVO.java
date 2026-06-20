package tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 文件操作日志 Request VO 对象 kg_knowledge_document_log
 *
 * @author qknow
 * @date 2025-10-22
 */
@Schema(description = "文件操作日志 Request VO")
@Data
public class KgKnowledgeDocumentLogPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "用户id", example = "")
    private Long userId;

    @Schema(description = "用户名", example = "")
    private String userName;

    @Schema(description = "文件id", example = "")
    private Long documentId;

    @Schema(description = "文件名", example = "")
    private String documentName;

    @Schema(description = "操作类型", example = "")
    private Integer type;




}
