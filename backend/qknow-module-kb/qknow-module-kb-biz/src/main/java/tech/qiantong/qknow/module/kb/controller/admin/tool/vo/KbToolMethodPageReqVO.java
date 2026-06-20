package tech.qiantong.qknow.module.kb.controller.admin.tool.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 工具方法 Request VO 对象 kb_tool_method
 *
 * @author qknow
 * @date 2026-03-19
 */
@Schema(description = "工具方法 Request VO")
@Data
public class KbToolMethodPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "工具id", example = "")
    private Long toolId;

    @Schema(description = "唯一标识", example = "")
    private String code;

    @Schema(description = "名称", example = "")
    private String name;

    @Schema(description = "描述", example = "")
    private String description;




}
