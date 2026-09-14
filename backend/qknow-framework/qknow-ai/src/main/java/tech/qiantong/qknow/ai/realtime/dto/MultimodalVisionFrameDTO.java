package tech.qiantong.qknow.ai.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视觉关键帧传输对象
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalVisionFrameDTO {
    private String frameId;
    private String sessionId;
    private long timestamp;
    private String imageBase64;
    private long pHash;
    private int hammingDistanceToLast;
    private boolean skipped;
    private String skipReason;
}
