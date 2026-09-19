package tech.qiantong.qknow.ai.mor;

import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.mor.model.DualTrackStreamEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 双轨流式思考分发器 (Dual-Track Thinking Dispatcher)
 * 将模型的内部认知推演与最终业务答复在传输层彻底解耦，消除白屏假死与上下文污染
 */
@Component
public class DualTrackThinkingDispatcher {

    /**
     * 构建单个分发信封
     */
    public List<DualTrackStreamEnvelope> dispatchChunk(
            String traceId,
            DualTrackStreamEnvelope.StreamTrack track,
            String textDelta,
            long sequenceNumber,
            boolean isFinished,
            String finishReason
    ) {
        DualTrackStreamEnvelope envelope = new DualTrackStreamEnvelope(
                traceId != null ? traceId : "trace-default",
                track,
                textDelta,
                sequenceNumber,
                isFinished,
                finishReason
        );
        return List.of(envelope);
    }

    /**
     * 基于流式 FSM 解析器实时将混合文本分发为双轨信封流
     */
    public void dispatchStream(
            String traceId,
            String rawChunk,
            CoTStreamFsmParser parser,
            AtomicLong seqCounter,
            Consumer<DualTrackStreamEnvelope> sink
    ) {
        if (rawChunk == null || rawChunk.isEmpty()) {
            return;
        }

        parser.feed(rawChunk, parsed -> {
            DualTrackStreamEnvelope.StreamTrack track = (parsed.type() == CoTStreamFsmParser.StreamChunkType.THINKING)
                    ? DualTrackStreamEnvelope.StreamTrack.THINKING
                    : DualTrackStreamEnvelope.StreamTrack.CONTENT;

            sink.accept(new DualTrackStreamEnvelope(
                    traceId,
                    track,
                    parsed.text(),
                    seqCounter.incrementAndGet(),
                    false,
                    null
            ));
        });
    }

    /**
     * 流结束时的收尾，刷新残留缓冲并发出结束帧
     */
    public void finishStream(
            String traceId,
            CoTStreamFsmParser parser,
            AtomicLong seqCounter,
            Consumer<DualTrackStreamEnvelope> sink
    ) {
        parser.finish(parsed -> {
            DualTrackStreamEnvelope.StreamTrack track = (parsed.type() == CoTStreamFsmParser.StreamChunkType.THINKING)
                    ? DualTrackStreamEnvelope.StreamTrack.THINKING
                    : DualTrackStreamEnvelope.StreamTrack.CONTENT;

            sink.accept(new DualTrackStreamEnvelope(
                    traceId,
                    track,
                    parsed.text(),
                    seqCounter.incrementAndGet(),
                    false,
                    null
            ));
        });

        // 发送终止帧
        sink.accept(new DualTrackStreamEnvelope(
                traceId,
                DualTrackStreamEnvelope.StreamTrack.CONTENT,
                "",
                seqCounter.incrementAndGet(),
                true,
                "stop"
        ));
    }
}
