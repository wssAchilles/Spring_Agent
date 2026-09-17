package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 应用管理 创建/修改 Request VO kac_apply
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "应用管理 Save VO")
@Data
public class KacApplySaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "名称", example = "")
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128个字符")
    private String name;

    @Schema(description = "描述", example = "")
    private String description;

    @Schema(description = "图标", example = "")
    @Size(max = 256, message = "图标长度不能超过256个字符")
    private String icon;

    @Schema(description = "状态", example = "")
    private Integer status;

    @Schema(description = "类型", example = "")
    @Size(max = 64, message = "类型长度不能超过64个字符")
    private String type;

    @Schema(description = "标签", example = "")
    @Size(max = 256, message = "标签长度不能超过256个字符")
    private String tags;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "配置", example = "")
    private String config;

    @Schema(description = "当前发布的版本ID")
    private Long publishedVersionId;

    @Schema(description = "执行模式: DIRECT_PROMPT_RAG, HERMES_AGENT, HERMES_DAG")
    private String executionMode;

    @Schema(description = "动态入参表单Schema定义 (JSON字符串)")
    private String inputSchema;

    @Schema(description = "出参Schema定义 (JSON字符串)")
    private String outputSchema;

    @Schema(description = "提示词模板")
    private String promptTemplate;

    @Schema(description = "执行策略配置 (JSON字符串)")
    private String executionConfig;

    @Schema(description = "应用大类 (0: 横向通用应用, 1: 纵向行业应用)")
    private Integer category;

    @Schema(description = "所属垂直行业分类")
    private String industry;

    @Schema(description = "行业关键业务KPI指标 (JSON字符串)")
    private String kpiMetrics;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;
}
