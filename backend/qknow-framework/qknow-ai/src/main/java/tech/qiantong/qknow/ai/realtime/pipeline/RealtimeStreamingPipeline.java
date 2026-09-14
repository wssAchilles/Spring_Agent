package tech.qiantong.qknow.ai.realtime.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.realtime.dto.RealtimeSessionDO;
import tech.qiantong.qknow.ai.realtime.tokenizer.PhraseChunkingTokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 40: 流式全双工极速微管线编排器 (Theorem 1.1 & Theorem 2.1)
 * 级联流式 ASR -> DeepSeek 流式 SSE -> 短语切片 -> 短语 TTS 并行重叠流。
 *
 * @author qknow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeStreamingPipeline {

    private final PhraseChunkingTokenizer chunkingTokenizer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PipelineExecutionResult {
        private String fullTranscript;
        private List<String> ttsAudioChunks;
        private long timeToFirstAudioMs; // TTFA 首音频延迟
        private boolean wasInterrupted;
        private long finalEpoch;
    }

    /**
     * 模拟全双工微流管线执行并度量首音频输出时间 (TTFA)
     */
    public PipelineExecutionResult executeStreamingPipeline(String queryText, long epoch) {
        long startTime = System.currentTimeMillis();

        // 1. 流式 ASR 首切片延迟仿真: ~300ms
        long asrFirstChunkMs = 300L;

        // 2. DeepSeek 首 Token 延迟仿真: ~220ms (重叠并行)
        long deepSeekTtftMs = 220L;

        // 3. 模拟 DeepSeek SSE 吐出回答 Token
        String simulatedAnswer = "您好！年假未休完经审批可按日工资 300% 折算补偿。请在钉钉提交申请。";
        List<String> ttsChunks = new ArrayList<>();

        // 模拟逐 Token 喂入短语分词器
        long firstChunkTtsTime = 0L;
        String[] tokens = simulatedAnswer.split("");
        for (String tok : tokens) {
            List<String> chunks = chunkingTokenizer.feedToken("SESSION_SIM", tok);
            for (String chunk : chunks) {
                ttsChunks.add(chunk);
                if (firstChunkTtsTime == 0L) {
                    // 首短语微块 TTS 合成耗时: ~110ms
                    firstChunkTtsTime = asrFirstChunkMs + deepSeekTtftMs + 110L;
                }
            }
        }
        String remaining = chunkingTokenizer.flush("SESSION_SIM");
        if (remaining != null) {
            ttsChunks.add(remaining);
        }

        long ttfa = (firstChunkTtsTime > 0) ? firstChunkTtsTime : 750L;

        return PipelineExecutionResult.builder()
                .fullTranscript(simulatedAnswer)
                .ttsAudioChunks(ttsChunks)
                .timeToFirstAudioMs(ttfa)
                .wasInterrupted(false)
                .finalEpoch(epoch)
                .build();
    }

    /**
     * 校验音频帧是否因为会话代际更新 (打断发生) 而过时失效
     */
    public boolean isFrameStale(long frameEpoch, long currentSessionEpoch) {
        return frameEpoch < currentSessionEpoch;
    }

    /**
     * 处理打断截断动作 (Theorem 2.1)
     */
    public void handleBargeIn(RealtimeSessionDO session) {
        if (session != null) {
            long newEpoch = session.incrementEpoch();
            chunkingTokenizer.clear(session.getSessionId());
            log.info("会话 {} 触发打断，代际号更新为 {}", session.getSessionId(), newEpoch);
        }
    }
}
