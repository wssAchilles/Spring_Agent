package tech.qiantong.qknow.hermes.flow.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DagUtils 测试")
class DagUtilsTest {

    // ========== 辅助方法 ==========

    private KbFlowNodeDO node(String uuid) {
        KbFlowNodeDO n = new KbFlowNodeDO();
        n.setUuid(uuid);
        return n;
    }

    private KbFlowEdgeDO edge(String source, String target) {
        KbFlowEdgeDO e = new KbFlowEdgeDO();
        e.setSourceNodeUuid(source);
        e.setTargetNodeUuid(target);
        return e;
    }

    // ========== 拓扑排序 ==========

    @Test
    @DisplayName("线性 DAG 拓扑排序正确：A→B→C 排序为 [A,B,C]")
    void topologicalSort_linearDag_returnsCorrectOrder() {
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"));
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("B", "C"));

        List<String> sorted = DagUtils.topologicalSort(nodes, edges);

        assertEquals(List.of("A", "B", "C"), sorted);
    }

    @Test
    @DisplayName("分叉 DAG 拓扑排序正确")
    void topologicalSort_forkDag_returnsValidOrder() {
        // A → B, A → C, B → D, C → D
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"), node("D"));
        List<KbFlowEdgeDO> edges = List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("C", "D")
        );

        List<String> sorted = DagUtils.topologicalSort(nodes, edges);

        assertEquals(4, sorted.size());
        assertEquals("A", sorted.get(0));
        assertEquals("D", sorted.get(3));
        assertTrue(sorted.indexOf("B") < sorted.indexOf("D"));
        assertTrue(sorted.indexOf("C") < sorted.indexOf("D"));
    }

    @Test
    @DisplayName("有环 DAG 抛出 IllegalStateException")
    void topologicalCycle_throwsIllegalState() {
        // A → B → C → A（环）
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"));
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"));

        assertThrows(IllegalStateException.class, () -> DagUtils.topologicalSort(nodes, edges));
    }

    @Test
    @DisplayName("空节点列表返回空排序")
    void topologicalSort_emptyNodes_returnsEmptyList() {
        List<String> sorted = DagUtils.topologicalSort(Collections.emptyList(), Collections.emptyList());

        assertNotNull(sorted);
        assertTrue(sorted.isEmpty());
    }

    // ========== getParallelGroups ==========

    @Test
    @DisplayName("线性 DAG 每组一个节点")
    void getParallelGroups_linearDag_eachNodeInOwnGroup() {
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"));
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("B", "C"));

        List<List<String>> groups = DagUtils.getParallelGroups(nodes, edges);

        assertEquals(3, groups.size());
        assertEquals(List.of("A"), groups.get(0));
        assertEquals(List.of("B"), groups.get(1));
        assertEquals(List.of("C"), groups.get(2));
    }

    @Test
    @DisplayName("分叉 DAG 并行节点在同一组")
    void getParallelGroups_forkDag_parallelNodesInSameGroup() {
        // A → B, A → C, B → D, C → D
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"), node("D"));
        List<KbFlowEdgeDO> edges = List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("C", "D")
        );

        List<List<String>> groups = DagUtils.getParallelGroups(nodes, edges);

        assertEquals(3, groups.size());
        assertEquals(List.of("A"), groups.get(0));
        // B 和 C 在同一组（可并行）
        assertEquals(2, groups.get(1).size());
        assertTrue(groups.get(1).contains("B"));
        assertTrue(groups.get(1).contains("C"));
        assertEquals(List.of("D"), groups.get(2));
    }

    // ========== getPredecessors / getSuccessors ==========

    @Test
    @DisplayName("getPredecessors 返回正确的前驱节点")
    void getPredecessors_returnsCorrectNodes() {
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("C", "B"));

        Set<String> predecessors = DagUtils.getPredecessors("B", edges);

        assertEquals(2, predecessors.size());
        assertTrue(predecessors.contains("A"));
        assertTrue(predecessors.contains("C"));
    }

    @Test
    @DisplayName("getSuccessors 返回正确的后继节点")
    void getSuccessors_returnsCorrectNodes() {
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("A", "C"));

        Set<String> successors = DagUtils.getSuccessors("A", edges);

        assertEquals(2, successors.size());
        assertTrue(successors.contains("B"));
        assertTrue(successors.contains("C"));
    }


    // ========== 条件分支剪枝：公共下游保护 ==========

    @Test
    @DisplayName("条件分支未选中路径与选中路径汇合时，公共下游不得被剪枝")
    void computePruned_commonDescendantIsProtected() {
        // condition -> selected(A), unselected(B); A -> merge(C) <- B
        List<KbFlowEdgeDO> edges = List.of(
                edge("condition", "A"), edge("condition", "B"),
                edge("A", "C"), edge("B", "C"));

        Set<String> pruned = DagUtils.computePrunedNodes("condition", List.of("A"), edges);

        assertEquals(Set.of("B"), pruned,
                "公共下游 C 也可从选中分支到达，绝不能被标记为 SKIPPED");
    }

    @Test
    @DisplayName("选中路径更深时，其可达后代全部受保护")
    void computePruned_deepSelectedDescendantsProtected() {
        // condition -> A(selected), B(unselected); A -> A2 -> C; B -> C
        List<KbFlowEdgeDO> edges = List.of(
                edge("condition", "A"), edge("condition", "B"),
                edge("A", "A2"), edge("A2", "C"), edge("B", "C"));

        Set<String> pruned = DagUtils.computePrunedNodes("condition", List.of("A"), edges);

        assertEquals(Set.of("B"), pruned);
    }

    // ========== hasCycle ==========

    @Test
    @DisplayName("hasCycle 无环 DAG 返回 false")
    void hasCycle_noCycle_returnsFalse() {
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"));
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("B", "C"));

        assertFalse(DagUtils.hasCycle(nodes, edges));
    }

    @Test
    @DisplayName("hasCycle 有环 DAG 返回 true")
    void hasCycle_withCycle_returnsTrue() {
        List<KbFlowNodeDO> nodes = List.of(node("A"), node("B"), node("C"));
        List<KbFlowEdgeDO> edges = List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"));

        assertTrue(DagUtils.hasCycle(nodes, edges));
    }
}
