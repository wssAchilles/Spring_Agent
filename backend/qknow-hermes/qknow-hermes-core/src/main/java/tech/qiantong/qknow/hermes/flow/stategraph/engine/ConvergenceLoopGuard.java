package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.LoopDecisionType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 循环收敛门禁 (ConvergenceLoopGuard)
 * 具备边级迭代计数、全局超步熔断与 SpEL 条件动态评估能力
 */
@Slf4j
@Component
public class ConvergenceLoopGuard {

    private final int globalMaxSupersteps;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public ConvergenceLoopGuard() {
        this(50);
    }

    public ConvergenceLoopGuard(int globalMaxSupersteps) {
        this.globalMaxSupersteps = Math.max(globalMaxSupersteps, 1);
    }

    /**
     * 评估回跳循环边的决策流向
     *
     * @param loopEdge 回跳边定义
     * @param context 运行上下文
     * @param latestResult 当前节点执行结果
     * @return 门禁决策结果：CONVERGED_EXIT / CONTINUE_LOOP / DEGRADED_BREAK
     */
    public LoopDecisionType evaluateLoopDecision(
            StateGraphEdge loopEdge,
            StateGraphContext context,
            NodeRunResultBO latestResult) {

        // 1. 检查全局超步是否达到硬上限
        int currentStep = context.getCurrentSuperstep().get();
        if (currentStep >= globalMaxSupersteps) {
            log.warn("[LoopGuard] 全局超步达到硬熔断上限 {} (当前步数: {}), 强制触发 DEGRADED_BREAK 逃逸",
                    globalMaxSupersteps, currentStep);
            context.getDegradedBreakOccurred().set(true);
            return LoopDecisionType.DEGRADED_BREAK;
        }

        // 2. 检查条件谓词是否达成收敛
        String exprStr = loopEdge.getConditionExpression();
        if (exprStr != null && !exprStr.isBlank()) {
            boolean satisfied = evaluateCondition(exprStr, context, latestResult);
            if (satisfied) {
                log.info("[LoopGuard] 边 {} 收敛条件满足 ({}), 退出循环进入前向流转",
                        loopEdge.getEdgeId(), exprStr);
                return LoopDecisionType.CONVERGED_EXIT;
            }
        }

        // 3. 递增单边迭代计数并检查是否超出配额
        int maxIterations = loopEdge.getMaxIterations() > 0 ? loopEdge.getMaxIterations() : 10;
        int currentCount = context.incrementLoopCount(loopEdge.getEdgeId());

        if (currentCount > maxIterations) {
            log.warn("[LoopGuard] 回跳边 {} 累计循环次数 {} 超过上限 {}, 强制触发 DEGRADED_BREAK 逃逸",
                    loopEdge.getEdgeId(), currentCount, maxIterations);
            context.getDegradedBreakOccurred().set(true);
            return LoopDecisionType.DEGRADED_BREAK;
        }

        log.debug("[LoopGuard] 回跳边 {} 推进第 {}/{} 轮循环迭代",
                loopEdge.getEdgeId(), currentCount, maxIterations);
        return LoopDecisionType.CONTINUE_LOOP;
    }

    /**
     * 安全评估 SpEL 表达式
     */
    private boolean evaluateCondition(String exprStr, StateGraphContext context, NodeRunResultBO latestResult) {
        try {
            Expression expression = expressionCache.computeIfAbsent(exprStr, expressionParser::parseExpression);
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariable("result", latestResult);
            evalContext.setVariable("context", context);
            evalContext.setVariable("iteration", context.getLoopCount(exprStr));

            Boolean val = expression.getValue(evalContext, Boolean.class);
            return Boolean.TRUE.equals(val);
        } catch (Exception e) {
            log.error("[LoopGuard] 评估表达式 '{}' 失败, 默认判定为未收敛: {}", exprStr, e.getMessage());
            return false;
        }
    }
}
