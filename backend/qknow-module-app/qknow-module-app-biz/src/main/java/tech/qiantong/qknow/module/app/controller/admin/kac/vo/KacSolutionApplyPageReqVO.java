package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 解决方案应用关联 Request VO 对象 kac_solution_apply
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "解决方案应用关联 Request VO")
@Data
public class KacSolutionApplyPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "解决方案id", example = "")
    private Long solutionId;

    @Schema(description = "应用id", example = "")
    private Long applyId;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;
}
