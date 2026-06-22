package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 插件管理 Request VO 对象 kac_plugin
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "插件管理 Request VO")
@Data
public class KacPluginPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "名称", example = "")
    private String name;

    @Schema(description = "描述", example = "")
    private String description;

    @Schema(description = "类型", example = "")
    private String type;

    @Schema(description = "状态", example = "")
    private Integer status;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;
}
