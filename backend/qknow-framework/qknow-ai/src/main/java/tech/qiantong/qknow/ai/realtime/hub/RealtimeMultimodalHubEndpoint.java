package tech.qiantong.qknow.ai.realtime.hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.realtime.dto.AudioFrameDTO;
import tech.qiantong.qknow.ai.realtime.dto.MultimodalVisionFrameDTO;
import tech.qiantong.qknow.ai.realtime.dto.RealtimeEventVO;
import tech.qiantong.qknow.ai.realtime.dto.RealtimeSessionDO;
import tech.qiantong.qknow.ai.realtime.pipeline.RealtimeStreamingPipeline;
import tech.qiantong.qknow.ai.realtime.vad.BayesianEnergyVadDetector;
import tech.qiantong.qknow.ai.realtime.vision.VisionTokenGovernor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 40: 全双工多模态 WebSocket 终端与会话调度中枢
 * 管理实时音频流、视觉关键帧、VAD 语音活动与代际号原子路由。
 *
 * @author qknow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMultimodalHubEndpoint {

    private final BayesianEnergyVadDetector vadDetector;
    private final VisionTokenGovernor visionGovernor;
    private final RealtimeStreamingPipeline streamingPipeline;

    private final Map<String, RealtimeSessionDO> activeSessions = new ConcurrentHashMap<>();

    /**
     * 创建实时会话
     */
    public RealtimeSessionDO createSession(String userId) {
        String sessionId = "SESS_RT_" + UUID.randomUUID().toString().substring(0, 8);
        RealtimeSessionDO session = RealtimeSessionDO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionState(RealtimeSessionDO.SessionState.IDLE)
                .build();
        activeSessions.put(sessionId, session);
        return session;
    }

    public RealtimeSessionDO getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * 处理接入的音频数据帧
     */
    public RealtimeEventVO handleIncomingAudio(String sessionId, byte[] pcmData, long frameEpoch) {
        RealtimeSessionDO session = activeSessions.get(sessionId);
        if (session == null) {
            return RealtimeEventVO.builder().eventType(RealtimeEventVO.EventType.ERROR).sessionId(sessionId).build();
        }

        // 丢弃旧代际帧
        if (streamingPipeline.isFrameStale(frameEpoch, session.getEpochValue())) {
            log.debug("丢弃过时代际音频帧: frameEpoch={}, currentEpoch={}", frameEpoch, session.getEpochValue());
            return null;
        }

        session.setAudioFramesReceived(session.getAudioFramesReceived() + 1);
        boolean botSpeaking = (session.getSessionState() == RealtimeSessionDO.SessionState.BOT_SPEAKING);

        // VAD 检测
        BayesianEnergyVadDetector.VadDecision vad = vadDetector.processAudioFrame(sessionId, pcmData, botSpeaking);

        if (vad.isBargeInTriggered()) {
            // 触发打断，原子递增代际号！
            streamingPipeline.handleBargeIn(session);
            return RealtimeEventVO.builder()
                    .eventId("EVT_" + UUID.randomUUID().toString().substring(0, 8))
                    .eventType(RealtimeEventVO.EventType.RESPONSE_INTERRUPTED)
                    .sessionId(sessionId)
                    .epoch(session.getEpochValue())
                    .payload(Map.of("reason", "USER_BARGE_IN", "energy", vad.getEnergy()))
                    .build();
        } else if (vad.isSpeechStarted()) {
            session.setSessionState(RealtimeSessionDO.SessionState.USER_SPEAKING);
            return RealtimeEventVO.builder()
                    .eventId("EVT_" + UUID.randomUUID().toString().substring(0, 8))
                    .eventType(RealtimeEventVO.EventType.SPEECH_STARTED)
                    .sessionId(sessionId)
                    .epoch(session.getEpochValue())
                    .build();
        } else if (vad.isSpeechEnded()) {
            session.setSessionState(RealtimeSessionDO.SessionState.BOT_THINKING);
            return RealtimeEventVO.builder()
                    .eventId("EVT_" + UUID.randomUUID().toString().substring(0, 8))
                    .eventType(RealtimeEventVO.EventType.SPEECH_STOPPED)
                    .sessionId(sessionId)
                    .epoch(session.getEpochValue())
                    .build();
        }

        return null;
    }

    /**
     * 处理接入的视觉关键帧
     */
    public MultimodalVisionFrameDTO handleIncomingVision(String sessionId, String imageBase64, long timestamp) {
        RealtimeSessionDO session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }
        session.setVisionFramesReceived(session.getVisionFramesReceived() + 1);
        MultimodalVisionFrameDTO evaluated = visionGovernor.evaluateFrame(sessionId, imageBase64, timestamp);
        if (!evaluated.isSkipped()) {
            session.setVisionFramesSent(session.getVisionFramesSent() + 1);
        }
        return evaluated;
    }

    /**
     * 关闭会话并清理内存
     */
    public void closeSession(String sessionId) {
        RealtimeSessionDO session = activeSessions.remove(sessionId);
        if (session != null) {
            session.setSessionState(RealtimeSessionDO.SessionState.CLOSED);
            vadDetector.resetSession(sessionId);
            visionGovernor.clear(sessionId);
        }
    }
}
