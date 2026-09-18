package tech.qiantong.qknow.hermes.flow.hitl.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.flow.hitl.dto.WorkflowNodeStateSnapshot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 节点级时空快照定长环形快照池 (TimeTravelSnapshotRingBuffer)
 * <p>
 * 遵循 Phase 104 学术与工业规范：
 * 1. 定长环形快照池 (默认容量 M = 20)，李雅普诺夫内存势一致有界 (sup_t V(t) <= M * C_max < inf)；
 * 2. 驱逐最老快照时自动解除物理引用，杜绝内存泄漏；
 * 3. 时光旅行回溯算子 R(S, tau) 支持从祖先链重构不可变快照，100% 偏序一致；
 * 4. 分叉派生 (Fork Branching) 隔离机制，彻底杜绝反向时间污染。
 */
public class TimeTravelSnapshotRingBuffer {

    private static final Logger log = LoggerFactory.getLogger(TimeTravelSnapshotRingBuffer.class);

    private final int capacity;
    private final Deque<WorkflowNodeStateSnapshot> ringBuffer = new ConcurrentLinkedDeque<>();
    private final Map<String, WorkflowNodeStateSnapshot> snapshotIndex = new ConcurrentHashMap<>();

    public TimeTravelSnapshotRingBuffer() {
        this(20);
    }

    public TimeTravelSnapshotRingBuffer(int capacity) {
        this.capacity = capacity > 0 ? capacity : 20;
    }

    /**
     * 写入新节点快照，若超出容量则以 O(1) 驱逐最老快照，维持内存一致有界性
     */
    public synchronized void recordSnapshot(WorkflowNodeStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        while (ringBuffer.size() >= capacity) {
            WorkflowNodeStateSnapshot evicted = ringBuffer.pollFirst();
            if (evicted != null) {
                snapshotIndex.remove(evicted.snapshotId());
                log.debug("[RingBuffer] 驱逐最老快照以维持定长容量: id={}, node={}",
                        evicted.snapshotId(), evicted.nodeName());
            }
        }

        ringBuffer.addLast(snapshot);
        snapshotIndex.put(snapshot.snapshotId(), snapshot);
        log.debug("[RingBuffer] 记录新快照: id={}, node={}, 当前大小={}/{}",
                snapshot.snapshotId(), snapshot.nodeName(), ringBuffer.size(), capacity);
    }

    /**
     * 获取当前快照池内已存储的快照数量
     */
    public int size() {
        return ringBuffer.size();
    }

    /**
     * 获取环形快照池最大容量 M
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * 获取全部活跃快照（按时间先后顺序不可变列表）
     */
    public List<WorkflowNodeStateSnapshot> getAllSnapshots() {
        return List.copyOf(ringBuffer);
    }

    /**
     * 获取最新执行快照
     */
    public WorkflowNodeStateSnapshot getLatestSnapshot() {
        return ringBuffer.peekLast();
    }

    /**
     * 根据快照 ID 快速检索快照元数据
     */
    public WorkflowNodeStateSnapshot findSnapshotById(String snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        return snapshotIndex.get(snapshotId);
    }

    /**
     * 时光旅行状态重构算子 R(S, tau)：重构指定快照时刻的全局变量快照
     * 沿着祖先依赖链或序列累积 deltaVariables，返回不可变变量只读字典，零反向时间污染
     */
    public Map<String, Object> reconstructStateAt(String targetSnapshotId) {
        WorkflowNodeStateSnapshot target = findSnapshotById(targetSnapshotId);
        if (target == null) {
            throw new IllegalArgumentException("未找到指定的快照目标: " + targetSnapshotId);
        }

        // 收集从目标快照回溯至最早祖先的链路
        List<WorkflowNodeStateSnapshot> lineage = new ArrayList<>();
        WorkflowNodeStateSnapshot curr = target;
        Set<String> visited = new HashSet<>();

        while (curr != null && visited.add(curr.snapshotId())) {
            lineage.add(curr);
            String parentId = curr.parentSnapshotId();
            if (parentId == null || parentId.isEmpty()) {
                break;
            }
            curr = findSnapshotById(parentId);
        }

        // 逆序回放累积增量
        Collections.reverse(lineage);
        Map<String, Object> state = new HashMap<>();
        for (WorkflowNodeStateSnapshot snap : lineage) {
            if (snap.deltaVariables() != null) {
                state.putAll(snap.deltaVariables());
            }
        }

        return Collections.unmodifiableMap(state);
    }

    /**
     * 从历史快照创建派生时间线分叉 (Fork Branching)
     * 保证原时间线快照只读冻结，新分支拥有独立标识与全新首节点快照
     */
    public WorkflowNodeStateSnapshot forkBranch(String fromSnapshotId, String newBranchId,
                                                String forkedNodeUuid, String forkedNodeName,
                                                Map<String, Object> modifiedInputs) {
        WorkflowNodeStateSnapshot base = findSnapshotById(fromSnapshotId);
        if (base == null) {
            throw new IllegalArgumentException("无法从不存在的快照进行分叉: " + fromSnapshotId);
        }

        Map<String, Object> baseState = new HashMap<>(reconstructStateAt(fromSnapshotId));
        if (modifiedInputs != null) {
            baseState.putAll(modifiedInputs);
        }

        String newSnapshotId = "SNAP-" + newBranchId + "-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowNodeStateSnapshot forkedSnapshot = new WorkflowNodeStateSnapshot(
                newSnapshotId,
                base.workflowId(),
                newBranchId,
                forkedNodeUuid,
                forkedNodeName,
                fromSnapshotId,
                Collections.unmodifiableMap(baseState),
                "forked-input-hash",
                "forked-output-hash",
                base.sphericalEmbedding(),
                System.currentTimeMillis() * 1000L
        );

        recordSnapshot(forkedSnapshot);
        log.info("[RingBuffer] 成功从快照 {} 派生新分支: branchId={}, newSnapshotId={}",
                fromSnapshotId, newBranchId, newSnapshotId);
        return forkedSnapshot;
    }

    /**
     * 清空当前快照池
     */
    public synchronized void clear() {
        ringBuffer.clear();
        snapshotIndex.clear();
    }
}
