package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.MultimodalTemporalFrame;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多源时序感知流采集与对齐器 (Phase 64)
 * <p>
 * 接入文本指令、结构化遥测与视觉符号流，引入 200ms 滑动窗口与单调时钟消除时钟漂移与因果倒流。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class MultimodalTemporalIngestor {

    private static final Logger log = LoggerFactory.getLogger(MultimodalTemporalIngestor.class);
    public static final long SLIDING_WINDOW_MS = 200L;

    private final AtomicLong monotonicSequence = new AtomicLong(0);
    // 按时间戳有序组织的感知切片跳表缓冲
    private final NavigableMap<Long, List<MultimodalTemporalFrame>> frameBuffer = new ConcurrentSkipListMap<>();

    /**
     * 摄取异构多源感知数据并生成自增单调序号
     */
    public MultimodalTemporalFrame ingestRawEvent(
            long physicalTimestampMs,
            String textInstruction,
            Map<String, Double> telemetry,
            String visualSymbol
    ) {
        long seq = monotonicSequence.incrementAndGet();
        String frameId = "FRM-" + seq;
        MultimodalTemporalFrame frame = new MultimodalTemporalFrame(
                frameId, physicalTimestampMs, seq, textInstruction, telemetry, visualSymbol
        );

        frameBuffer.compute(physicalTimestampMs, (k, v) -> {
            List<MultimodalTemporalFrame> list = (v == null) ? new ArrayList<>() : v;
            list.add(frame);
            return list;
        });

        log.debug("摄取时序感知切片 [frameId={}, physicalTs={}, seq={}]", frameId, physicalTimestampMs, seq);
        return frame;
    }

    /**
     * 滑动窗口对齐提取：提取截至指定时间戳窗口范围 [currentTs - 200ms, currentTs] 的有序帧并对齐聚合
     */
    public List<MultimodalTemporalFrame> alignSlidingWindow(long currentPhysicalTimestampMs) {
        long windowStart = Math.max(0, currentPhysicalTimestampMs - SLIDING_WINDOW_MS);
        NavigableMap<Long, List<MultimodalTemporalFrame>> subMap = frameBuffer.subMap(windowStart, true, currentPhysicalTimestampMs, true);

        List<MultimodalTemporalFrame> alignedFrames = new ArrayList<>();
        for (List<MultimodalTemporalFrame> list : subMap.values()) {
            alignedFrames.addAll(list);
        }

        // 严格根据单调逻辑时钟进行主偏序对齐，彻底排除 NTP 抖动引起的时序紊乱
        alignedFrames.sort(Comparator.comparingLong(MultimodalTemporalFrame::getMonotonicSeq));

        // 清理过期历史帧（释放内存）
        frameBuffer.headMap(windowStart, false).clear();

        log.info("完成 200ms 滑动窗口时序对齐，窗口范围 [{} ~ {}ms]，聚合帧数: {}",
                windowStart, currentPhysicalTimestampMs, alignedFrames.size());
        return Collections.unmodifiableList(alignedFrames);
    }

    public long getCurrentMonotonicSequence() {
        return monotonicSequence.get();
    }
}
