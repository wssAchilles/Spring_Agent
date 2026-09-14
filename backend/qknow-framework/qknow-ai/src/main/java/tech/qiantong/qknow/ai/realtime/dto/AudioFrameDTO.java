package tech.qiantong.qknow.ai.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实时 PCM 音频帧传输对象
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioFrameDTO {
    private String frameId;
    private String sessionId;
    private long epoch;
    @Builder.Default
    private int sampleRate = 16000;
    @Builder.Default
    private int channels = 1;
    private byte[] pcmData;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    private double energy;
    private double zeroCrossingRate;
    private boolean isSpeech;
}
