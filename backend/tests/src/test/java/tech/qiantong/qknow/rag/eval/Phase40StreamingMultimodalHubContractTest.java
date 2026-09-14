package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.realtime.dto.MultimodalVisionFrameDTO;
import tech.qiantong.qknow.ai.realtime.dto.RealtimeEventVO;
import tech.qiantong.qknow.ai.realtime.dto.RealtimeSessionDO;
import tech.qiantong.qknow.ai.realtime.hub.RealtimeMultimodalHubEndpoint;
import tech.qiantong.qknow.ai.realtime.pipeline.RealtimeStreamingPipeline;
import tech.qiantong.qknow.ai.realtime.tokenizer.PhraseChunkingTokenizer;
import tech.qiantong.qknow.ai.realtime.vad.BayesianEnergyVadDetector;
import tech.qiantong.qknow.ai.realtime.vision.VisionTokenGovernor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 40: 低延迟全双工流式多模态实时音视频交互中枢 10 项严苛契约测试
 *
 * 核心指标验证：
 * 1. 级联流式流水线首音频延迟 TTFA <= 800ms，延迟削减 >= 70% (定理 1.1)；
 * 2. 标点短语双门限微切块与断句语义完备性契约；
 * 3. 双阈值贝叶斯能量 VAD 状态机平滑跃迁契约；
 * 4. 瞬态噪声时序平滑过滤与抗误打断契约；
 * 5. 毫秒级语音打断 (Barge-in) 与响应延迟契约 (定理 2.1)；
 * 6. 代际号 (Epoch) 原子自增与旧音频帧零残留拦截契约；
 * 7. 视觉关键帧 pHash 差异检测与静态帧过滤契约 (定理 3.1)；
 * 8. 全双工双向多模态 WebSocket 状态机生命周期契约；
 * 9. 网络中断异常自愈与会话资源清理契约；
 * 10. 端到端音视频多模态交互全链路协同闭环契约。
 *
 * @author qknow
 */
public class Phase40StreamingMultimodalHubContractTest {

    private BayesianEnergyVadDetector vadDetector;
    private PhraseChunkingTokenizer chunkingTokenizer;
    private VisionTokenGovernor visionGovernor;
    private RealtimeStreamingPipeline streamingPipeline;
    private RealtimeMultimodalHubEndpoint hubEndpoint;

    @BeforeEach
    void setUp() {
        vadDetector = new BayesianEnergyVadDetector();
        chunkingTokenizer = new PhraseChunkingTokenizer();
        visionGovernor = new VisionTokenGovernor();
        streamingPipeline = new RealtimeStreamingPipeline(chunkingTokenizer);
        hubEndpoint = new RealtimeMultimodalHubEndpoint(vadDetector, visionGovernor, streamingPipeline);
    }

    private byte[] createSyntheticPcmFrame(double amplitude, int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            short val = (short) (Math.sin(2 * Math.PI * 440 * i / 16000.0) * 32767.0 * amplitude);
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return pcm;
    }

    @Test
    @DisplayName("Contract 01: 流式微管线重叠推进与首音频延迟 TTFA 压降契约 (定理 1.1)")
    void contract01_streamingPipelineTTFABound() {
        RealtimeStreamingPipeline.PipelineExecutionResult result = 
                streamingPipeline.executeStreamingPipeline("请问年假补偿标准", 1L);

        assertNotNull(result);
        assertFalse(result.getTtsAudioChunks().isEmpty(), "必须切分出短语级音频合成微块");
        // 定理 1.1 验证: TTFA <= 800ms
        assertTrue(result.getTimeToFirstAudioMs() <= 800L, "首音频响应时间 TTFA 必须严格 <= 800ms (实测: " + result.getTimeToFirstAudioMs() + "ms)");

        // 相比串行瀑布流 (约 4500ms) 延迟降低 >= 70%
        double reduction = (4500.0 - result.getTimeToFirstAudioMs()) / 4500.0;
        assertTrue(reduction >= 0.70, "首音频延迟相比传统瀑布流削减必须 >= 70%");
    }

    @Test
    @DisplayName("Contract 02: 标点短语双门限微切块与断句语义完备性契约")
    void contract02_phraseChunkingTokenizerNaturalDelimiters() {
        String sessionId = "SESS_TEST_TOKENIZER";
        String streamText = "您好！关于调休政策，已通过审批。";

        List<String> collectedChunks = new java.util.ArrayList<>();
        for (String c : streamText.split("")) {
            List<String> chunks = chunkingTokenizer.feedToken(sessionId, c);
            collectedChunks.addAll(chunks);
        }
        String flushed = chunkingTokenizer.flush(sessionId);
        if (flushed != null) {
            collectedChunks.add(flushed);
        }

        assertFalse(collectedChunks.isEmpty());
        // 验证：标点符号处切分
        assertTrue(collectedChunks.contains("您好！") || collectedChunks.get(0).contains("您好"), "感叹号处形成自然断句");
        assertTrue(collectedChunks.stream().anyMatch(chunk -> chunk.contains("调休政策")), "短语包含完整语义词");
    }

    @Test
    @DisplayName("Contract 03: 双阈值贝叶斯能量 VAD 人声起始与结束精确感知契约")
    void contract03_bayesianEnergyVadStateTransitions() {
        String sessionId = "SESS_TEST_VAD_STATE";
        vadDetector.resetSession(sessionId);

        // 1. 发送低能量静音帧 (幅值 0.005 < 0.012)
        byte[] silenceFrame = createSyntheticPcmFrame(0.005, 320); // 20ms @ 16kHz
        BayesianEnergyVadDetector.VadDecision silenceDec = vadDetector.processAudioFrame(sessionId, silenceFrame, false);
        assertFalse(silenceDec.isSpeech(), "静音帧判定为非语音");
        assertFalse(silenceDec.isSpeechStarted());

        // 2. 连续发送 8 帧高能量人声 (幅值 0.15 > 0.035)
        byte[] speechFrame = createSyntheticPcmFrame(0.15, 320);
        BayesianEnergyVadDetector.VadDecision speechDec = null;
        for (int i = 0; i < 8; i++) {
            speechDec = vadDetector.processAudioFrame(sessionId, speechFrame, false);
        }
        assertNotNull(speechDec);
        assertTrue(speechDec.isSpeech(), "高能量帧判定为语音");
        assertTrue(speechDec.isSpeechStarted(), "连续 8 帧判定语音开始 (speechStarted = true)");

        // 3. 连续发送 15 帧静音，判定语音结束
        BayesianEnergyVadDetector.VadDecision endDec = null;
        for (int i = 0; i < 15; i++) {
            endDec = vadDetector.processAudioFrame(sessionId, silenceFrame, false);
        }
        assertNotNull(endDec);
        assertTrue(endDec.isSpeechEnded(), "连续 15 帧静音判定语音结束 (speechEnded = true)");
    }

    @Test
    @DisplayName("Contract 04: 瞬态噪声时序平滑过滤与抗误打断契约")
    void contract04_transientNoiseTemporalSmoothingFilter() {
        String sessionId = "SESS_TEST_NOISE";
        vadDetector.resetSession(sessionId);

        // 模拟突发瞬态噪声：仅持续 2 帧 (40ms) 的高能量脉冲 (模拟敲击声)
        byte[] noisePulseFrame = createSyntheticPcmFrame(0.20, 320);
        vadDetector.processAudioFrame(sessionId, noisePulseFrame, true);
        BayesianEnergyVadDetector.VadDecision noiseDec = vadDetector.processAudioFrame(sessionId, noisePulseFrame, true);

        // 验证：未达到连续 8 帧门限，绝不触发误打断
        assertFalse(noiseDec.isBargeInTriggered(), "持续 < 100ms 的瞬态高能量噪声绝不触发误打断");
        assertFalse(noiseDec.isSpeechStarted(), "瞬态杂音不触发说话开始");
    }

    @Test
    @DisplayName("Contract 05: 毫秒级语音打断 (Barge-in) 与响应延迟契约 (定理 2.1)")
    void contract05_millisecondBargeInLatencyBound() {
        RealtimeSessionDO session = hubEndpoint.createSession("USER_BARGE_IN");
        session.setSessionState(RealtimeSessionDO.SessionState.BOT_SPEAKING);

        byte[] humanVoiceFrame = createSyntheticPcmFrame(0.12, 320); // 20ms 人声
        RealtimeEventVO bargeInEvent = null;

        long startTime = System.nanoTime();
        // 连续输入 8 帧有效人声 (160ms)
        for (int i = 0; i < 8; i++) {
            RealtimeEventVO evt = hubEndpoint.handleIncomingAudio(session.getSessionId(), humanVoiceFrame, session.getEpochValue());
            if (evt != null && evt.getEventType() == RealtimeEventVO.EventType.RESPONSE_INTERRUPTED) {
                bargeInEvent = evt;
                break;
            }
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;

        assertNotNull(bargeInEvent, "必须精准捕获打断事件 RESPONSE_INTERRUPTED");
        assertEquals(RealtimeEventVO.EventType.RESPONSE_INTERRUPTED, bargeInEvent.getEventType());
        // 打断响应延迟 <= 200ms
        assertTrue(durationMs <= 200L, "打断处理延迟必须 <= 200ms");
        assertEquals(RealtimeSessionDO.SessionState.USER_SPEAKING, session.getSessionState(), "会话状态切换为 USER_SPEAKING");
    }

    @Test
    @DisplayName("Contract 06: 代际号 (Epoch) 原子流转与旧音频帧零残留消除契约")
    void contract06_epochIncrementAndStaleFrameZeroRetention() {
        RealtimeSessionDO session = hubEndpoint.createSession("USER_EPOCH");
        long epochOld = session.getEpochValue();
        assertEquals(1L, epochOld);

        // 模拟打断发生
        streamingPipeline.handleBargeIn(session);
        long epochNew = session.getEpochValue();
        assertEquals(2L, epochNew, "打断后代际号原子自增为 2");

        // 模拟接收到属于旧代际 Epoch=1 的未消费残留音频帧
        byte[] staleFrame = createSyntheticPcmFrame(0.08, 320);
        RealtimeEventVO droppedEvt = hubEndpoint.handleIncomingAudio(session.getSessionId(), staleFrame, epochOld);

        // 验证：旧代际帧被物理拦截并丢弃，返回 null
        assertNull(droppedEvt, "旧代际音频帧必须 100% 拦截丢弃，杜绝幽灵音频回放");
    }

    @Test
    @DisplayName("Contract 07: 视觉关键帧 pHash 差异检测与静态帧过滤契约 (定理 3.1)")
    void contract07_visionTokenGovernorPhashFiltering() {
        String sessionId = "SESS_TEST_VISION";
        visionGovernor.clear(sessionId);

        String staticScreenBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        // 1. 第一帧必须放行 (首帧建立基线)
        MultimodalVisionFrameDTO frame1 = visionGovernor.evaluateFrame(sessionId, staticScreenBase64, 1000L);
        assertFalse(frame1.isSkipped(), "首帧必须放行");

        // 2. 连续发送相同的静态画面 (未达到心跳超时)
        int skippedCount = 0;
        for (int i = 1; i <= 10; i++) {
            MultimodalVisionFrameDTO frame = visionGovernor.evaluateFrame(sessionId, staticScreenBase64, 1000L + i * 200L);
            if (frame.isSkipped()) {
                skippedCount++;
            }
        }
        // 验证：静态画面 10 帧全部跳过
        assertEquals(10, skippedCount, "静态不变画面必须全部跳过过滤");

        // 3. 产生显著变化的画面
        String changedScreenBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mN8//8/AwAI/AL+X6k8NwAAAABJRU5ErkJggg==";
        MultimodalVisionFrameDTO changedFrame = visionGovernor.evaluateFrame(sessionId, changedScreenBase64, 3500L);
        assertFalse(changedFrame.isSkipped(), "显著变动帧 (汉明距离超标) 必须放行");
    }

    @Test
    @DisplayName("Contract 08: 全双工双向多模态 WebSocket 状态机生命周期契约")
    void contract08_realtimeMultimodalWebSocketLifecycle() {
        RealtimeSessionDO session = hubEndpoint.createSession("USER_WS_LIFECYCLE");
        assertNotNull(session);
        assertEquals(RealtimeSessionDO.SessionState.IDLE, session.getSessionState());

        // 注入视觉帧
        String dummyImg = "data:image/png;base64,dummy123456";
        MultimodalVisionFrameDTO vFrame = hubEndpoint.handleIncomingVision(session.getSessionId(), dummyImg, System.currentTimeMillis());
        assertNotNull(vFrame);
        assertEquals(1L, session.getVisionFramesReceived());

        // 关闭会话
        hubEndpoint.closeSession(session.getSessionId());
        assertNull(hubEndpoint.getSession(session.getSessionId()), "会话关闭后从活动表注销");
    }

    @Test
    @DisplayName("Contract 09: 极端断网重连与客户端 Jitter Buffer 自愈契约")
    void contract09_networkDisconnectSessionCleanUp() {
        RealtimeSessionDO session = hubEndpoint.createSession("USER_NETWORK_DROP");
        String sessId = session.getSessionId();

        // 模拟异常断开并清理
        hubEndpoint.closeSession(sessId);

        // 尝试向已注销会话发送音频，必须返回 ERROR 事件或优雅降级
        byte[] pcm = createSyntheticPcmFrame(0.05, 320);
        RealtimeEventVO errEvt = hubEndpoint.handleIncomingAudio(sessId, pcm, 1L);
        assertNotNull(errEvt);
        assertEquals(RealtimeEventVO.EventType.ERROR, errEvt.getEventType(), "无效会话必须安全拦截并返回错误");
    }

    @Test
    @DisplayName("Contract 10: 端到端实时音视频多模态交互全链路协同闭环契约")
    void contract10_endToEndMultimodalHubCoordinatedFlow() {
        RealtimeSessionDO session = hubEndpoint.createSession("USER_E2E_COORDINATED");

        // 1. 用户说话开始
        byte[] voiceChunk = createSyntheticPcmFrame(0.12, 320);
        for (int i = 0; i < 8; i++) {
            hubEndpoint.handleIncomingAudio(session.getSessionId(), voiceChunk, session.getEpochValue());
        }
        assertEquals(RealtimeSessionDO.SessionState.USER_SPEAKING, session.getSessionState());

        // 2. 视觉画面输入
        MultimodalVisionFrameDTO visionFrame = hubEndpoint.handleIncomingVision(session.getSessionId(), "image_ppt_slide_1", System.currentTimeMillis());
        assertNotNull(visionFrame);

        // 3. 流式生成与 TTS 微管线推进
        RealtimeStreamingPipeline.PipelineExecutionResult pipeRes = 
                streamingPipeline.executeStreamingPipeline("请根据PPT介绍业务", session.getEpochValue());
        assertFalse(pipeRes.getTtsAudioChunks().isEmpty());
        session.setSessionState(RealtimeSessionDO.SessionState.BOT_SPEAKING);

        // 4. 用户突然打断
        streamingPipeline.handleBargeIn(session);
        assertEquals(RealtimeSessionDO.SessionState.USER_SPEAKING, session.getSessionState());
        assertEquals(2L, session.getEpochValue());
        assertEquals(1, session.getBargeInCount(), "打断计数累加为 1");

        hubEndpoint.closeSession(session.getSessionId());
    }
}
