package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 应用执行同步结果响应 VO
 *
 * @author qknow
 */
@Schema(description = "应用执行同步结果响应 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppRunResultRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用ID")
    private Long applyId;

    @Schema(description = "执行凭单 ID")
    private String receiptId;

    @Schema(description = "链路 Trace ID")
    private String traceId;

    @Schema(description = "深度思考链思维过程 (DeepSeek R1)")
    private String reasoningContent;

    @Schema(description = "生成文本正文或输出结果")
    private String outputContent;

    @Schema(description = "命中的知识库切片来源详情")
    private List<Map<String, Object>> ragSources;

    @Schema(description = "输入 Token 数")
    private Integer promptTokens;

    @Schema(description = "输出 Token 数")
    private Integer completionTokens;

    @Schema(description = "总 Token 数")
    private Integer totalTokens;

    @Schema(description = "执行总时延 (ms)")
    private Long latencyMs;

    @Schema(description = "状态: SUCCESS, FAILED")
    private String status;

    @Schema(description = "异常提示 (如有)")
    private String errorMessage;
}
