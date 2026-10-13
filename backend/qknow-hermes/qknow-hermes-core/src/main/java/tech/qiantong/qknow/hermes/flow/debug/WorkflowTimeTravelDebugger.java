package tech.qiantong.qknow.hermes.flow.debug;

import java.util.List;
import java.util.Map;

/**
 * 工作流交互式断点调试与时间旅行引擎接口
 * 提供断点挂起、单步执行、状态快照回溯与历史分叉重放能力
 */
public interface WorkflowTimeTravelDebugger {

    /**
     * 注册/更新断点
     */
    void registerBreakpoint(DebugBreakpointRecord breakpoint);

    /**
     * 移除断点
     */
    void removeBreakpoint(String breakpointId);

    /**
     * 检查节点是否应当触发断点挂起
     */
    boolean shouldPauseAtNode(String flowId, String nodeId, Map<String, Object> currentVariables);

    /**
     * 挂起工作流执行并记录当前调试快照
     */
    void pauseExecution(String runtimeId, String nodeId, Map<String, Object> contextSnapshot);

    /**
     * 单步跳过 (Step Over)
     */
    void stepOver(String runtimeId);

    /**
     * 继续全速执行 (Resume)
     */
    void resumeExecution(String runtimeId);

    /**
     * 记录执行步序快照
     */
    void recordStepSnapshot(TimeTravelStepRecord stepRecord);

    /**
     * 获取指定运行时的完整时间旅行历史版本链
     */
    List<TimeTravelStepRecord> getTimeTravelHistory(String runtimeId);

    /**
     * 时间旅行分叉重放：从历史指定步序重新分叉执行工作流
     *
     * @param runtimeId        原执行运行时 ID
     * @param forkStepSequence 回溯的目标历史步序
     * @param modifiedVariables 开发者在调试面板中注入的修正变量环境
     * @return 新分叉的运行时 ID
     */
    String forkAndReplay(String runtimeId, long forkStepSequence, Map<String, Object> modifiedVariables);
}
