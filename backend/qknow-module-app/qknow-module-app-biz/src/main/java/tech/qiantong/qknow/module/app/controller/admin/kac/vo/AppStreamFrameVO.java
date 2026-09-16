package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 应用流式事件帧 VO (SSE Payload)
 *
 * @author qknow
 */
@Schema(description = "应用流式事件帧 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppStreamFrameVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 帧类型: init, thinking, rag_recall, chunk, complete, error
     */
    @Schema(description = "帧类型: init, thinking, rag_recall, chunk, complete, error")
    private String frameType;

    @Schema(description = "帧文本内容或Markdown增量")
    private String content;

    @Schema(description = "调用链路 Trace ID")
    private String traceId;

    @Schema(description = "执行凭单 ID")
    private String receiptId;

    @Schema(description = "时间戳")
    private Long timestamp;

    @Schema(description = "指标或附加数据 (Token消耗、时延等)")
    private Map<String, Object> extra;
}
