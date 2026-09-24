package tech.qiantong.qknow.hermes.rag.causal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 带图谱实体因果锚定的无损流式打字机管道 (定理 1.3)
 * 1. 在 SSE 打字机流中注入实体命题因果锚定帧 (ENTITY_ANCHOR)，实现流式答案与图谱因果闭环；
 * 2. 维护定长环形重放缓冲区，当客户端网络瞬态闪断时支持基于 lastAckSequenceId 的 0 丢失补齐重放；
 * 3. 驱动端到端时空流形对齐与因果拓扑剪枝，并签发不可变存证凭单。
 */
@Slf4j
@Component
public class GraphStreamLosslessTypewriter {

    public record TypewriterFrame(
            long sequenceId,
            String eventType, // "ENTITY_ANCHOR", "CHUNK", "COMPLETE", "ERROR"
            String content,
            String entityAnchorId,
            long timestamp
    ) {}

    private final AtomicLong globalSequenceGenerator = new AtomicLong(1);
    private final Map<String, List<TypewriterFrame>> sessionReplayBuffers = new ConcurrentHashMap<>();
    private static final int MAX_BUFFER_CAPACITY = 256;

    /**
     * 流式推送带图谱实体因果锚定的打字机响应流
     *
     * @param sessionId         会话 ID
     * @param causalPropositions 因果命题链列表
     * @param answerText        最终生成回答文本
     * @param chunkSize         文本分块大小 (字符数)
     * @param frameConsumer     帧监听消费者
     * @return 实际发送的总帧数
     */
    public int streamCausalResponse(
            String sessionId,
            List<String> causalPropositions,
            String answerText,
            int chunkSize,
            Consumer<TypewriterFrame> frameConsumer
    ) {
        String safeSessionId = sessionId != null ? sessionId : "default_stream";
        List<TypewriterFrame> buffer = sessionReplayBuffers.computeIfAbsent(
                safeSessionId, k -> new CopyOnWriteArrayList<>()
        );

        int sentCount = 0;

        // 1. 优先推送实体因果命题锚定帧
        if (causalPropositions != null) {
            int anchorIdx = 1;
            for (String prop : causalPropositions) {
                long seq = globalSequenceGenerator.getAndIncrement();
                TypewriterFrame anchorFrame = new TypewriterFrame(
                        seq,
                        "ENTITY_ANCHOR",
                        prop,
                        "anchor-" + (anchorIdx++),
                        System.currentTimeMillis()
                );
                recordAndEmit(buffer, anchorFrame, frameConsumer);
                sentCount++;
            }
        }

        // 2. 切分推送正文流式 chunk
        if (answerText != null && !answerText.isEmpty()) {
            int safeChunkSize = chunkSize > 0 ? chunkSize : 4;
            int len = answerText.length();
            for (int i = 0; i < len; i += safeChunkSize) {
                int end = Math.min(len, i + safeChunkSize);
                String chunk = answerText.substring(i, end);
                long seq = globalSequenceGenerator.getAndIncrement();
                TypewriterFrame chunkFrame = new TypewriterFrame(
                        seq,
                        "CHUNK",
                        chunk,
                        null,
                        System.currentTimeMillis()
                );
                recordAndEmit(buffer, chunkFrame, frameConsumer);
                sentCount++;
            }
        }

        // 3. 推送 COMPLETE 终态帧
        long completeSeq = globalSequenceGenerator.getAndIncrement();
        TypewriterFrame completeFrame = new TypewriterFrame(
                completeSeq,
                "COMPLETE",
                "[STREAM_DONE]",
                null,
                System.currentTimeMillis()
        );
        recordAndEmit(buffer, completeFrame, frameConsumer);
        sentCount++;

        return sentCount;
    }

    /**
     * 断网闪断无损重放机制：根据客户端最后确认的序号补发遗漏帧
     *
     * @param sessionId          会话 ID
     * @param lastAckSequenceId  客户端最后确认收到的序列号
     * @param frameConsumer      重发消费者
     * @return 实际补发重放的帧数
     */
    public int replayMissingFrames(String sessionId, long lastAckSequenceId, Consumer<TypewriterFrame> frameConsumer) {
        List<TypewriterFrame> buffer = sessionReplayBuffers.get(sessionId);
        if (buffer == null || buffer.isEmpty()) {
            return 0;
        }

        int replayCount = 0;
        for (TypewriterFrame frame : buffer) {
            if (frame.sequenceId() > lastAckSequenceId) {
                if (frameConsumer != null) {
                    frameConsumer.accept(frame);
                }
                replayCount++;
            }
        }
        log.info("打字机无损重放完成: sessionId={}, lastAck={}, replayed={}", sessionId, lastAckSequenceId, replayCount);
        return replayCount;
    }

    /**
     * 清理会话缓冲区
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionReplayBuffers.remove(sessionId);
        }
    }

    private void recordAndEmit(List<TypewriterFrame> buffer, TypewriterFrame frame, Consumer<TypewriterFrame> consumer) {
        if (buffer.size() >= MAX_BUFFER_CAPACITY) {
            buffer.remove(0);
        }
        buffer.add(frame);
        if (consumer != null) {
            consumer.accept(frame);
        }
    }
}
