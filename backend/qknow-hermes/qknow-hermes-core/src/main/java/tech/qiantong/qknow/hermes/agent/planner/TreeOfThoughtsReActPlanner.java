package tech.qiantong.qknow.hermes.agent.planner;

import org.springframework.ai.tool.ToolCallback;
import tech.qiantong.qknow.hermes.agent.planner.mcts.MctsSearchReceipt;

import java.util.List;
import java.util.Map;

/**
 * 蒙特卡洛思维树搜索 (MCTS + ToT) 认知规划器接口
 * 结合前瞻状态评估、超球面向量先验偏角加权与 UCB1 剪枝回溯，攻克单线 ReAct 线性死胡同。
 */
public interface TreeOfThoughtsReActPlanner {

    /**
     * 针对复杂多跳目标执行 MCTS 树分支探索与状态回溯规划
     *
     * @param rootGoal 目标任务
     * @param availableTools 可用工具集
     * @param context 隔离上下文
     * @param maxIterations 最大树搜索迭代次数 (推荐 10~20)
     * @param maxDepth 最大前瞻深度 (推荐 3~5)
     * @return 包含最优推理执行路径与审计存证的凭单
     */
    MctsSearchReceipt planAndExecute(
            String rootGoal,
            List<ToolCallback> availableTools,
            Map<String, Object> context,
            int maxIterations,
            int maxDepth
    );
}
