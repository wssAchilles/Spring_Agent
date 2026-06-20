package tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 白盒化开发 创建/修改 Request VO kb_code_native
 *
 * @author qknow
 * @date 2026-04-09
 */
@Schema(description = "白盒化开发 Response VO")
@Data
public class KbCodeNativeSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botId", example = "")
    @NotNull(message = "botId不能为空")
    private Long botId;

    @Schema(description = "类名", example = "")
    @NotBlank(message = "类名不能为空")
    private String className;

    @Schema(description = "代码", example = "")
    @NotBlank(message = "代码不能为空")
    private String code;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
