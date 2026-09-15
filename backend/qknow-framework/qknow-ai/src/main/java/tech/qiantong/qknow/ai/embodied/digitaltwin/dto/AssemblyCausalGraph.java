package tech.qiantong.qknow.ai.embodied.digitaltwin.dto;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * 柔性装配线结构因果拓扑图与 Pearl 结构因果模型 (SCM) 载体 (Java 21 Record)
 * <p>
 * 封装产线 ID、30+ 关键工位节点名称列表、因果有向边邻接矩阵 adjacencyMatrix[u][v]、
 * 各节点间结构方程传播权重数组与编译时间戳。
 */
public record AssemblyCausalGraph(
        String lineId,
        String[] stationNodeNames,
        int[][] adjacencyMatrix,
        double[] structuralWeights,
        long timestampMs
) {
    public AssemblyCausalGraph {
        Objects.requireNonNull(lineId, "lineId 不能为空");
        Objects.requireNonNull(stationNodeNames, "stationNodeNames 不能为空");
        Objects.requireNonNull(adjacencyMatrix, "adjacencyMatrix 不能为空");
        Objects.requireNonNull(structuralWeights, "structuralWeights 不能为空");

        int n = stationNodeNames.length;
        if (n < 2) {
            throw new IllegalArgumentException("装配线工位节点数必须至少为 2，当前为: " + n);
        }
        if (adjacencyMatrix.length != n) {
            throw new IllegalArgumentException("邻接矩阵行数必须等于节点数: " + n);
        }
        for (int i = 0; i < n; i++) {
            if (adjacencyMatrix[i] == null || adjacencyMatrix[i].length != n) {
                throw new IllegalArgumentException("邻接矩阵必须为正方形方阵，行 " + i + " 不匹配");
            }
        }
    }

    /**
     * 利用 Kahn 算法入度消除严格验证因果图的有向无环性 (DAG)
     *
     * @return 若不存在有向环路则返回 true，否则返回 false
     */
    public boolean isAcyclic() {
        int n = stationNodeNames.length;
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (adjacencyMatrix[i][j] > 0) {
                    inDegree[j]++;
                }
            }
        }

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }

        int visitedCount = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            visitedCount++;
            for (int v = 0; v < n; v++) {
                if (adjacencyMatrix[u][v] > 0) {
                    inDegree[v]--;
                    if (inDegree[v] == 0) {
                        queue.offer(v);
                    }
                }
            }
        }
        return visitedCount == n;
    }

    public int getNodeCount() {
        return stationNodeNames.length;
    }

    public List<Integer> getParents(int nodeIndex) {
        List<Integer> parents = new ArrayList<>();
        int n = stationNodeNames.length;
        for (int i = 0; i < n; i++) {
            if (adjacencyMatrix[i][nodeIndex] > 0) {
                parents.add(i);
            }
        }
        return parents;
    }

    public List<Integer> getChildren(int nodeIndex) {
        List<Integer> children = new ArrayList<>();
        int n = stationNodeNames.length;
        for (int j = 0; j < n; j++) {
            if (adjacencyMatrix[nodeIndex][j] > 0) {
                children.add(j);
            }
        }
        return children;
    }
}
