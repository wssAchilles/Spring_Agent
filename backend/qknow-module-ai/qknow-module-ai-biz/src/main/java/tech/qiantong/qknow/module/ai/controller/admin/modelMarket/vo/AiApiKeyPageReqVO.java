package tech.qiantong.qknow.module.ai.controller.admin.modelMarket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * API秘钥 Request VO 对象 ai_api_key
 *
 * @author qknow
 * @date 2025-12-23
 */
@Schema(description = "API秘钥 Request VO")
@Data
public class AiApiKeyPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "名称", example = "")
    private String name;

    @Schema(description = "平台", example = "")
    private String platform;

    @Schema(description = "状态", example = "")
    private Integer status;

    @Schema(description = "平台标签", example = "")
    private String platformTag ;

    @Schema(description = "描述", example = "")
    private String description ;

    @Schema(description = "状态字符串", example = "")
    private String statusStr;
}
