package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.StepLifecycleStatus;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.WriteAheadStepJournalEntry;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierCommitDecision;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.service.flow.compiler.KbFlowStateGraphCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 博士论文代际升级 · 阶段 1（论文第 6 章内核）核心契约测试套件
 * 覆盖：
 * 1. DBOS 风格预写步骤日志（WriteAheadStepJournalEntry）崩溃幂等重放与 DeltaChannel 时间旅行分叉；
 * 2. KbFlowStateGraphCompiler 有界反思/检索回边编译与无界死锁环拦截；
 * 3. 定理 6.1：Epoch-Frontier 三级副作用隔离、严格纪元序可串行化、零不可逆投机泄漏与租约 TTL 无死锁自愈。
 */
class Phase1PhdDurableFrontierEngineContractTest {

    @Test
    @DisplayName("1.1 嵌入式 Step Journal：崩溃重启后已提交步骤幂等零重复执行，且 DeltaChannel 时间旅行分叉零污染")
    void test01_StepJournalIdempotentCrashReplayAndDeltaForkIsolation() {
        PgStepJournalStateCheckpointer checkpointer = new PgStepJournalStateCheckpointer(null, "phd-journal-hmac-secret", 4);
        AtomicInteger expensiveLlmCalls = new AtomicInteger(0);

        // 第 1 次执行超步 1 的节点：真正触发动作并持久化 COMMITTED 步骤日志
        Map<String, Object> out1 = checkpointer.executeOrReplayIdempotentStep(
                "exec-dbos-01", "main", 1, "node_retrieve", "idem-key-step1", 10L,
                Map.of("query", 1L),
                () -> {
                    expensiveLlmCalls.incrementAndGet();
                    return Map.of("retrievedDocs", List.of("docA", "docB"), "step1Done", true);
                }
        );
        assertEquals(1, expensiveLlmCalls.get());
        assertEquals(true, out1.get("step1Done"));

        // 模拟第 2 超步中途进程崩溃重启，重新重放超步 1 同一幂等键：必须直接从 Journal 旁路返回，调用计数保持为 1！
        Map<String, Object> replayedOut1 = checkpointer.executeOrReplayIdempotentStep(
                "exec-dbos-01", "main", 1, "node_retrieve", "idem-key-step1", 11L,
                Map.of("query", 1L),
                () -> {
                    expensiveLlmCalls.incrementAndGet();
                    return Map.of("retrievedDocs", List.of("WRONG_REEXECUTION"), "step1Done", false);
                }
        );
        assertEquals(1, expensiveLlmCalls.get(), "已提交的幂等步骤在崩溃恢复重放时严禁重复触发外部 API！");
        assertEquals(List.of("docA", "docB"), replayedOut1.get("retrievedDocs"));

        // 验证步骤凭单常量时间验签与防篡改
        List<WriteAheadStepJournalEntry> entries = checkpointer.listJournalEntries("exec-dbos-01", "main");
        assertEquals(1, entries.size());
        assertEquals(StepLifecycleStatus.COMMITTED, entries.get(0).status());
        assertTrue(entries.get(0).verifyMac("phd-journal-hmac-secret"), "步骤日志 MAC 常量时间校验必须通过");
        assertFalse(entries.get(0).verifyMac("wrong-secret"), "错误密钥验签必须失败");

        // 继续推进超步 2，随后从超步 1 执行因果时间旅行分叉（Causal Time-Travel Fork）
        checkpointer.executeOrReplayIdempotentStep(
                "exec-dbos-01", "main", 2, "node_synthesize", "idem-key-step2", 12L,
                Map.of("retrievedDocs", 1L),
                () -> Map.of("answer", "主分支初始回答", "confidence", 0.62)
        );

        String forkBranchId = checkpointer.forkBranchFromJournal(
                "exec-dbos-01", "main", 1, Map.of("queryRewrite", "强化超球面约束子查询")
        );
        Map<String, Object> mainState = checkpointer.reconstructStateAt("exec-dbos-01", "main", 2);
        Map<String, Object> forkState = checkpointer.reconstructStateAt("exec-dbos-01", forkBranchId, 1);

        assertEquals("主分支初始回答", mainState.get("answer"));
        assertFalse(mainState.containsKey("queryRewrite"), "分叉覆盖严禁反向污染主分支状态（零幽灵变量泄漏）");
        assertFalse(forkState.containsKey("answer"), "从超步 1 分叉的分支严禁携带主分支超步 2 的未来状态");
        assertEquals("强化超球面约束子查询", forkState.get("queryRewrite"));
        assertEquals(true, forkState.get("step1Done"), "分叉分支必须完整继承超步 1 的已提交增量");
    }

    @Test
    @DisplayName("1.2 KbFlowStateGraphCompiler：支持有界反思回边编译执行，同时精确阻断无上限死锁环路")
    void test02_CyclicWorkflowCompilerSupportsBoundedReflectionAndRejectsUnboundedDeadlock() {
        PgStepJournalStateCheckpointer checkpointer = new PgStepJournalStateCheckpointer(null, "compiler-secret", 4);
        DurableStateGraphEngine engine = new DurableStateGraphEngine.DefaultDurableStateGraphEngine(checkpointer);
        KbFlowStateGraphCompiler compiler = new KbFlowStateGraphCompiler();

        KbFlowNodeDO start = KbFlowNodeDO.builder().uuid("n_start").name("开始").type(1).config("{}").build();
        KbFlowNodeDO draft = KbFlowNodeDO.builder().uuid("n_draft").name("生成草稿").type(2).config("{}").build();
        KbFlowNodeDO critic = KbFlowNodeDO.builder().uuid("n_critic").name("批判审查").type(3)
                .config("{\"maxLoopIterations\": 3, \"passKey\": \"criticPassed\"}").build();
        KbFlowNodeDO reply = KbFlowNodeDO.builder().uuid("n_reply").name("最终回复").type(4).config("{}").build();

        // 构造含反思回边 (n_critic -> n_draft) 的有环工作流图
        List<KbFlowEdgeDO> cyclicEdges = List.of(
                KbFlowEdgeDO.builder().sourceNodeUuid("n_start").targetNodeUuid("n_draft").build(),
                KbFlowEdgeDO.builder().sourceNodeUuid("n_draft").targetNodeUuid("n_critic").build(),
                KbFlowEdgeDO.builder().sourceNodeUuid("n_critic").targetNodeUuid("n_draft").sourceHandle("retry").build(),
                KbFlowEdgeDO.builder().sourceNodeUuid("n_critic").targetNodeUuid("n_reply").sourceHandle("pass").build()
        );

        AtomicInteger draftAttempts = new AtomicInteger(0);
        KbFlowStateGraphCompiler.CompiledFlowExecution execution = compiler.compileAndRegister(
                "flow-cyclic-01",
                List.of(start, draft, critic, reply),
                cyclicEdges,
                engine,
                (node, state) -> switch (node.getUuid()) {
                    case "n_start" -> Map.of("question", "证明定理6.1");
                    case "n_draft" -> {
                        int attempt = draftAttempts.incrementAndGet();
                        yield Map.of("draftAttempt", attempt, "draftText", "第" + attempt + "版证明");
                    }
                    case "n_critic" -> {
                        int attempt = (int) state.getOrDefault("draftAttempt", 0);
                        // 第 2 次尝试才通过审查
                        yield Map.of("criticPassed", attempt >= 2);
                    }
                    case "n_reply" -> Map.of("finalAnswer", state.get("draftText"));
                    default -> Map.of();
                }
        );

        Map<String, Object> finalState = compiler.runToCompletion(execution, "exec-cyclic-01", Map.of(), 12);
        assertEquals(2, draftAttempts.get(), "有界反思回边应精确循环 2 次后跳出至终态回复节点");
        assertEquals("第2版证明", finalState.get("finalAnswer"));

        // 反例验证：未声明 maxLoopIterations 的裸死循环必须被编译器静态拦截
        KbFlowNodeDO unsafeCritic = KbFlowNodeDO.builder().uuid("n_critic").name("无界节点").type(3).config("{}").build();
        assertThrows(IllegalArgumentException.class, () -> compiler.compileAndRegister(
                "flow-deadlock",
                List.of(start, draft, unsafeCritic, reply),
                cyclicEdges,
                engine,
                (n, s) -> Map.of()
        ), "缺少有界循环上限的环路必须在编译期被拒绝");
    }

    @Test
    @DisplayName("1.3 定理 6.1：Epoch-Frontier 严格纪元序可串行化、零不可逆投机泄漏与慢节点 TTL 吊销自愈")
    void test03_Theorem6_1_EpochFrontierSerializabilityZeroIrreversibleLeakageAndTtlLiveness() {
        FrontierGatedToolTransactionManager txManager = new FrontierGatedToolTransactionManager("frontier-hmac-key");
        AtomicInteger irreversibleExternalCalls = new AtomicInteger(0);
        List<String> compensationTrace = new ArrayList<>();

        // 1. 派生两个共享外部资源 "db://ledger/account_1" 的并发/投机分支：T1 (epoch=1) 与 T2 (epoch=2, 投机分支)
        var branch1 = txManager.openBranch("branch-1", Set.of("db://ledger/account_1"), false, 1.0, 50L);
        var branch2 = txManager.openBranch("branch-2-spec", Set.of("db://ledger/account_1"), true, 0.92, 5000L);
        assertTrue(branch1.epoch() < branch2.epoch(), "后派生分支必须获得严格更大的逻辑纪元 e(T1) < e(T2)");

        // 2. 零不可逆投机泄漏验证：投机分支 T2 尝试触发 IRREVERSIBLE 物理副作用，必须在执行前被硬挂起拦截！
        FrontierCommitDecision irrevDecision = txManager.executeGatedTool(
                "branch-2-spec",
                "prod_webhook_dispatch",
                "db://ledger/account_1",
                ToolSideEffectTier.IRREVERSIBLE,
                false,
                () -> {
                    irreversibleExternalCalls.incrementAndGet();
                    return Map.of("sent", true);
                },
                null
        );
        assertInstanceOf(FrontierCommitDecision.SuspendedForHitlApproval.class, irrevDecision);
        assertEquals(0, irreversibleExternalCalls.get(), "投机分支触发的不可逆物理副作用执行次数必须恒等于 0！");

        // 3. 外部化可逆级动作执行与逆序补偿验证：T2 执行 2 步 EXTERNALIZED_REVERSIBLE 动作
        txManager.executeGatedTool(
                "branch-2-spec", "stage_row_1", "db://ledger/account_1",
                ToolSideEffectTier.EXTERNALIZED_REVERSIBLE, false,
                () -> Map.of("staged", 1),
                () -> compensationTrace.add("undo_row_1")
        );
        txManager.executeGatedTool(
                "branch-2-spec", "stage_row_2", "db://ledger/account_1",
                ToolSideEffectTier.EXTERNALIZED_REVERSIBLE, false,
                () -> Map.of("staged", 2),
                () -> compensationTrace.add("undo_row_2")
        );

        // 4. 严格纪元序提交门禁：当 T1 (epoch=1) 尚未终结时，frontier(r)=1 < e(T2)=2，T2 请求提交必须被排队延迟！
        FrontierCommitDecision earlyCommitT2 = txManager.tryCommitBranch("branch-2-spec");
        assertInstanceOf(FrontierCommitDecision.DeferredInQueue.class, earlyCommitT2,
                "前序纪元分支未结算前，后序冲突分支严禁越序提交！");

        // 5. 有界租约 TTL 活性自愈验证：模拟慢节点 T1 崩溃超时，守护器自动吊销 T1 并推进资源水位线至 T2！
        List<FrontierCommitDecision.LeaseRecoveredFromCrash> recovered =
                txManager.reapExpiredBranches(branch1.openedAtEpochMs() + 200L);
        assertEquals(1, recovered.size(), "超时的前序纪元慢分支必须被确定性回收");
        assertEquals(branch2.epoch(), txManager.getResourceFrontier("db://ledger/account_1"),
                "慢节点回收后资源水位线必须严格单调推进至下一活跃纪元 e(T2)");

        // 6. 此时若 T2 被 HITL 驳回中止，必须按严格逆拓扑序执行外部补偿链 (undo_row_2 -> undo_row_1) 并归入三级冷备环！
        txManager.abortBranch("branch-2-spec", "HITL 审查驳回投机分支");
        assertEquals(List.of("undo_row_2", "undo_row_1"), compensationTrace,
                "中止分支必须按严格逆日志序 c_k^{-1} ... c_1^{-1} 执行幂等补偿");
        assertEquals(2, txManager.getColdRingArchivedCount(),
                "超时分支与中止分支均必须归档至三级冷备可逆环形缓冲区（零物理硬删除）");

        // 7. 陈旧纪元写屏障验证：已中止的旧纪元若发生网络延迟重放提交，必须 100% 返回 RejectedStaleEpoch
        FrontierCommitDecision staleAttempt = txManager.tryCommitWithExplicitEpoch(
                "branch-1", "db://ledger/account_1", branch1.epoch()
        );
        assertInstanceOf(FrontierCommitDecision.RejectedStaleEpoch.class, staleAttempt);
    }
}
