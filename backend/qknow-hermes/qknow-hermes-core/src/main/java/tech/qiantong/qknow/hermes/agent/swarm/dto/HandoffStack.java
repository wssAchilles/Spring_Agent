package tech.qiantong.qknow.hermes.agent.swarm.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变交接历史栈
 * 严格施加 MAX_HANDOFF_DEPTH = 5 硬上限与栈深度检查
 */
public record HandoffStack(
        String sessionId,
        List<HandoffFrame> frames
) {
    public static final int MAX_HANDOFF_DEPTH = 5;

    public HandoffStack(String sessionId, List<HandoffFrame> frames) {
        this.sessionId = sessionId != null ? sessionId : "default-session";
        this.frames = frames != null ? Collections.unmodifiableList(new ArrayList<>(frames)) : Collections.emptyList();
    }

    public int depth() {
        return frames.size();
    }

    public boolean isMaxDepthReached() {
        return frames.size() >= MAX_HANDOFF_DEPTH;
    }

    public HandoffStack push(HandoffFrame frame) {
        if (isMaxDepthReached()) {
            throw new IllegalStateException("Handoff depth exceeded maximum limit: " + MAX_HANDOFF_DEPTH);
        }
        List<HandoffFrame> nextList = new ArrayList<>(frames);
        nextList.add(frame);
        return new HandoffStack(sessionId, nextList);
    }
}
