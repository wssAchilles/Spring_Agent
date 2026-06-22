package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 应用机器人关联 创建/修改 Request VO kac_apply_bot
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "应用机器人关联 Save VO")
@Data
public class KacApplyBotSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "应用id", example = "")
    private Long applyId;

    @Schema(description = "机器人id", example = "")
    private Long botId;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;
}
