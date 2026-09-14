package tech.qiantong.qknow.hermes.memory.dst;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.model.DialogueStateFrame;
import tech.qiantong.qknow.hermes.memory.model.SlotValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DST 对话状态跟踪器默认实现
 * 支持多轮槽位填充、意图 DAG 转移约束，以及基于栈结构的分支插话挂起与精准无损恢复。
 */
@Slf4j
public class DialogueStateTrackerImpl implements DialogueStateTracker {

    private final IntentDAG intentDAG;
    private final Map<String, DialogueStateFrame> sessionStates = new ConcurrentHashMap<>();

    public DialogueStateTrackerImpl() {
        this(new IntentDAG());
    }

    public DialogueStateTrackerImpl(IntentDAG intentDAG) {
        this.intentDAG = intentDAG != null ? intentDAG : new IntentDAG();
    }

    @Override
    public DialogueStateFrame processTurn(String sessionId, String userUtterance, String detectedIntent, Map<String, Object> extractedSlots) {
        DialogueStateFrame frame = sessionStates.computeIfAbsent(sessionId, sid -> DialogueStateFrame.builder()
                .sessionId(sid)
                .currentIntent(detectedIntent)
                .slots(new HashMap<>())
                .intentStack(new ArrayDeque<>())
                .status(DialogueStateFrame.StateStatus.ACTIVE)
                .updatedAt(System.currentTimeMillis())
                .build());

        String currentIntent = frame.getCurrentIntent();
        if (detectedIntent != null && !detectedIntent.isBlank()) {
            if (intentDAG.canTransition(currentIntent, detectedIntent)) {
                frame.setCurrentIntent(detectedIntent);
            } else {
                log.warn("Intent transition from {} to {} is not explicitly allowed in DAG, keeping {}",
                        currentIntent, detectedIntent, currentIntent);
            }
        }

        if (extractedSlots != null) {
            for (Map.Entry<String, Object> entry : extractedSlots.entrySet()) {
                if (entry.getValue() != null) {
                    frame.setSlot(entry.getKey(), entry.getValue(), 1.0);
                }
            }
        }

        frame.setUpdatedAt(System.currentTimeMillis());
        return frame;
    }

    @Override
    public void pushFrame(String sessionId, String newIntent, Map<String, Object> initialSlots) {
        DialogueStateFrame current = sessionStates.get(sessionId);
        if (current == null) {
            current = DialogueStateFrame.builder()
                    .sessionId(sessionId)
                    .currentIntent(newIntent)
                    .slots(new HashMap<>())
                    .intentStack(new ArrayDeque<>())
                    .status(DialogueStateFrame.StateStatus.ACTIVE)
                    .updatedAt(System.currentTimeMillis())
                    .build();
            sessionStates.put(sessionId, current);
        } else {
            // 将当前状态创建快照并挂起推入栈
            DialogueStateFrame suspended = DialogueStateFrame.builder()
                    .sessionId(sessionId)
                    .currentIntent(current.getCurrentIntent())
                    .slots(new HashMap<>(current.getSlots()))
                    .status(DialogueStateFrame.StateStatus.SUSPENDED)
                    .updatedAt(current.getUpdatedAt())
                    .build();
            current.getIntentStack().push(suspended);

            // 切换为新的子意图
            current.setCurrentIntent(newIntent);
            current.setSlots(new HashMap<>());
            current.setStatus(DialogueStateFrame.StateStatus.ACTIVE);
            current.setUpdatedAt(System.currentTimeMillis());
        }

        if (initialSlots != null) {
            for (Map.Entry<String, Object> entry : initialSlots.entrySet()) {
                if (entry.getValue() != null) {
                    current.setSlot(entry.getKey(), entry.getValue(), 1.0);
                }
            }
        }
        log.info("Pushed dialogue frame for session {}: newIntent={}, stackDepth={}",
                sessionId, newIntent, current.getIntentStack().size());
    }

    @Override
    public DialogueStateFrame popFrame(String sessionId) {
        DialogueStateFrame current = sessionStates.get(sessionId);
        if (current == null || current.getIntentStack().isEmpty()) {
            log.warn("No suspended frame to pop for session {}", sessionId);
            return current;
        }

        // 弹出上一层挂起的父级状态帧并恢复
        DialogueStateFrame parentFrame = current.getIntentStack().pop();
        current.setCurrentIntent(parentFrame.getCurrentIntent());
        current.setSlots(parentFrame.getSlots()); // 槽位 100% 原样无损恢复
        current.setStatus(DialogueStateFrame.StateStatus.ACTIVE);
        current.setUpdatedAt(System.currentTimeMillis());

        log.info("Popped dialogue frame for session {}: restoredIntent={}, restoredSlots={}, remainingStackDepth={}",
                sessionId, current.getCurrentIntent(), current.getSlots().keySet(), current.getIntentStack().size());
        return current;
    }

    @Override
    public DialogueStateFrame getCurrentFrame(String sessionId) {
        return sessionStates.get(sessionId);
    }

    @Override
    public IntentDAG getIntentDAG() {
        return intentDAG;
    }

    @Override
    public void clear(String sessionId) {
        sessionStates.remove(sessionId);
    }
}
