package tech.qiantong.qknow.module.kmc.controller.admin.kmcCategory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识分类 创建/修改 Request VO kmc_category
 *
 * @author qknow
 * @date 2025-02-13
 */
@Schema(description = "知识分类 Response VO")
@Data
public class KmcCategorySaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
//    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    /** 知识库id */
    @Schema(description = "知识库id", example = "")
    private Long knowledgeBaseId;

    @Schema(description = "父级id", example = "")
//    @NotNull(message = "父级id不能为空")
    private Long parentId;

    @Schema(description = "分类名称", example = "")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 256, message = "分类名称长度不能超过256个字符")
    private String name;

    @Schema(description = "显示顺序", example = "")
    private Long orderNum;

    @Schema(description = "祖级列表", example = "")
    @Size(max = 256, message = "祖级列表长度不能超过256个字符")
    private String ancestors;


    @Schema(description = "备注", example = "")
    @Size(max = 256, message = "备注长度不能超过256个字符")
    private String remark;



}
