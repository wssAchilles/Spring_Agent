package tech.qiantong.qknow.ai.embodied.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 多源时序对齐感知帧 (Phase 64)
 * <p>
 * 封装单时刻跨模态对齐切片（文本指令、结构化遥测、视觉符号与单调逻辑时序标识）
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class MultimodalTemporalFrame implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String frameId;
    private final long timestampMs;         // 物理毫秒时间戳
    private final long monotonicSeq;        // 单调递增逻辑序列号
    private final String textInstruction;   // 自然语言操作指令
    private final Map<String, Double> telemetryValues; // 结构化传感器遥测 (如关节角、力矩、测距)
    private final String visualSymbol;      // 视觉目标符号标签与位姿摘要

    public MultimodalTemporalFrame(
            String frameId,
            long timestampMs,
            long monotonicSeq,
            String textInstruction,
            Map<String, Double> telemetryValues,
            String visualSymbol
    ) {
        this.frameId = frameId;
        this.timestampMs = timestampMs;
        this.monotonicSeq = monotonicSeq;
        this.textInstruction = textInstruction != null ? textInstruction : "";
        this.telemetryValues = telemetryValues != null
                ? Collections.unmodifiableMap(telemetryValues)
                : Map.of();
        this.visualSymbol = visualSymbol != null ? visualSymbol : "NONE";
    }

    public String getFrameId() { return frameId; }
    public long getTimestampMs() { return timestampMs; }
    public long getMonotonicSeq() { return monotonicSeq; }
    public String getTextInstruction() { return textInstruction; }
    public Map<String, Double> getTelemetryValues() { return telemetryValues; }
    public String getVisualSymbol() { return visualSymbol; }
}
