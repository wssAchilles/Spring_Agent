package tech.qiantong.qknow.hermes.flow.pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.pattern.EvaluatorOptimizerEngine.*;
import tech.qiantong.qknow.hermes.flow.pattern.OrchestratorWorkersEngine.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Anthropic 核心 Agent 工作流模式契约测试
 * 验证：Evaluator-Optimizer 自反思评分收敛与最大轮次熔断、Orchestrator-Workers 虚拟线程并发派发与容错聚合
 */
public class AnthropicCorePatternsTest {

    private EvaluatorOptimizerEngine evaluatorOptimizer;
    private OrchestratorWorkersEngine orchestratorWorkers;

    @BeforeEach
    public void setup() {
        evaluatorOptimizer = new EvaluatorOptimizerEngine();
        orchestratorWorkers = new OrchestratorWorkersEngine();
    }

    @Test
    @DisplayName("测试 Evaluator-Optimizer：两轮自反思优化收敛达标并退出")
    public void testEvaluatorOptimizerConvergesSuccessfully() {
        String taskPrompt = "编写一个高可用分布式状态机类，要求包含租约自愈机制。";
        String rubric = "必须实现租约心跳超时检测与 fencing token 防并发写冲突。";

        AtomicInteger genCount = new AtomicInteger(0);

        // 模拟生成器：第一轮方案缺少 fencing token，第二轮吸收评估反馈补全
        GeneratorFunction generator = (task, prevSolution, lastFeedback) -> {
            int round = genCount.incrementAndGet();
            if (round == 1) {
                return "class SimpleStateMachine { void heartbeat() {} }";
            } else {
                return "class HighAvailabilityStateMachine { void heartbeat() {} long fencingToken; boolean checkFencing() { return true; } }";
            }
        };

        // 模拟评估器：第一轮打 60 分并给出修改项；第二轮打 95 分判定通过
        EvaluatorFunction evaluator = (task, currentSolution, rub) -> {
            if (currentSolution.contains("fencingToken")) {
                return EvaluationResult.pass(95, "完全符合评分准则，包含 fencing token 写屏障与租约机制。");
            } else {
                return EvaluationResult.fail(60, "缺少 fencing token 并发控制机制。", List.of("增加 fencingToken 字段", "增加 checkFencing 校验方法"));
            }
        };

        OptimizationRunResult result = evaluatorOptimizer.optimize(
                taskPrompt,
                rubric,
                85, // 目标分 85
                3,  // 最大 3 轮
                generator,
                evaluator
        );

        // 验证：
        // 1. 成功收敛达标
        assertTrue(result.success());
        assertEquals("REACHED_TARGET_SCORE", result.terminationReason());

        // 2. 总共执行 2 轮即收敛，没有浪费第 3 轮
        assertEquals(2, result.totalIterations());
        assertEquals(95, result.finalScore());
        assertTrue(result.finalSolution().contains("fencingToken"));

        // 3. 历史记录完整
        assertEquals(2, result.iterationHistory().size());
        assertEquals(60, result.iterationHistory().get(0).evaluation().score());
        assertEquals(95, result.iterationHistory().get(1).evaluation().score());
    }

    @Test
    @DisplayName("测试 Evaluator-Optimizer：无法达标时受最大轮次熔断保护，杜绝死循环")
    public void testEvaluatorOptimizerMaxIterationsGuard() {
        String taskPrompt = "解决停机问题。";
        String rubric = "必须给出图灵机停机问题的封闭形式解析解。";

        // 模拟生成器：永远无法给出符合苛刻准则的答案
        GeneratorFunction generator = (task, prevSolution, lastFeedback) -> "当前数学理论证明停机问题不可判定。";

        // 模拟苛刻评估器：永远打 40 分
        EvaluatorFunction evaluator = (task, currentSolution, rub) -> EvaluationResult.fail(40, "未给出确定性解法", List.of("请重试"));

        OptimizationRunResult result = evaluatorOptimizer.optimize(
                taskPrompt,
                rubric,
                90, // 目标分 90
                3,  // 最大 3 轮
                generator,
                evaluator
        );

        // 验证：触发最大迭代轮次熔断
        assertFalse(result.success());
        assertEquals("MAX_ITERATIONS_REACHED", result.terminationReason());
        assertEquals(3, result.totalIterations());
        assertEquals(40, result.finalScore());
    }

    @Test
    @DisplayName("测试 Orchestrator-Workers：并发派发多子任务并汇总")
    public void testOrchestratorWorkersExecution() {
        String rootTask = "企业级 AI 知识库与智能体上线前全链路综合体检。";

        // 1. 拆解为 3 个子任务
        OrchestratorPlanner planner = task -> List.of(
                new SubTask("task-kb", "知识库索引检索验证", "KB_WORKER", Map.of("topK", 10)),
                new SubTask("task-mcp", "远程 MCP 工具安全性扫描", "MCP_WORKER", Map.of("sandbox", true)),
                new SubTask("task-perf", "长程长文本上下文重凝缩压测", "PERF_WORKER", Map.of("rounds", 20))
        );

        // 2. 并行执行每个 Worker
        WorkerExecutor worker = subTask -> {
            Thread.sleep(20); // 模拟耗时操作
            return switch (subTask.taskId()) {
                case "task-kb" -> Map.of("recallRate", 0.98, "status", "HEALTHY");
                case "task-mcp" -> Map.of("scannedTools", 15, "violations", 0);
                case "task-perf" -> Map.of("compressionRatio", 0.35, "cacheHitRate", 0.94);
                default -> Map.of("result", "OK");
            };
        };

        // 3. 汇总成果
        AggregatorReducer reducer = (task, outputs) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("体检报告综合结论：全部 ").append(outputs.size()).append(" 项子任务执行完毕。\n");
            outputs.forEach((id, out) -> sb.append("- [").append(id).append("]: ").append(out.output()).append("\n"));
            return sb.toString();
        };

        OrchestratedExecutionResult result = orchestratorWorkers.execute(rootTask, planner, worker, reducer);

        // 验证：
        // 1. 全部子任务成功
        assertTrue(result.allSucceeded());
        assertEquals(3, result.workerOutputs().size());

        // 2. 包含每个子任务的具体输出
        assertTrue(result.workerOutputs().containsKey("task-kb"));
        assertTrue(result.workerOutputs().containsKey("task-mcp"));
        assertTrue(result.workerOutputs().containsKey("task-perf"));
        assertEquals(0.98, result.workerOutputs().get("task-kb").output().get("recallRate"));

        // 3. 汇总结果正常生成
        assertNotNull(result.summary());
        assertTrue(result.summary().contains("全部 3 项子任务执行完毕"));
    }

    @Test
    @DisplayName("测试 Orchestrator-Workers：单任务异常时故障隔离，保留其余成功结果")
    public void testOrchestratorWorkersPartialFailureIsolation() {
        String rootTask = "多源数据采集与同步。";

        OrchestratorPlanner planner = task -> List.of(
                new SubTask("task-1", "正常任务1", "WORKER_A", Map.of()),
                new SubTask("task-2", "异常崩溃任务", "WORKER_B", Map.of()),
                new SubTask("task-3", "正常任务2", "WORKER_C", Map.of())
        );

        WorkerExecutor worker = subTask -> {
            if ("task-2".equals(subTask.taskId())) {
                throw new RuntimeException("第三方远程连接超时中断");
            }
            return Map.of("dataCount", 100);
        };

        AggregatorReducer reducer = (task, outputs) -> "汇总完成";

        OrchestratedExecutionResult result = orchestratorWorkers.execute(rootTask, planner, worker, reducer);

        // 验证：
        // 1. 标记整体未全成功
        assertFalse(result.allSucceeded());

        // 2. task-1 和 task-3 正常保留并成功
        assertTrue(result.workerOutputs().get("task-1").success());
        assertTrue(result.workerOutputs().get("task-3").success());

        // 3. task-2 记录了错误信息且未导致主流程崩溃
        assertFalse(result.workerOutputs().get("task-2").success());
        assertTrue(result.workerOutputs().get("task-2").errorMessage().contains("第三方远程连接超时中断"));
    }
}
