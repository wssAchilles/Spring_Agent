package tech.qiantong.qknow.ai.skill;

import java.util.*;

/**
 * Phase 62: 神经符号技能 DAG 拓扑编译与分层执行引擎
 * <p>
 * 基于定理 1.2，利用 Kahn 算法排查循环依赖，生成拓扑分层执行计划，死锁发生率严格为 0。
 */
public class NeuroSymbolicSkillDagEngine {

    public record PhasedExecutionPlan(
            List<List<String>> phases,
            int totalSkills,
            int maxParallelism
    ) {}

    /**
     * 静态排查循环死锁并生成拓扑分阶段执行计划
     */
    public PhasedExecutionPlan compileDagPlan(List<SkillMetadata> skills) {
        if (skills == null || skills.isEmpty()) {
            return new PhasedExecutionPlan(List.of(), 0, 0);
        }

        Set<String> skillIds = new HashSet<>();
        Map<String, Set<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (SkillMetadata skill : skills) {
            String id = skill.skillId();
            skillIds.add(id);
            adjacencyList.putIfAbsent(id, new HashSet<>());
            inDegree.putIfAbsent(id, 0);
        }

        // 构建依赖图 (dependency -> targetSkill)
        for (SkillMetadata skill : skills) {
            String targetId = skill.skillId();
            for (String depId : skill.dependencies()) {
                if (skillIds.contains(depId)) {
                    adjacencyList.get(depId).add(targetId);
                    inDegree.merge(targetId, 1, Integer::sum);
                }
            }
        }

        // Kahn 算法分层推进 (BFS)
        Queue<String> queue = new LinkedList<>();
        for (String id : skillIds) {
            if (inDegree.get(id) == 0) {
                queue.offer(id);
            }
        }

        List<List<String>> phases = new ArrayList<>();
        int visitedCount = 0;
        int maxParallelism = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            maxParallelism = Math.max(maxParallelism, levelSize);
            List<String> currentPhase = new ArrayList<>();

            for (int i = 0; i < levelSize; i++) {
                String u = queue.poll();
                currentPhase.add(u);
                visitedCount++;

                for (String v : adjacencyList.get(u)) {
                    int remaining = inDegree.merge(v, -1, Integer::sum);
                    if (remaining == 0) {
                        queue.offer(v);
                    }
                }
            }
            phases.add(currentPhase);
        }

        // 若访问节点数不等于总节点数，证明依赖图中存在环路死锁！
        if (visitedCount != skillIds.size()) {
            throw new IllegalStateException("检测到技能依赖存在环形循环死锁 (Cyclic Dependency)，Kahn 拓扑编译阻断！");
        }

        return new PhasedExecutionPlan(phases, visitedCount, maxParallelism);
    }
}
