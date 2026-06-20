package tech.qiantong.qknow.module.ai.controller.admin.modelMarket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * API秘钥 创建/修改 Request VO ai_api_key
 *
 * @author qknow
 * @date 2025-12-23
 */
@Schema(description = "API秘钥 Response VO")
@Data
public class AiApiKeySaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "名称", example = "")
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128个字符")
    private String name;

    @Schema(description = "秘钥", example = "")
    @Size(max = 64, message = "秘钥长度不能超过32个字符")
    private String apiKey;

    @Schema(description = "平台", example = "")
    @NotBlank(message = "平台不能为空")
    @Size(max = 32, message = "平台长度不能超过32个字符")
    private String platform;

    @Schema(description = "API地址", example = "")
    @Size(max = 256, message = "API地址长度不能超过256个字符")
    private String url;

    @Schema(description = "平台标签", example = "")
    @Size(max = 256, message = "平台标签不能超过256个字符")
    private String platformTag ;

    @Schema(description = "描述", example = "")
    @Size(max = 1024, message = "描述不能超过1024个字符")
    private String description ;

    @Schema(description = "部署方式", example = "")
    @Size(max = 32, message = "部署方式不能超过32个字符")
    private String deployType ;

    @Schema(description = "状态", example = "")
    private Integer status;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
