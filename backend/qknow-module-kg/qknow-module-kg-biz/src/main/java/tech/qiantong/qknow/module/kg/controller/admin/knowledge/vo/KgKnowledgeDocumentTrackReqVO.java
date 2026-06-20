package tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识文件跟踪对象
 *
 * @author qknow
 * @date 2025-02-14
 */
@Schema(description = "知识文件跟踪VO")
@Data
public class KgKnowledgeDocumentTrackReqVO {

    @Schema(description = "知识文件id")
    private Long documentId;

    @Schema(description = "跟踪类型")
    private String type;
}
