package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.BatchIterationSubgraphEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchIterationSubgraphEngine 批处理迭代子图引擎测试")
class BatchIterationSubgraphEngineTest {

    private BatchIterationSubgraphEngine iterationEngine;
    private StateGraphContext context;

    @BeforeEach
    void setUp() {
        iterationEngine = new BatchIterationSubgraphEngine(1024, 16);
        context = new StateGraphContext("exec-subgraph-1", "flow-subgraph-1", 10);
    }

    @Test
    @DisplayName("乱序延迟并发完成下，槽位保序聚拢率严格达到 100.0%")
    void subgraph_orderedMapReduce_preservesIndex() {
        int count = 100;
        List<String> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add("item-" + i);
        }

        // 模拟各分片存在 0~15ms 差异化延迟的乱序处理
        List<NodeRunResultBO> results = iterationEngine.executeMapReduce(
                items,
                context,
                (item, itemIndex, ctx) -> {
                    long sleepMs = ThreadLocalRandom.current().nextLong(0, 15);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ignored) {}

                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid("sub-node-" + itemIndex);
                    bo.setOutput(Map.of("processedItem", item.toUpperCase(), "originalIndex", itemIndex));
                    return bo;
                },
                true // tolerant
        );

        assertNotNull(results);
        assertEquals(count, results.size(), "输出列表长度必须与输入完全一致");

        // 严格检验每一个槽位的下标与原输入是否 100% 保序对齐
        for (int i = 0; i < count; i++) {
            NodeRunResultBO bo = results.get(i);
            assertNotNull(bo, "槽位 " + i + " 不能为 null");
            Integer origIdx = (Integer) bo.getOutput().get("originalIndex");
            String processed = (String) bo.getOutput().get("processedItem");
            assertEquals(i, origIdx, "槽位 " + i + " 索引必须保序");
            assertEquals("ITEM-" + i, processed, "处理结果必须对齐");
        }
    }

    @Test
    @DisplayName("输入超过 1024 上限拦截，抛出异常防止 OOM 事故")
    void subgraph_exceedMaxLength_throwsException() {
        List<String> hugeList = new ArrayList<>();
        for (int i = 0; i < 1025; i++) {
            hugeList.add("data-" + i);
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                iterationEngine.executeMapReduce(
                        hugeList,
                        context,
                        (item, idx, ctx) -> new NodeRunResultBO(),
                        true
                )
        );

        assertTrue(ex.getMessage().contains("1024"), "异常信息必须包含上限数字提示");
    }

    @Test
    @DisplayName("背压信号量限制生效，瞬时最大并发受控")
    void subgraph_concurrencyThrottle_respectsSemaphore() {
        int concurrencyLimit = 4;
        BatchIterationSubgraphEngine throttledEngine = new BatchIterationSubgraphEngine(1024, concurrencyLimit);

        AtomicInteger runningWorkers = new AtomicInteger(0);
        AtomicInteger maxObservedWorkers = new AtomicInteger(0);

        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            inputs.add(i);
        }

        throttledEngine.executeMapReduce(
                inputs,
                context,
                (item, idx, ctx) -> {
                    int current = runningWorkers.incrementAndGet();
                    maxObservedWorkers.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {}
                    runningWorkers.decrementAndGet();

                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid("node-" + idx);
                    return bo;
                },
                true
        );

        assertTrue(maxObservedWorkers.get() <= concurrencyLimit,
                "观察到的最大并发数 " + maxObservedWorkers.get() + " 不得超过信号量限制 " + concurrencyLimit);
    }
}
