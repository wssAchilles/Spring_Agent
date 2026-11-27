package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.SupernetControlPlaneDispatcher;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.BridgeContractGate;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.TypedSubgraphExecutionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.DynamicSendPacket;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.NodeExecutionEnvelope;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.StandardStateReducers;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.StateChannelDefinition;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.StateGraphSchema;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.TypedChannelKey;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.2 契约单测：PA-TSG 前缀锚定隔离上下文类型化并行子图引擎 + R2 桥接门控与算子控制面。
 */
class Phase9_2TypedParallelSubgraphContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void sameTargetMultiInstanceFanoutPreservesAllPayloadsAndReducesDeterministically() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine engine = new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);

        TypedChannelKey<List> factCardsKey = TypedChannelKey.of("factCards", List.class);
        StateGraphSchema schema = StateGraphSchema.builder()
                .channel(StateChannelDefinition.of("factCards", StandardStateReducers.appendList(), List.of()))
                .channel(StateChannelDefinition.of("totalScore", StandardStateReducers.arithmeticSumLong(), 0L))
                .channel(StateChannelDefinition.of("finalAnswer", StandardStateReducers.overwrite(), ""))
                .build();
        engine.setStateGraphSchema(schema);

        engine.registerScopedNode(
                "planner",
                Set.of("question"),
                state -> true,
                view -> {
                    // 向同一个 worker 节点并行扇出 4 个不同 payload
                    List<DynamicSendPacket> sends = List.of(
                            DynamicSendPacket.of("hop_worker", Map.of("subQuery", "sub-A", "sleepMs", 40L)),
                            DynamicSendPacket.of("hop_worker", Map.of("subQuery", "sub-B", "sleepMs", 5L)),
                            DynamicSendPacket.of("hop_worker", Map.of("subQuery", "sub-C", "sleepMs", 25L)),
                            DynamicSendPacket.of("hop_worker", Map.of("subQuery", "sub-D", "sleepMs", 1L))
                    );
                    return NodeExecutionEnvelope.of("planner", Map.of("totalScore", 1L), sends);
                },
                null,
                List.of()
        );

        engine.registerScopedNode(
                "hop_worker",
                Set.of("question"),
                state -> true,
                view -> {
                    String subQ = String.valueOf(view.sendPayload().get("subQuery"));
                    long sleepMs = ((Number) view.sendPayload().get("sleepMs")).longValue();
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return NodeExecutionEnvelope.of(
                            "hop_worker",
                            Map.of(
                                    "factCards", List.of("card:" + subQ),
                                    "totalScore", 10L
                            ),
                            List.of()
                    );
                },
                (reducedState, lastEnv) -> List.of(DynamicSendPacket.of("joiner", Map.of())),
                List.of()
        );

        engine.registerScopedNode(
                "joiner",
                Set.of("factCards", "totalScore"),
                state -> true,
                view -> {
                    List<String> cards = factCardsKey.readFrom(view.readableChannelsSnapshot());
                    return NodeExecutionEnvelope.of("joiner", Map.of("finalAnswer", String.join("|", cards)));
                },
                null,
                List.of()
        );

        engine.setZone0PrefixAnchor("exec-fanout", "main", "SYSTEM_ANCHOR_64_TOKEN_INVARIANT_HEADER_BLOCK");
        engine.initializeExecution("exec-fanout", "main", Map.of("question", "MuSiQue 4-hop"), List.of("planner"));

        TypedSubgraphExecutionReceipt receipt = engine.executeToCompletion("exec-fanout", "main", 8);
        assertTrue(receipt.completed());
        assertTrue(receipt.zone0AnchorPreserved());
        assertEquals(4, receipt.maxParallelWidth(), "4 个并发同节点扇出不可被 LinkedHashSet 折叠");

        // 超步 1: planner (1), 超步 2: 4 个 hop_worker (+40 score, 4 条有序 card), 超步 3: 4 次路由到 joiner（或汇聚）
        var lastStep = engine.advanceSuperstep("exec-fanout", "main");
        assertTrue(lastStep.completed());
        // 即使 sub-D 比 sub-A 快完成 40ms，归约顺序也严格按照确定性 dispatchPathKey (sub-A -> sub-B -> sub-C -> sub-D)
        String answer = String.valueOf(lastStep.mutatedState().get("finalAnswer"));
        assertTrue(answer.startsWith("card:sub-A|card:sub-B|card:sub-C|card:sub-D"),
                "确定性路径归约序被破坏: " + answer);
        assertEquals(41L, ((Number) lastStep.mutatedState().get("totalScore")).longValue());
    }

    @Test
    void scopedNodeIsolatesPrivateDirtyChannelsAndPreservesZone0Sha256() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine engine = new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);
        engine.setStateGraphSchema(StateGraphSchema.builder()
                .channel(StateChannelDefinition.of("dirtySecretPassage", StandardStateReducers.overwrite(), ""))
                .channel(StateChannelDefinition.of("verifiedFact", StandardStateReducers.overwrite(), ""))
                .channel(StateChannelDefinition.of("leakedCount", StandardStateReducers.arithmeticSumLong(), 0L))
                .build());

        AtomicReference<String> seenZone0 = new AtomicReference<>("");
        String anchor = "IMMUTABLE_DEEPSEEK_ZONE0_PREFIX_BLOCK_ALIGNMENT_V4";

        engine.registerScopedNode(
                "nodeA",
                Set.of("question"),
                state -> true,
                view -> NodeExecutionEnvelope.of("nodeA", Map.of(
                        "dirtySecretPassage", "RAW_NOISY_HTML_CHUNK_12KB",
                        "verifiedFact", "Fact-42"
                )),
                null,
                List.of("nodeB")
        );

        engine.registerScopedNode(
                "nodeB",
                Set.of("verifiedFact"), // 仅授权读取精炼 verifiedFact，严禁看到 dirtySecretPassage
                state -> true,
                view -> {
                    seenZone0.set(view.zone0AnchorPrefix());
                    assertTrue(view.isZone0AnchorPreserved(ScopedNodeContextViewSnapshot(anchor)));
                    assertFalse(view.readableChannelsSnapshot().containsKey("dirtySecretPassage"),
                            "节点私有隔离视角绝不能泄露兄弟分支未授权的原始脏日志");
                    assertEquals("Fact-42", view.get(TypedChannelKey.of("verifiedFact", String.class)));
                    return NodeExecutionEnvelope.of("nodeB", Map.of("leakedCount", 0L));
                },
                null,
                List.of()
        );

        engine.setZone0PrefixAnchor("exec-iso", "main", anchor);
        engine.initializeExecution("exec-iso", "main", Map.of("question", "Q"), List.of("nodeA"));
        TypedSubgraphExecutionReceipt rcpt = engine.executeToCompletion("exec-iso", "main", 5);
        assertTrue(rcpt.completed());
        assertTrue(rcpt.zone0AnchorPreserved());
        assertEquals(anchor, seenZone0.get());
    }

    private static String ScopedNodeContextViewSnapshot(String s) {
        return tech.qiantong.qknow.hermes.flow.stategraph.engine.ScopedNodeContextView.computeSha256(s);
    }

    @Test
    void conditionalRouterBlocksUnmatchedBranchesAndExceptionsDoNotDeadlock() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine engine = new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);
        AtomicInteger unreachableVisits = new AtomicInteger(0);

        engine.registerScopedNode(
                "routerNode",
                null,
                state -> true,
                view -> NodeExecutionEnvelope.of("routerNode", Map.of("route", "LEFT")),
                (state, env) -> "LEFT".equals(state.get("route"))
                        ? List.of(DynamicSendPacket.of("leftLeaf", Map.of()))
                        : List.of(DynamicSendPacket.of("rightLeaf", Map.of())),
                List.of("rightLeaf") // 即便 defaultOutbound 写了 rightLeaf，有 ConditionalRouter 时也必须按条件拦截
        );
        engine.registerScopedNode("leftLeaf", null, state -> true,
                view -> NodeExecutionEnvelope.of("leftLeaf", Map.of("done", true)), null, List.of());
        engine.registerScopedNode("rightLeaf", null, state -> true,
                view -> {
                    unreachableVisits.incrementAndGet();
                    return NodeExecutionEnvelope.of("rightLeaf", Map.of());
                }, null, List.of());

        engine.initializeExecution("exec-cond", "main", Map.of(), List.of("routerNode"));
        TypedSubgraphExecutionReceipt condRcpt = engine.executeToCompletion("exec-cond", "main", 5);
        assertTrue(condRcpt.completed());
        assertEquals(0, unreachableVisits.get(), "ConditionalEdgeRouter 必须精确阻断未命中静态出边");

        // 异常结构化返回，绝不死锁
        engine.registerScopedNode("boomNode", null, state -> true, view -> {
            throw new IllegalStateException("simulated tool crash");
        }, null, List.of());
        engine.initializeExecution("exec-boom", "main", Map.of(), List.of("boomNode"));
        var boomRes = engine.advanceSuperstep("exec-boom", "main");
        assertEquals("ERR_NODE_EXECUTION_FAILED", boomRes.errorCode());
        assertTrue(boomRes.message().contains("simulated tool crash"));

        // 可配超时精确返回 ERR_SUPERSTEP_TIMEOUT
        engine.setSuperstepTimeout(Duration.ofMillis(60));
        engine.registerScopedNode("slowNode", null, state -> true, view -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return NodeExecutionEnvelope.of("slowNode", Map.of());
        }, null, List.of());
        engine.initializeExecution("exec-slow", "main", Map.of(), List.of("slowNode"));
        var slowRes = engine.advanceSuperstep("exec-slow", "main");
        assertEquals("ERR_SUPERSTEP_TIMEOUT", slowRes.errorCode());
    }

    @Test
    void r2BridgeContractGateRejectsHallucinationsAndBoundsSpeculativeForkWidthToTwo() {
        BridgeContractGate gate = new BridgeContractGate();
        float[] slotVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("mother of Arthur Balfour", 1024);
        float[] c1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Blanche Balfour mother family", 1024);
        float[] c2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Mary Balfour mother relative", 1024);
        float[] c3 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("quantum chromodynamics", 1024);

        // 1. 幻觉实体不在观测原文中 -> 拒签
        var hallucination = gate.evaluateBridge(
                "mother of Arthur Balfour",
                List.of("Fabricated Person"),
                List.of(c1),
                slotVec,
                "Arthur Balfour was born in Scotland in 1848.",
                0.25, 0.08
        );
        assertFalse(hallucination.passed());
        assertEquals("R2_BRIDGE_GROUNDING_OR_COSINE_REJECTED", hallucination.failureReason());

        // 2. 歧义双桥接候选 -> 触发宽度严格 <= 2 的有界投机分叉
        var fork = gate.evaluateBridge(
                "mother of Arthur Balfour",
                List.of("Blanche Balfour", "Mary Balfour", "Unrelated Quantum"),
                List.of(c1, c2, c3),
                slotVec,
                "Arthur Balfour, son of Blanche Balfour and nephew of Mary Balfour, visited Edinburgh.",
                -0.20, 0.50
        );
        assertTrue(fork.passed());
        assertTrue(fork.speculativeForkTriggered());
        assertEquals(2, fork.selectedBridgeEntities().size());
    }

    @Test
    void controlPlaneAndBudgetGateTypedSubgraphOperatorCleanly() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("sec", 42L);
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        SupernetControlPlaneDispatcher dispatcher = new SupernetControlPlaneDispatcher(router, resolver);
        HnscdsAblationConfig g12 = HnscdsAblationConfig.g12V4Subgraph(42L);
        HnscdsAblationConfig g10 = HnscdsAblationConfig.g10HnscdsV3Ultimate(42L);

        // 在未启用 typedParallelSubgraphEnabled 时，BudgetVector6D 也能正确降档或控制面门禁阻断误分发
        BudgetVector6D tightBudget = new BudgetVector6D(2000L, 1500L, 4, 10000L, 0.01, 2);
        var downgrade = tightBudget.enforceStaticBudget(
                SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE,
                DeepSeekReasoningEffort.HIGH,
                1600L,
                3
        );
        assertTrue(downgrade.budgetDowngraded());
        assertEquals(SupernetTopologyOperator.DIRECT_FAST, downgrade.resolvedOperator());

        var d12 = dispatcher.dispatch("req-12", "3-hop parallel subgraph bridge decomposition", 800L, 3, BudgetVector6D.defaults(), g12);
        var d10 = dispatcher.dispatch("req-10", "3-hop parallel subgraph bridge decomposition", 800L, 3, BudgetVector6D.defaults(), g10);
        assertNotNull(d12.effectiveOperator());
        assertNotEquals(SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE, d10.effectiveOperator(),
                "G10 未开启 Phase 9.2 开关时绝对不能分发至 TYPED_SUBGRAPH_MAP_REDUCE");
    }
}
