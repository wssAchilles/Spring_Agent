package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.ConvergenceLoopGuard;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.LoopDecisionType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConvergenceLoopGuard 收敛门禁测试")
class ConvergenceLoopGuardTest {

    private ConvergenceLoopGuard loopGuard;
    private StateGraphContext context;

    @BeforeEach
    void setUp() {
        loopGuard = new ConvergenceLoopGuard(50);
        context = new StateGraphContext("exec-loop-1", "flow-loop-1", 10);
    }

    @Test
    @DisplayName("正常循环迭代在预算内返回 CONTINUE_LOOP")
    void loopGuard_withinLimit_continuesLoop() {
        StateGraphEdge loopEdge = StateGraphEdge.builder()
                .edgeId("edge-loop-1")
                .sourceNodeUuid("evaluator")
                .targetNodeUuid("generator")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .maxIterations(5)
                .conditionExpression("#result.output.get('score') >= 0.9") // 未达到时继续循环
                .build();

        // 模拟当前节点输出 score = 0.6
        NodeRunResultBO resultBO = new NodeRunResultBO();
        resultBO.setNodeUuid("evaluator");
        resultBO.setOutput(Map.of("score", 0.6));
        context.putNodeResult("evaluator", resultBO);

        // 前 4 轮迭代必须返回 CONTINUE_LOOP
        for (int i = 1; i <= 4; i++) {
            LoopDecisionType decision = loopGuard.evaluateLoopDecision(loopEdge, context, resultBO);
            assertEquals(LoopDecisionType.CONTINUE_LOOP, decision, "第 " + i + " 轮必须返回 CONTINUE_LOOP");
            assertEquals(i, context.getLoopCount("edge-loop-1"));
        }
    }

    @Test
    @DisplayName("达到收敛条件时返回 CONVERGED_EXIT 退出循环")
    void loopGuard_predicateSatisfied_convergedExit() {
        StateGraphEdge loopEdge = StateGraphEdge.builder()
                .edgeId("edge-loop-2")
                .sourceNodeUuid("evaluator")
                .targetNodeUuid("generator")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .maxIterations(5)
                .conditionExpression("#result.output.get('score') >= 0.9")
                .build();

        // 模拟当前节点输出 score = 0.95，满足收敛条件
        NodeRunResultBO resultBO = new NodeRunResultBO();
        resultBO.setNodeUuid("evaluator");
        resultBO.setOutput(Map.of("score", 0.95));
        context.putNodeResult("evaluator", resultBO);

        LoopDecisionType decision = loopGuard.evaluateLoopDecision(loopEdge, context, resultBO);
        assertEquals(LoopDecisionType.CONVERGED_EXIT, decision, "满足 score >= 0.9 必须收敛退出");
    }

    @Test
    @DisplayName("超过单边最大循环轮次触发 DEGRADED_BREAK 降级熔断")
    void loopGuard_exceedLimit_triggersDegradedBreak() {
        StateGraphEdge loopEdge = StateGraphEdge.builder()
                .edgeId("edge-loop-3")
                .sourceNodeUuid("evaluator")
                .targetNodeUuid("generator")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .maxIterations(3)
                .conditionExpression("#result.output.get('score') >= 0.99")
                .build();

        NodeRunResultBO resultBO = new NodeRunResultBO();
        resultBO.setNodeUuid("evaluator");
        resultBO.setOutput(Map.of("score", 0.5));
        context.putNodeResult("evaluator", resultBO);

        // 迭代 3 轮
        assertEquals(LoopDecisionType.CONTINUE_LOOP, loopGuard.evaluateLoopDecision(loopEdge, context, resultBO));
        assertEquals(LoopDecisionType.CONTINUE_LOOP, loopGuard.evaluateLoopDecision(loopEdge, context, resultBO));
        assertEquals(LoopDecisionType.CONTINUE_LOOP, loopGuard.evaluateLoopDecision(loopEdge, context, resultBO));

        // 第 4 轮超限，必须触发 DEGRADED_BREAK
        LoopDecisionType decision = loopGuard.evaluateLoopDecision(loopEdge, context, resultBO);
        assertEquals(LoopDecisionType.DEGRADED_BREAK, decision, "超过 maxIterations 必须触发 DEGRADED_BREAK");
        assertTrue(context.getDegradedBreakOccurred().get(), "上下文必须记录发生降级熔断");
    }

    @Test
    @DisplayName("全局超步超限触发全局 DEGRADED_BREAK")
    void loopGuard_superstepExceeded_triggersGlobalBreak() {
        StateGraphEdge loopEdge = StateGraphEdge.builder()
                .edgeId("edge-loop-4")
                .sourceNodeUuid("A")
                .targetNodeUuid("B")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .maxIterations(100) // 边未超限
                .build();

        // 模拟全局步数达到上限 50
        for (int i = 0; i < 50; i++) {
            context.incrementSuperstep();
        }

        LoopDecisionType decision = loopGuard.evaluateLoopDecision(loopEdge, context, new NodeRunResultBO());
        assertEquals(LoopDecisionType.DEGRADED_BREAK, decision, "全局超步达到上限必须强制熔断");
    }
}
