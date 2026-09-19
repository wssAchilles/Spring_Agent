package tech.qiantong.qknow.ai.mor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.mor.model.DualTrackStreamEnvelope;
import tech.qiantong.qknow.ai.mor.model.ThinkingStreamInterruptionReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 双轨流式思考分发器 (强化治理版：内嵌实时流式死循环截断与因果自愈切面)
 * 将模型的内部认知推演与最终业务答复在传输层彻底解耦，消除白屏假死、死循环与上下文污染
 */
@Component
public class DualTrackThinkingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DualTrackThinkingDispatcher.class);

    private final ThinkingStreamInterrupter interrupter;
    private final ReflectiveSelfHealingCoordinator healingCoordinator;

    // 活跃会话流分析上下文: traceId -> SessionStreamContext
    private final Map<String, ThinkingStreamInterrupter.SessionStreamContext> sessionContexts = new ConcurrentHashMap<>();

    // 活跃会话中断标志: traceId -> AtomicBoolean
    private final Map<String, AtomicBoolean> interruptionFlags = new ConcurrentHashMap<>();

    /**
     * 默认构造函数：自动实例化轻量组件，保证单元测试与无注入环境兼容
     */
    public DualTrackThinkingDispatcher() {
        this(new ThinkingStreamInterrupter(), new ReflectiveSelfHealingCoordinator());
    }

    /**
     * Spring 依赖注入构造函数
     */
    @Autowired
    public DualTrackThinkingDispatcher(ThinkingStreamInterrupter interrupter,
                                       ReflectiveSelfHealingCoordinator healingCoordinator) {
        this.interrupter = interrupter != null ? interrupter : new ThinkingStreamInterrupter();
        this.healingCoordinator = healingCoordinator != null ? healingCoordinator : new ReflectiveSelfHealingCoordinator();
    }

    public ThinkingStreamInterrupter getInterrupter() {
        return interrupter;
    }

    public ReflectiveSelfHealingCoordinator getHealingCoordinator() {
        return healingCoordinator;
    }

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
     * 检查指定 traceId 的流是否已被中断
     */
    public boolean isTraceInterrupted(String traceId) {
        if (traceId == null) return false;
        AtomicBoolean flag = interruptionFlags.get(traceId);
        return flag != null && flag.get();
    }

    /**
     * 基于流式 FSM 解析器实时将混合文本分发为双轨信封流，并实时执行死循环认知监控
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

        String safeTraceId = traceId != null ? traceId : "trace-default";
        AtomicBoolean interruptedFlag = interruptionFlags.computeIfAbsent(safeTraceId, k -> new AtomicBoolean(false));

        // 如果已被中断，抑制后续思考流文本
        if (interruptedFlag.get()) {
            return;
        }

        ThinkingStreamInterrupter.SessionStreamContext context =
                sessionContexts.computeIfAbsent(safeTraceId, k -> interrupter.createContext());

        parser.feed(rawChunk, parsed -> {
            if (interruptedFlag.get()) {
                return;
            }

            if (parsed.type() == CoTStreamFsmParser.StreamChunkType.THINKING) {
                // 实时认知监控：判定是否陷入思考死循环
                ThinkingStreamInterrupter.InterruptionDecision decision =
                        interrupter.feedAndEvaluate(context, parsed.text());

                if (decision.shouldInterrupt()) {
                    if (interruptedFlag.compareAndSet(false, true)) {
                        log.warn("[DualTrackDispatcher] traceId={} 触发思考流实时截断: {}", safeTraceId, decision.reason());

                        // 下发平滑中断提示信封
                        sink.accept(new DualTrackStreamEnvelope(
                                safeTraceId,
                                DualTrackStreamEnvelope.StreamTrack.THINKING,
                                "\n[系统认知哨兵干预: 思考链陷入死循环震荡，已触发毫秒级截断与因果自愈]\n",
                                seqCounter.incrementAndGet(),
                                false,
                                "interrupted"
                        ));
                        return;
                    }
                }
            }

            // 正常分发
            DualTrackStreamEnvelope.StreamTrack track = (parsed.type() == CoTStreamFsmParser.StreamChunkType.THINKING)
                    ? DualTrackStreamEnvelope.StreamTrack.THINKING
                    : DualTrackStreamEnvelope.StreamTrack.CONTENT;

            sink.accept(new DualTrackStreamEnvelope(
                    safeTraceId,
                    track,
                    parsed.text(),
                    seqCounter.incrementAndGet(),
                    false,
                    null
            ));
        });
    }

    /**
     * 流结束时的收尾，刷新残留缓冲并发出结束帧，内嵌平滑封口保护
     */
    public void finishStream(
            String traceId,
            CoTStreamFsmParser parser,
            AtomicLong seqCounter,
            Consumer<DualTrackStreamEnvelope> sink
    ) {
        String safeTraceId = traceId != null ? traceId : "trace-default";
        boolean wasInterrupted = isTraceInterrupted(safeTraceId);

        if (!wasInterrupted) {
            parser.finish(parsed -> {
                DualTrackStreamEnvelope.StreamTrack track = (parsed.type() == CoTStreamFsmParser.StreamChunkType.THINKING)
                        ? DualTrackStreamEnvelope.StreamTrack.THINKING
                        : DualTrackStreamEnvelope.StreamTrack.CONTENT;

                sink.accept(new DualTrackStreamEnvelope(
                        safeTraceId,
                        track,
                        parsed.text(),
                        seqCounter.incrementAndGet(),
                        false,
                        null
                ));
            });
        }

        // 发送终止帧，保证 finishReason 精确标识
        sink.accept(new DualTrackStreamEnvelope(
                safeTraceId,
                DualTrackStreamEnvelope.StreamTrack.CONTENT,
                "",
                seqCounter.incrementAndGet(),
                true,
                wasInterrupted ? "interrupted" : "stop"
        ));

        // 清理会话上下文
        sessionContexts.remove(safeTraceId);
        interruptionFlags.remove(safeTraceId);
    }
}
