package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 应用执行请求 VO
 *
 * @author qknow
 */
@Schema(description = "应用执行请求 VO")
@Data
public class AppRunReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "应用ID不能为空")
    private Long applyId;

    @Schema(description = "动态输入参数键值对")
    private Map<String, Object> inputs;

    @Schema(description = "是否启用流式打字机输出")
    private Boolean stream = false;

    @Schema(description = "模型超参微调覆盖配置 (可选)")
    private Map<String, Object> modelOverrides;
}
