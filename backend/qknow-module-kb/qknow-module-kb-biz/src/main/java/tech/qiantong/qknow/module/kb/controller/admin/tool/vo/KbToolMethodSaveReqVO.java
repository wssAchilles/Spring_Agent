package tech.qiantong.qknow.module.kb.controller.admin.tool.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 工具方法 创建/修改 Request VO kb_tool_method
 *
 * @author qknow
 * @date 2026-03-19
 */
@Schema(description = "工具方法 Response VO")
@Data
public class KbToolMethodSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "工具id", example = "")
    @NotNull(message = "未绑定工具")
    private Long toolId;

    @Schema(description = "唯一标识", example = "")
    @NotBlank(message = "唯一标识不能为空")
    @Size(max = 32, message = "唯一标识长度不能超过32个字符")
    private String code;

    @Schema(description = "名称", example = "")
    @NotBlank(message = "名称不能为空")
    @Size(max = 32, message = "名称长度不能超过32个字符")
    private String name;

    @Schema(description = "描述", example = "")
    //@NotBlank(message = "描述不能为空")
    @Size(max = 128, message = "描述长度不能超过128个字符")
    private String description;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
