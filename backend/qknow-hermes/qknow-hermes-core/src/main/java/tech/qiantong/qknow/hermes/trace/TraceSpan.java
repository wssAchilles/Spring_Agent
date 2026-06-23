package tech.qiantong.qknow.hermes.trace;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 追踪片段模型。
 */
@Data
public class TraceSpan {

    private final String spanId;
    private final String name;
    private long startTime;
    private long endTime;

    @Getter(AccessLevel.NONE)
    private long duration;

    private String status = "success";
    private String output;
    private int tokenCount;
    private double cost;
    private List<TraceSpan> children;

    public TraceSpan(String name) {
        this.spanId = UUID.randomUUID().toString();
        this.name = name;
        this.startTime = System.currentTimeMillis();
        this.children = new ArrayList<>();
    }

    public long getDuration() {
        return this.endTime - this.startTime;
    }

    public void addChild(TraceSpan child) {
        if (this.children.isEmpty() && !(this.children instanceof ArrayList)) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
