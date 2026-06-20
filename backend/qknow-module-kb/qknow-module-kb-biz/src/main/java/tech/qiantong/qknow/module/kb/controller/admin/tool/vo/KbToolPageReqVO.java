package tech.qiantong.qknow.module.kb.controller.admin.tool.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 工具管理 Request VO 对象 kb_tool
 *
 * @author qknow
 * @date 2026-03-19
 */
@Schema(description = "工具管理 Request VO")
@Data
public class KbToolPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "名称", example = "")
    private String name;

    @Schema(description = "描述", example = "")
    private String description;

    @Schema(description = "标签", example = "")
    private String tags;

    @Schema(description = "类型", example = "")
    private Integer type;

    @Schema(description = "来源", example = "")
    private String source;

    @Schema(description = "方法数", example = "")
    private Integer methodNum;
}
