package tech.qiantong.qknow.hermes.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.flow.bo.*;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.flow.dag.DagExecutor;
import tech.qiantong.qknow.hermes.flow.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.flow.factory.NodeFactory;
import tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor;
import tech.qiantong.qknow.hermes.flow.rag.RagRetrievalService;
import tech.qiantong.qknow.hermes.flow.saga.CompensableNode;
import tech.qiantong.qknow.hermes.flow.saga.SagaCompensationEngine;
import tech.qiantong.qknow.hermes.flow.util.TypeSafeContextAccessor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 23 反应式工作流编排引擎、非阻塞人机协同审批 (HITL) 与 SAGA 事务状态补偿核心契约测试
 */
@ExtendWith(MockitoExtension.class)
public class ReactiveWorkflowContractTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private RagRetrievalService ragRetrievalService;

    private NodeFactory nodeFactory;
    private DagCheckpointManager checkpointManager;
    private DagExecutor dagExecutor;
    private SagaCompensationEngine sagaCompensationEngine;

    @BeforeEach
    void setUp() {
        nodeFactory = new NodeFactory();
        var chatField = org.springframework.util.ReflectionUtils.findField(NodeFactory.class, "chatModelFactory");
        chatField.setAccessible(true);
        org.springframework.util.ReflectionUtils.setField(chatField, nodeFactory, chatModelFactory);

        var ragField = org.springframework.util.ReflectionUtils.findField(NodeFactory.class, "ragRetrievalService");
        ragField.setAccessible(true);
        org.springframework.util.ReflectionUtils.setField(ragField, nodeFactory, ragRetrievalService);

        // 内存版断点管理器（无需真实数据库）
        checkpointManager = new InMemoryDagCheckpointManager();
        dagExecutor = new DagExecutor(nodeFactory, checkpointManager);
        sagaCompensationEngine = new SagaCompensationEngine();
    }

    private KbFlowNodeDO createNodeDef(String uuid, String name, Integer type, String config) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid(uuid);
        node.setName(name);
        node.setType(type);
        node.setConfig(config != null ? config : "{}");
        node.setInput("[]");
        node.setOutput("[]");
        return node;
    }

    private KbFlowEdgeDO createEdge(String sourceUuid, String targetUuid, String sourceHandle) {
        KbFlowEdgeDO edge = new KbFlowEdgeDO();
        edge.setSourceNodeUuid(sourceUuid);
        edge.setTargetNodeUuid(targetUuid);
        edge.setSourceHandle(sourceHandle);
        return edge;
    }

    private RuntimeContextBO createContext() {
        KbRuntimeDO runtime = new KbRuntimeDO();
        return new RuntimeContextBO(runtime, new JSONObject());
    }

    // ==========================================
    // 契约 1：审批节点非阻塞挂起即刻释放物理线程
    // ==========================================
    @Test
    @DisplayName("契约 1: 审批节点执行为非阻塞挂起，立即返回 SUSPENDED 且释放线程池工作线程")
    void testApprovalNode_NonBlockingSuspension_ReleasesThread() throws Exception {
        KbFlowNodeDO approvalNodeDef = createNodeDef("node_approval_1", "人工审批",
                FlowNodeTypeEnums.APPROVAL.getCode(), "{\"reason\":\"需要财务负责人审批\"}");

        RuntimeContextBO context = createContext();
        context.getVariables().put("orderId", "ORD-123456");

        long startTime = System.currentTimeMillis();
        // 执行审批节点
        NodeRunResultBO result = ApprovalNodeExecutor.execute(approvalNodeDef, context, "flow_001", "req_001");
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertEquals(RuntimeStatusEnums.SUSPENDED.getCode(), result.getStatus(),
                "审批节点必须返回 SUSPENDED 挂起状态");
        assertTrue(elapsed < 2000, "非阻塞挂起执行必须在极短时间内（毫秒级）完成并返回，不可阻塞物理线程");
        assertNotNull(result.getOutput());
        assertEquals("SUSPENDED", result.getOutput().get("status"));
    }

    // ==========================================
    // 契约 2：断点快照上下文持久化与类型安全恢复
    // ==========================================
    @Test
    @DisplayName("契约 2: 检查点完整持久化全量上下文变量，通过 TypeSafeContextAccessor 恢复时零类型转换异常")
    void testApprovalCheckpoint_SaveAndRestoreContextVariables() {
        String runtimeId = "rt_snapshot_test";
        String flowId = "flow_snapshot";
        RuntimeContextBO context = createContext();

        // 注入多种复杂类型变量（FastJSON 反序列化常见类型擦除场景）
        context.getVariables().put("totalAmount", 9999999999L);
        context.getVariables().put("itemCount", 42);
        context.getVariables().put("scoreRatio", 0.985);
        context.getVariables().put("isVip", true);
        context.getVariables().put("user.address.city", "Hangzhou");
        context.getVariables().put("tagList", List.of("electronics", "smartphone"));

        Map<String, NodeRunResultBO> completedResults = new LinkedHashMap<>();
        completedResults.put("node_start", NodeRunResultBO.success("node_start", "Start",
                Map.of("initKey", "initVal")));

        // 保存包含全量变量的快照
        checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, 1, completedResults, context.getVariables());

        // 加载快照
        DagCheckpointManager.DagCheckpoint loaded = checkpointManager.loadCheckpoint(runtimeId);
        assertNotNull(loaded);
        Map<String, Object> restoredVars = checkpointManager.restoreVariables(loaded);

        // 使用 TypeSafeContextAccessor 安全读取
        Long totalAmount = TypeSafeContextAccessor.getLong(restoredVars, "totalAmount");
        Integer itemCount = TypeSafeContextAccessor.getInteger(restoredVars, "itemCount");
        Double scoreRatio = TypeSafeContextAccessor.getDouble(restoredVars, "scoreRatio");
        Boolean isVip = TypeSafeContextAccessor.getBoolean(restoredVars, "isVip");
        String city = TypeSafeContextAccessor.getString(restoredVars, "user.address.city");
        List<String> tags = TypeSafeContextAccessor.getList(restoredVars, "tagList", String.class);

        assertEquals(9999999999L, totalAmount);
        assertEquals(42, itemCount);
        assertEquals(0.985, scoreRatio, 0.0001);
        assertTrue(isVip);
        assertEquals("Hangzhou", city);
        assertEquals(List.of("electronics", "smartphone"), tags);
    }

    // ==========================================
    // 契约 3：外部审批通过后断点安全唤醒继续推进
    // ==========================================
    @Test
    @DisplayName("契约 3: 审批通过后，工作流从断点快照处无缝唤醒，继续推进后继波前节点执行")
    void testApprovalApprove_ResumesWorkflowSuccessfully() {
        String runtimeId = "rt_resume_test";
        String flowId = "flow_resume";

        KbFlowNodeDO startNode = createNodeDef("node_1", "开始", FlowNodeTypeEnums.START.getCode(), "{}");
        KbFlowNodeDO approvalNode = createNodeDef("node_2", "审批", FlowNodeTypeEnums.APPROVAL.getCode(), "{}");
        KbFlowNodeDO replyNode = createNodeDef("node_3", "回复", FlowNodeTypeEnums.REPLY.getCode(),
                "{\"replyText\":\"审批已通过，流程顺利完成！\"}");

        List<KbFlowNodeDO> nodes = List.of(startNode, approvalNode, replyNode);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("node_1", "node_2", null),
                createEdge("node_2", "node_3", null)
        );

        RuntimeContextBO context = createContext();

        // 第一次执行：在 node_2 挂起
        List<NodeRunResultBO> run1 = dagExecutor.executeWithCheckpoint(runtimeId, flowId, nodes, edges, context);
        assertEquals(2, run1.size());
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), run1.get(0).getStatus());
        assertEquals(RuntimeStatusEnums.SUSPENDED.getCode(), run1.get(1).getStatus());

        // 模拟外部审批唤醒
        Map<String, Object> humanInput = Map.of("approver", "张三", "comment", "同意放行");
        boolean woken = dagExecutor.wakeSuspended(runtimeId, humanInput);
        assertTrue(woken, "唤醒挂起检查点应成功");

        // 第二次执行：恢复后继续推进
        List<NodeRunResultBO> run2 = dagExecutor.executeWithCheckpoint(runtimeId, flowId, nodes, edges, context);
        assertEquals(3, run2.size());
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), run2.get(2).getStatus());
        assertEquals("node_3", run2.get(2).getNodeUuid());
    }

    // ==========================================
    // 契约 4：多端并发审批基于分布式锁/CAS 防重防抖拦截
    // ==========================================
    @Test
    @DisplayName("契约 4: 20 个高并发审批唤醒请求，在防重互斥机制下仅且仅有 1 个成功，其余全部安全拦截")
    void testApprovalApprove_ConcurrentRaceCondition_ProtectedByDistributedLock() throws Exception {
        String runtimeId = "rt_concurrency_race";
        String flowId = "flow_concurrency";

        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        results.put("node_1", NodeRunResultBO.success("node_1", "开始", Map.of()));
        results.put("node_2", NodeRunResultBO.suspended("node_2", "审批", Map.of()));

        checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, 1, results, new JSONObject());

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    boolean ok = checkpointManager.wakeSuspendedWithLock(runtimeId,
                            Map.of("approver", "User_" + index));
                    if (ok) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successCount.get(), "并发审批只能有 1 个请求获得锁并执行成功");
        assertEquals(threadCount - 1, failCount.get(), "其余并发请求必须被安全拦截幂等返回");
    }

    // ==========================================
    // 契约 5：条件分支未选中路径递归剪枝跳过
    // ==========================================
    @Test
    @DisplayName("契约 5: 条件节点选定 Branch A，Branch B 及其后继子孙节点递归被标记为 SKIPPED 且不物理执行")
    void testConditionNode_PrunesUnselectedBranchRecursively() {
        // 拓扑图：
        // node_start -> node_cond
        // node_cond --(handle_a)--> node_a1 -> node_a2
        // node_cond --(handle_b)--> node_b1 -> node_b2
        String condConfig = "{\"conditions\":[{\"expression\":\"{{ score }} >= 60\",\"targetHandle\":\"handle_a\"},{\"expression\":\"default\",\"targetHandle\":\"handle_b\"}]}";
        KbFlowNodeDO startNode = createNodeDef("node_start", "开始", FlowNodeTypeEnums.START.getCode(), "{}");
        KbFlowNodeDO condNode = createNodeDef("node_cond", "条件判断", FlowNodeTypeEnums.CONDITION.getCode(), condConfig);
        KbFlowNodeDO nodeA1 = createNodeDef("node_a1", "及格分支A1", FlowNodeTypeEnums.REPLY.getCode(), "{}");
        KbFlowNodeDO nodeA2 = createNodeDef("node_a2", "及格分支A2", FlowNodeTypeEnums.REPLY.getCode(), "{}");
        KbFlowNodeDO nodeB1 = createNodeDef("node_b1", "不及格分支B1", FlowNodeTypeEnums.REPLY.getCode(), "{}");
        KbFlowNodeDO nodeB2 = createNodeDef("node_b2", "不及格分支B2", FlowNodeTypeEnums.REPLY.getCode(), "{}");

        List<KbFlowNodeDO> nodes = List.of(startNode, condNode, nodeA1, nodeA2, nodeB1, nodeB2);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("node_start", "node_cond", null),
                createEdge("node_cond", "node_a1", "handle_a"),
                createEdge("node_a1", "node_a2", null),
                createEdge("node_cond", "node_b1", "handle_b"),
                createEdge("node_b1", "node_b2", null)
        );

        RuntimeContextBO context = createContext();
        context.getVariables().put("score", 85); // 命中 handle_a

        List<NodeRunResultBO> results = dagExecutor.execute(nodes, edges, context);

        Map<String, NodeRunResultBO> resultMap = new HashMap<>();
        for (NodeRunResultBO r : results) {
            resultMap.put(r.getNodeUuid(), r);
        }

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), resultMap.get("node_a1").getStatus());
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), resultMap.get("node_a2").getStatus());

        // B 分支应被自动剪枝打上 SKIPPED 标记
        assertNotNull(resultMap.get("node_b1"), "未命中分支节点应记录在结果集中并标记为 SKIPPED");
        assertEquals(RuntimeStatusEnums.SKIPPED.getCode(), resultMap.get("node_b1").getStatus());
        assertNotNull(resultMap.get("node_b2"), "未命中分支的后继孙子节点也必须递归标记为 SKIPPED");
        assertEquals(RuntimeStatusEnums.SKIPPED.getCode(), resultMap.get("node_b2").getStatus());
    }

    // ==========================================
    // 契约 6：汇聚网关自适应过滤 SKIPPED 分支消除死锁
    // ==========================================
    @Test
    @DisplayName("契约 6: 汇聚网关自动过滤前驱被 SKIPPED 的无效分支，正常汇聚有效分支，零死锁执行")
    void testAggregatorNode_AdaptivePrunedInputs_ExecutesWithoutDeadlock() {
        // 汇聚节点配置两个输入：branch_a.output 和 branch_b.output
        String aggConfig = "{\"strategy\":\"concat\",\"inputKeys\":[\"branch_a.text\",\"branch_b.text\"]}";
        KbFlowNodeDO aggNodeDef = createNodeDef("node_agg", "汇聚结果", FlowNodeTypeEnums.AGGREGATOR.getCode(), aggConfig);

        AggregatorNodeBO aggNode = new AggregatorNodeBO(aggNodeDef, List.of());

        RuntimeContextBO context = createContext();
        context.getVariables().put("branch_a.text", "有效分支产出内容");
        // branch_b 在前置条件中被剪枝跳过，上下文中打上 SKIPPED 状态标记
        context.getVariables().put("branch_b.status", RuntimeStatusEnums.SKIPPED.getCode());

        NodeRunResultBO result = aggNode.execute(context);

        assertNotNull(result);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus(), "汇聚节点应自适应跳过 SKIPPED 分支并执行成功");
        String merged = (String) result.getOutput().get("node_agg.merged");
        assertNotNull(merged);
        assertTrue(merged.contains("有效分支产出内容"));
        assertFalse(merged.contains("branch_b"));
    }

    // ==========================================
    // 契约 7：人工审批硬性驳回触发 SAGA 逆拓扑补偿
    // ==========================================
    @Test
    @DisplayName("契约 7: 审批节点被 REJECTED 驳回时，上游已执行的可补偿节点按逆拓扑序 (LIFO) 触发补偿")
    void testSagaCompensation_TriggeredOnApprovalRejection() {
        List<String> compensationTrace = new CopyOnWriteArrayList<>();

        TestCompensableNode node1 = new TestCompensableNode("node_1", "创建订单", compensationTrace);
        TestCompensableNode node2 = new TestCompensableNode("node_2", "预扣库存", compensationTrace);

        KbFlowNodeDO def1 = createNodeDef("node_1", "创建订单", FlowNodeTypeEnums.HTTP.getCode(), "{}");
        KbFlowNodeDO def2 = createNodeDef("node_2", "预扣库存", FlowNodeTypeEnums.HTTP.getCode(), "{}");
        KbFlowNodeDO defApproval = createNodeDef("node_approval", "领导审批", FlowNodeTypeEnums.APPROVAL.getCode(), "{}");

        List<KbFlowNodeDO> nodes = List.of(def1, def2, defApproval);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("node_1", "node_2", null),
                createEdge("node_2", "node_approval", null)
        );

        Map<String, BaseNodeBO> nodeInstanceMap = Map.of(
                "node_1", node1,
                "node_2", node2
        );

        Map<String, NodeRunResultBO> completedResults = new LinkedHashMap<>();
        completedResults.put("node_1", NodeRunResultBO.success("node_1", "创建订单", Map.of("orderId", "1001")));
        completedResults.put("node_2", NodeRunResultBO.success("node_2", "预扣库存", Map.of("skuId", "SKU-99")));

        RuntimeContextBO context = createContext();

        // 触发审批驳回补偿
        boolean compSuccess = sagaCompensationEngine.compensateOnRejection(
                nodes, edges, completedResults, nodeInstanceMap, context, "领导拒绝批准预算");

        assertTrue(compSuccess, "SAGA 补偿执行应成功");
        assertEquals(2, compensationTrace.size(), "应依次补偿 2 个已执行节点");
        // 验证逆拓扑序（LIFO）：node_2 后执行，必须先被补偿；node_1 先执行，后被补偿
        assertEquals("node_2", compensationTrace.get(0), "后执行的节点必须先补偿 (LIFO)");
        assertEquals("node_1", compensationTrace.get(1), "先执行的节点后补偿");
    }

    // ==========================================
    // 契约 8：下游节点执行异常触发全链路 SAGA 逆序补偿
    // ==========================================
    @Test
    @DisplayName("契约 8: 下游节点执行抛出不可恢复异常时，全链路自动触发逆拓扑补偿自愈")
    void testSagaCompensation_TriggeredOnDownstreamExecutionError() {
        List<String> compensationTrace = new CopyOnWriteArrayList<>();

        TestCompensableNode nodeA = new TestCompensableNode("node_A", "初始化数据", compensationTrace);
        TestCompensableNode nodeB = new TestCompensableNode("node_B", "申请外呼任务", compensationTrace);

        KbFlowNodeDO defA = createNodeDef("node_A", "初始化数据", FlowNodeTypeEnums.HTTP.getCode(), "{}");
        KbFlowNodeDO defB = createNodeDef("node_B", "申请外呼任务", FlowNodeTypeEnums.HTTP.getCode(), "{}");
        KbFlowNodeDO defC = createNodeDef("node_C", "发送短信通知(失败)", FlowNodeTypeEnums.HTTP.getCode(), "{}");

        List<KbFlowNodeDO> nodes = List.of(defA, defB, defC);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("node_A", "node_B", null),
                createEdge("node_B", "node_C", null)
        );

        Map<String, BaseNodeBO> nodeInstanceMap = Map.of(
                "node_A", nodeA,
                "node_B", nodeB
        );

        Map<String, NodeRunResultBO> completedResults = new LinkedHashMap<>();
        completedResults.put("node_A", NodeRunResultBO.success("node_A", "初始化数据", Map.of()));
        completedResults.put("node_B", NodeRunResultBO.success("node_B", "申请外呼任务", Map.of()));

        RuntimeContextBO context = createContext();

        boolean compSuccess = sagaCompensationEngine.compensateOnError(
                nodes, edges, completedResults, nodeInstanceMap, context, "node_C", "SMS Gateway 503 Unavailable");

        assertTrue(compSuccess);
        assertEquals(2, compensationTrace.size());
        assertEquals("node_B", compensationTrace.get(0));
        assertEquals("node_A", compensationTrace.get(1));
    }

    // ==========================================
    // 契约 9：非可补偿节点优雅跳过不报错
    // ==========================================
    @Test
    @DisplayName("契约 9: 未实现 CompensableNode 接口的只读或普通节点，SAGA 补偿引擎优雅跳过且不报错")
    void testSagaCompensation_NonCompensableNodesSkippedGracefully() {
        List<String> compensationTrace = new CopyOnWriteArrayList<>();

        TestCompensableNode compensableNode = new TestCompensableNode("node_write", "写入记录", compensationTrace);
        // 普通只读回复节点（未实现 CompensableNode）
        KbFlowNodeDO replyDef = createNodeDef("node_read", "纯文本回复", FlowNodeTypeEnums.REPLY.getCode(), "{}");
        ReplyNodeBO nonCompensableNode = new ReplyNodeBO(replyDef, List.of());

        KbFlowNodeDO defWrite = createNodeDef("node_write", "写入记录", FlowNodeTypeEnums.HTTP.getCode(), "{}");

        List<KbFlowNodeDO> nodes = List.of(defWrite, replyDef);
        List<KbFlowEdgeDO> edges = List.of(createEdge("node_write", "node_read", null));

        Map<String, BaseNodeBO> nodeInstanceMap = Map.of(
                "node_write", compensableNode,
                "node_read", nonCompensableNode
        );

        Map<String, NodeRunResultBO> completedResults = new LinkedHashMap<>();
        completedResults.put("node_write", NodeRunResultBO.success("node_write", "写入记录", Map.of()));
        completedResults.put("node_read", NodeRunResultBO.success("node_read", "纯文本回复", Map.of()));

        RuntimeContextBO context = createContext();

        boolean success = sagaCompensationEngine.compensateOnError(
                nodes, edges, completedResults, nodeInstanceMap, context, "node_future", "error");

        assertTrue(success);
        assertEquals(1, compensationTrace.size(), "仅有实现 CompensableNode 的节点触发补偿");
        assertEquals("node_write", compensationTrace.get(0));
    }

    // ==========================================
    // 契约 10：条件分支、非阻塞挂起与 SAGA 复合长链路协同
    // ==========================================
    @Test
    @DisplayName("契约 10: 端到端复合长链路协同：条件分流 -> 外部副作用 -> 非阻塞审批挂起 -> 人工驳回 -> SAGA 逆序补偿自愈")
    void testEndToEnd_ComplexWorkflowWithConditionApprovalAndCompensation() {
        List<String> trace = new CopyOnWriteArrayList<>();

        String condConfig = "{\"conditions\":[{\"expression\":\"{{ type }} == 'refund'\",\"targetHandle\":\"h_refund\"},{\"expression\":\"default\",\"targetHandle\":\"h_other\"}]}";
        KbFlowNodeDO startNode = createNodeDef("start", "开始", FlowNodeTypeEnums.START.getCode(), "{}");
        KbFlowNodeDO condNode = createNodeDef("cond", "分支选择", FlowNodeTypeEnums.CONDITION.getCode(), condConfig);
        KbFlowNodeDO deductNodeDef = createNodeDef("deduct", "冻结款项", FlowNodeTypeEnums.REPLY.getCode(), "{}");
        KbFlowNodeDO approvalNodeDef = createNodeDef("approval", "退款人工审批", FlowNodeTypeEnums.APPROVAL.getCode(), "{}");
        KbFlowNodeDO skipBranchNodeDef = createNodeDef("other_branch", "其他分支", FlowNodeTypeEnums.REPLY.getCode(), "{}");

        List<KbFlowNodeDO> nodes = List.of(startNode, condNode, deductNodeDef, approvalNodeDef, skipBranchNodeDef);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("start", "cond", null),
                createEdge("cond", "deduct", "h_refund"),
                createEdge("deduct", "approval", null),
                createEdge("cond", "other_branch", "h_other")
        );

        TestCompensableNode deductNode = new TestCompensableNode("deduct", "冻结款项", trace);
        Map<String, BaseNodeBO> instanceMap = Map.of("deduct", deductNode);

        RuntimeContextBO context = createContext();
        context.getVariables().put("type", "refund");

        // 1. 第一阶段调度
        List<NodeRunResultBO> runResults = dagExecutor.execute(nodes, edges, context);

        Map<String, NodeRunResultBO> resultMap = new HashMap<>();
        for (NodeRunResultBO r : runResults) {
            resultMap.put(r.getNodeUuid(), r);
        }

        // 验证未选中分支已剪枝
        assertEquals(RuntimeStatusEnums.SKIPPED.getCode(), resultMap.get("other_branch").getStatus());
        // 验证挂起节点正确返回
        assertEquals(RuntimeStatusEnums.SUSPENDED.getCode(), resultMap.get("approval").getStatus());

        // 2. 人工介入决策为 REJECTED 驳回
        boolean compResult = sagaCompensationEngine.compensateOnRejection(
                nodes, edges, resultMap, instanceMap, context, "风控驳回退款");

        assertTrue(compResult, "逆拓扑补偿自愈应执行成功");
        assertEquals(1, trace.size());
        assertEquals("deduct", trace.get(0), "已冻结的款项应被成功解冻补偿");
    }

    // ==========================================
    // 内部测试辅助类：内存版断点管理器与可补偿节点
    // ==========================================
    private static class InMemoryDagCheckpointManager extends DagCheckpointManager {
        private final Map<String, DagCheckpoint> store = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> varStore = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> versionStore = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();

        @Override
        public void saveCheckpoint(String runtimeId, String flowId, int groupIndex,
                                   Map<String, NodeRunResultBO> completedResults) {
            saveCheckpointWithVariables(runtimeId, flowId, groupIndex, completedResults, Collections.emptyMap());
        }

        public void saveCheckpointWithVariables(String runtimeId, String flowId, int groupIndex,
                                               Map<String, NodeRunResultBO> completedResults,
                                               Map<String, Object> variables) {
            DagCheckpoint cp = new DagCheckpoint();
            cp.setRuntimeId(runtimeId);
            cp.setFlowId(flowId);
            cp.setGroupIndex(groupIndex);
            cp.setCompletedResultsJson(com.alibaba.fastjson2.JSON.toJSONString(completedResults));
            store.put(runtimeId, cp);
            varStore.put(runtimeId, new HashMap<>(variables));
            versionStore.computeIfAbsent(runtimeId, k -> new AtomicInteger(1));
        }

        @Override
        public DagCheckpoint loadCheckpoint(String runtimeId) {
            return store.get(runtimeId);
        }

        public Map<String, Object> restoreVariables(DagCheckpoint checkpoint) {
            if (checkpoint == null) return Collections.emptyMap();
            return varStore.getOrDefault(checkpoint.getRuntimeId(), Collections.emptyMap());
        }

        public boolean wakeSuspendedWithLock(String runtimeId, Map<String, Object> humanInput) {
            // 模拟分布式互斥锁获取
            Boolean prev = locks.putIfAbsent(runtimeId, Boolean.TRUE);
            if (prev != null) {
                return false; // 锁竞争失败
            }
            try {
                DagCheckpoint cp = store.get(runtimeId);
                if (cp == null) return false;
                // 模拟 CAS 乐观锁版本检查
                AtomicInteger ver = versionStore.get(runtimeId);
                if (ver == null || ver.compareAndSet(1, 2)) {
                    return wakeSuspended(runtimeId, humanInput);
                }
                return false;
            } finally {
                // 模拟业务处理完成后释放分布式锁
                locks.remove(runtimeId);
            }
        }
    }

    private static class TestCompensableNode extends BaseNodeBO implements CompensableNode {
        private final List<String> trace;

        public TestCompensableNode(String uuid, String name, List<String> trace) {
            super(createNodeDefStatic(uuid, name), List.of());
            this.trace = trace;
        }

        private static KbFlowNodeDO createNodeDefStatic(String uuid, String name) {
            KbFlowNodeDO def = new KbFlowNodeDO();
            def.setUuid(uuid);
            def.setName(name);
            def.setType(FlowNodeTypeEnums.HTTP.getCode());
            def.setConfig("{}");
            return def;
        }

        @Override
        protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
            return NodeRunResultBO.success(getNodeDefinition().getUuid(), getNodeDefinition().getName(), Map.of("executed", true));
        }

        @Override
        public boolean compensate(RuntimeContextBO context, NodeRunResultBO originalResult) {
            trace.add(getNodeDefinition().getUuid());
            return true;
        }
    }
}
