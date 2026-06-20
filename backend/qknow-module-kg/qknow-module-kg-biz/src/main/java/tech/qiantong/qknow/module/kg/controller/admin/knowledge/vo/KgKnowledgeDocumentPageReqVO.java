package tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

import java.util.List;

/**
 * 知识文件 Request VO 对象 kg_knowledge_document
 *
 * @author qknow
 * @date 2025-10-20
 */
@Schema(description = "知识文件 Request VO")
@Data
public class KgKnowledgeDocumentPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "知识分类id", example = "")
    private Long categoryId;

    @Schema(description = "知识分类名称", example = "")
    private String categoryName;

    @Schema(description = "文件名称", example = "")
    private String name;

    @Schema(description = "文件路径", example = "")
    private String path;

    @Schema(description = "文件描述", example = "")
    private String description;

    @TableField(exist = false)
    private List<Long> ids ;


}
