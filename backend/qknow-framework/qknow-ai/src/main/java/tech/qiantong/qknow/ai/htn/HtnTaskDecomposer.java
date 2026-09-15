package tech.qiantong.qknow.ai.htn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 分层任务网络递归分解器 (HTN Task Decomposer)
 * 基于良基序势能约束将复合任务递归展开为原子动作，
 * 利用 Kahn 拓扑排序算法进行循环依赖检测，强制 D_max <= 5 深度截断 (定理 1.1)
 */
@Component
public class HtnTaskDecomposer {

    private static final Logger log = LoggerFactory.getLogger(HtnTaskDecomposer.class);

    /** 最大递归展开深度 D_max = 5 (防止堆栈溢出) */
    public static final int MAX_DECOMPOSITION_DEPTH = 5;

    /**
     * HTN 任务模型 (复合任务或原子任务)
     */
    public record HtnTask(
            String taskId,
            String taskName,
            boolean isPrimitive,
            String parentTaskId,
            List<String> dependsOnTaskIds,
            int depth,
            List<HtnTask> subtasks
    ) {
        public HtnTask(String taskId, String taskName, boolean isPrimitive, String parentTaskId, List<String> dependsOnTaskIds, int depth) {
            this(taskId, taskName, isPrimitive, parentTaskId, dependsOnTaskIds, depth, List.of());
        }
    }

    /**
     * 拓扑有序的分解执行计划
     */
    public record DecomposedPlan(
            String rootTaskId,
            List<HtnTask> orderedPrimitiveTasks,
            int maxDepth,
            String planTopologyHash
    ) {}

    /**
     * 递归分解复合任务为原子动作拓扑有序序列
     */
    public DecomposedPlan decompose(HtnTask rootTask) {
        if (rootTask == null) {
            throw new IllegalArgumentException("根任务不能为空");
        }

        List<HtnTask> allPrimitiveTasks = new ArrayList<>();
        int observedMaxDepth = 0;

        // 1. 递归展开任务树
        Deque<HtnTask> stack = new ArrayDeque<>();
        stack.push(rootTask);

        while (!stack.isEmpty()) {
            HtnTask current = stack.pop();
            observedMaxDepth = Math.max(observedMaxDepth, current.depth());

            if (current.depth() > MAX_DECOMPOSITION_DEPTH) {
                log.error("HTN 递归深度超限: 任务 [{}] 达到深度 {} > {}", current.taskId(), current.depth(), MAX_DECOMPOSITION_DEPTH);
                throw new IllegalStateException("HTN 递归分解超出最大允许深度 " + MAX_DECOMPOSITION_DEPTH);
            }

            if (current.isPrimitive()) {
                allPrimitiveTasks.add(current);
            } else {
                List<HtnTask> children = current.subtasks();
                if (children != null && !children.isEmpty()) {
                    for (int i = children.size() - 1; i >= 0; i--) {
                        stack.push(children.get(i));
                    }
                } else {
                    // 若无子任务却标记为复合任务，视作原子动作
                    allPrimitiveTasks.add(new HtnTask(current.taskId(), current.taskName(), true, current.parentTaskId(), current.dependsOnTaskIds(), current.depth()));
                }
            }
        }

        // 2. Kahn 算法拓扑排序与循环依赖检测
        List<HtnTask> ordered = kahnTopologicalSort(allPrimitiveTasks);

        String planHash = computePlanHash(ordered);
        return new DecomposedPlan(rootTask.taskId(), Collections.unmodifiableList(ordered), observedMaxDepth, planHash);
    }

    /**
     * Kahn 拓扑排序算法 (无环有向图验证与全序序列生成)
     */
    public List<HtnTask> kahnTopologicalSort(List<HtnTask> tasks) {
        Map<String, HtnTask> taskMap = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adjList = new LinkedHashMap<>();

        for (HtnTask t : tasks) {
            taskMap.put(t.taskId(), t);
            inDegree.put(t.taskId(), 0);
            adjList.put(t.taskId(), new ArrayList<>());
        }

        for (HtnTask t : tasks) {
            if (t.dependsOnTaskIds() != null) {
                for (String depId : t.dependsOnTaskIds()) {
                    if (taskMap.containsKey(depId)) {
                        adjList.get(depId).add(t.taskId());
                        inDegree.put(t.taskId(), inDegree.get(t.taskId()) + 1);
                    }
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<HtnTask> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String currId = queue.poll();
            sorted.add(taskMap.get(currId));

            for (String neighbor : adjList.get(currId)) {
                int deg = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, deg);
                if (deg == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != tasks.size()) {
            // 入度未全清零，存在环路死锁
            List<String> cyclicNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cyclicNodes.add(entry.getKey());
                }
            }
            log.error("Kahn 算法检测到循环依赖环路，涉案节点: {}", cyclicNodes);
            throw new IllegalArgumentException("检测到 HTN 任务前后置循环依赖死锁: " + cyclicNodes);
        }

        return sorted;
    }

    private String computePlanHash(List<HtnTask> tasks) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (HtnTask t : tasks) {
                sb.append(t.taskId()).append("->");
            }
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            return "PLAN_HASH_" + tasks.size();
        }
    }
}
