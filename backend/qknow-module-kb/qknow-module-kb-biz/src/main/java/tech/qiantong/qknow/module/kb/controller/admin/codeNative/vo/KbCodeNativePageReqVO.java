package tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 白盒化开发 Request VO 对象 kb_code_native
 *
 * @author qknow
 * @date 2026-04-09
 */
@Schema(description = "白盒化开发 Request VO")
@Data
public class KbCodeNativePageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    private Long botId;

    @Schema(description = "类名", example = "")
    private String className;

    @Schema(description = "代码", example = "")
    private String code;




}
