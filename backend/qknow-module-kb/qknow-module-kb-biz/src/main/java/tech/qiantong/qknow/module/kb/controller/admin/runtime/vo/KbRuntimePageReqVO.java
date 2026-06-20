package tech.qiantong.qknow.module.kb.controller.admin.runtime.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * bot运行 Request VO 对象 kb_runtime
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot运行 Request VO")
@Data
public class KbRuntimePageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    private Long botId;

    @Schema(description = "输入问题", example = "")
    private String input;

    @Schema(description = "输出结果", example = "")
    private String output;

    @Schema(description = "运行状态", example = "")
    private Integer status;

    @Schema(description = "运行时间(毫秒)", example = "")
    private Long runtime;


}
