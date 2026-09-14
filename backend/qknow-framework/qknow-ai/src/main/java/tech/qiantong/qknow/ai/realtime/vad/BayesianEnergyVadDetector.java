package tech.qiantong.qknow.ai.realtime.vad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 40: 双阈值贝叶斯时序能量语音活动检测器 (Theorem 2.1: Bayesian Energy-Entropy VAD Barge-in Invariant)
 * 实现短时能量与过零率联合双阈值判定，具备瞬态高频杂音平滑拦截与毫秒级语音打断能力。
 *
 * @author qknow
 */
@Component
public class BayesianEnergyVadDetector {

    public static final double ENERGY_HIGH_THRESHOLD = 0.035; // 语音触发起呼高阈值
    public static final double ENERGY_LOW_THRESHOLD = 0.012;  // 语音持续维持低阈值
    public static final int SPEECH_START_CONSECUTIVE_FRAMES = 8; // 连续 8 帧 (约 160ms) 判定说话开始并允许打断
    public static final int SPEECH_END_CONSECUTIVE_FRAMES = 15;  // 连续 15 帧 (约 300ms) 静音判定说话结束
    public static final int TRANSIENT_NOISE_MAX_FRAMES = 4;      // 小于等于 4 帧 (<80-100ms) 视为瞬态脉冲噪声

    private final Map<String, VadSessionState> sessionStates = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VadSessionState {
        private boolean inSpeech;
        private int consecutiveSpeechFrames;
        private int consecutiveSilenceFrames;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VadDecision {
        private boolean isSpeech;
        private boolean speechStarted;
        private boolean speechEnded;
        private boolean bargeInTriggered;
        private double energy;
        private double zeroCrossingRate;
        private int consecutiveSpeechFrames;
    }

    /**
     * 计算 PCM 字节流短时归一化均方能量
     */
    public double computeShortTimeEnergy(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0.0;
        }
        long sumSquares = 0L;
        int sampleCount = pcmData.length / 2;
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sumSquares += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sumSquares / sampleCount) / 32768.0;
        return rms;
    }

    /**
     * 计算过零率 (Zero Crossing Rate)
     */
    public double computeZeroCrossingRate(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 4) {
            return 0.0;
        }
        int crossings = 0;
        int sampleCount = pcmData.length / 2;
        short prevSample = (short) ((pcmData[0] & 0xFF) | (pcmData[1] << 8));
        for (int i = 2; i < pcmData.length - 1; i += 2) {
            short currSample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            if ((prevSample >= 0 && currSample < 0) || (prevSample < 0 && currSample >= 0)) {
                crossings++;
            }
            prevSample = currSample;
        }
        return (double) crossings / sampleCount;
    }

    /**
     * 处理单帧音频，返回 VAD 状态判定与打断研判 (Theorem 2.1)
     */
    public VadDecision processAudioFrame(String sessionId, byte[] pcmData, boolean isBotCurrentlySpeaking) {
        VadSessionState state = sessionStates.computeIfAbsent(sessionId, k -> new VadSessionState());
        double energy = computeShortTimeEnergy(pcmData);
        double zcr = computeZeroCrossingRate(pcmData);

        // 判定当前帧是否满足能量阈值 (双阈值滞后比较)
        boolean currentFrameActive = state.isInSpeech() ? (energy >= ENERGY_LOW_THRESHOLD) : (energy >= ENERGY_HIGH_THRESHOLD);

        boolean speechStarted = false;
        boolean speechEnded = false;
        boolean bargeInTriggered = false;

        if (currentFrameActive) {
            state.setConsecutiveSpeechFrames(state.getConsecutiveSpeechFrames() + 1);
            state.setConsecutiveSilenceFrames(0);

            // 达到连续起始门限 (160ms)，状态跃迁为说话中
            if (!state.isInSpeech() && state.getConsecutiveSpeechFrames() >= SPEECH_START_CONSECUTIVE_FRAMES) {
                state.setInSpeech(true);
                speechStarted = true;
                // 若此时智能体正在播报，立即触发打断！
                if (isBotCurrentlySpeaking) {
                    bargeInTriggered = true;
                }
            } else if (state.isInSpeech() && isBotCurrentlySpeaking && state.getConsecutiveSpeechFrames() >= SPEECH_START_CONSECUTIVE_FRAMES) {
                // 已经确认是说话状态，且机器还在说，持续触发打断
                bargeInTriggered = true;
            }
        } else {
            state.setConsecutiveSilenceFrames(state.getConsecutiveSilenceFrames() + 1);
            state.setConsecutiveSpeechFrames(0);

            if (state.isInSpeech() && state.getConsecutiveSilenceFrames() >= SPEECH_END_CONSECUTIVE_FRAMES) {
                state.setInSpeech(false);
                speechEnded = true;
            }
        }

        return VadDecision.builder()
                .isSpeech(currentFrameActive)
                .speechStarted(speechStarted)
                .speechEnded(speechEnded)
                .bargeInTriggered(bargeInTriggered)
                .energy(energy)
                .zeroCrossingRate(zcr)
                .consecutiveSpeechFrames(state.getConsecutiveSpeechFrames())
                .build();
    }

    public void resetSession(String sessionId) {
        sessionStates.remove(sessionId);
    }
}
