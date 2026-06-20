package tech.qiantong.qknow.module.kb.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * bot流程关系 创建/修改 Request VO kb_flow_edge
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot流程关系 Response VO")
@Data
public class KbFlowEdgeSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    @NotNull(message = "botId不能为空")
    private Long botId;

    @Schema(description = "开始节点唯一标识", example = "")
    @NotBlank(message = "开始节点唯一标识不能为空")
    private String sourceNodeUuid;

    @Schema(description = "结束节点唯一标识", example = "")
    @NotBlank(message = "结束节点唯一标识不能为空")
    private String targetNodeUuid;

    @Schema(description = "开始节点锚点", example = "")
    @NotBlank(message = "开始节点锚点不能为空")
    @Size(max = 128, message = "开始节点锚点长度不能超过128个字符")
    private String sourceHandle;

    @Schema(description = "连线样式", example = "")
    @NotBlank(message = "连线样式不能为空")
    private String style;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
