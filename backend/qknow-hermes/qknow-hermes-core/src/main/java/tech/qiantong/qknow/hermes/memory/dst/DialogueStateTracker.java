package tech.qiantong.qknow.hermes.memory.dst;

import tech.qiantong.qknow.hermes.memory.model.DialogueStateFrame;

import java.util.Map;

/**
 * 对话状态跟踪器 (Dialogue State Tracker, DST) 契约接口
 */
public interface DialogueStateTracker {

    /**
     * 处理单轮对话交互：提取/合并槽位，推动意图状态流转
     */
    DialogueStateFrame processTurn(String sessionId, String userUtterance, String detectedIntent, Map<String, Object> extractedSlots);

    /**
     * 分支插话：挂起当前主意图栈帧，激活子意图
     */
    void pushFrame(String sessionId, String newIntent, Map<String, Object> initialSlots);

    /**
     * 子任务闭环：出栈恢复上一级挂起的主意图状态帧，保持槽位无损
     */
    DialogueStateFrame popFrame(String sessionId);

    /**
     * 获取指定会话当前活跃的对话状态帧
     */
    DialogueStateFrame getCurrentFrame(String sessionId);

    /**
     * 获取关联的 IntentDAG
     */
    IntentDAG getIntentDAG();

    /**
     * 清理指定会话的状态帧
     */
    void clear(String sessionId);
}
