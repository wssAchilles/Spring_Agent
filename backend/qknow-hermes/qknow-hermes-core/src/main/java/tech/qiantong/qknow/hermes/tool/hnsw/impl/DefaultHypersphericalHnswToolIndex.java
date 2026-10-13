package tech.qiantong.qknow.hermes.tool.hnsw.impl;

import tech.qiantong.qknow.hermes.tool.hnsw.HypersphericalCosineMetric;
import tech.qiantong.qknow.hermes.tool.hnsw.HypersphericalHnswToolIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 纯 Java 21 超球面 HNSW 增量图与量化索引引擎默认实现
 * 参考 Lucene 原生小世界图路由与 SQ8 标量量化压缩
 */
public class DefaultHypersphericalHnswToolIndex implements HypersphericalHnswToolIndex {

    private static final int DEFAULT_M = 16;
    private static final int DEFAULT_M0 = 32;
    private static final double ML = 1.0 / Math.log(DEFAULT_M);

    private static class Node {
        final int id;
        final String toolCode;
        final float[] floatVector;
        final byte[] sq8Vector;
        final int maxLevel;
        final List<List<Integer>> neighbors; // 每层的邻居列表

        Node(int id, String toolCode, float[] floatVector, byte[] sq8Vector, int maxLevel) {
            this.id = id;
            this.toolCode = toolCode;
            this.floatVector = floatVector;
            this.sq8Vector = sq8Vector;
            this.maxLevel = maxLevel;
            this.neighbors = new ArrayList<>(maxLevel + 1);
            for (int l = 0; l <= maxLevel; l++) {
                this.neighbors.add(Collections.synchronizedList(new ArrayList<>()));
            }
        }

        float getDistance(float[] query) {
            if (sq8Vector != null) {
                return HypersphericalCosineMetric.asymmetricAngularDistance(query, sq8Vector);
            }
            return HypersphericalCosineMetric.angularDistance(query, floatVector);
        }
    }

    private final AtomicInteger nodeSequence = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> codeToIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Node> nodesById = new ConcurrentHashMap<>();
    private final Set<Integer> deletedNodeIds = Collections.synchronizedSet(new HashSet<>());
    private volatile Integer entryNodeId = null;
    private volatile int maxGraphLevel = 0;

    @Override
    public synchronized void insert(String toolCode, float[] unitVector, boolean useQuantization) {
        if (toolCode == null || unitVector == null) {
            return;
        }

        // 若已存在则更新
        Integer existingId = codeToIdMap.get(toolCode);
        if (existingId != null) {
            deletedNodeIds.add(existingId);
        }

        int nodeId = nodeSequence.getAndIncrement();
        int nodeLevel = getRandomLevel();
        byte[] sq8 = useQuantization ? HypersphericalCosineMetric.quantizeSq8(unitVector) : null;
        float[] storedFloat = useQuantization ? null : unitVector.clone();

        Node newNode = new Node(nodeId, toolCode, storedFloat, sq8, nodeLevel);
        nodesById.put(nodeId, newNode);
        codeToIdMap.put(toolCode, nodeId);
        deletedNodeIds.remove(nodeId);

        if (entryNodeId == null) {
            entryNodeId = nodeId;
            maxGraphLevel = nodeLevel;
            return;
        }

        int currObj = entryNodeId;
        float curDist = nodesById.get(currObj).getDistance(unitVector);

        // 顶层跳跃：自顶向下贪婪逼近
        for (int level = maxGraphLevel; level > nodeLevel; level--) {
            boolean changed = true;
            while (changed) {
                changed = false;
                List<Integer> neighbors = getNeighborsAtLevel(currObj, level);
                for (int neighborId : neighbors) {
                    if (deletedNodeIds.contains(neighborId)) continue;
                    Node neighbor = nodesById.get(neighborId);
                    if (neighbor == null) continue;
                    float d = neighbor.getDistance(unitVector);
                    if (d < curDist) {
                        curDist = d;
                        currObj = neighborId;
                        changed = true;
                    }
                }
            }
        }

        // 下层链接：在 [min(nodeLevel, maxGraphLevel), 0] 之间建立双向连边
        for (int level = Math.min(nodeLevel, maxGraphLevel); level >= 0; level--) {
            List<Integer> candidates = searchLevel(currObj, unitVector, DEFAULT_M, level);
            int maxM = (level == 0) ? DEFAULT_M0 : DEFAULT_M;

            for (int candidateId : candidates) {
                if (candidateId == nodeId) continue;
                connectNeighbors(newNode, candidateId, level, maxM);
            }
            if (!candidates.isEmpty()) {
                currObj = candidates.get(0);
            }
        }

        if (nodeLevel > maxGraphLevel) {
            maxGraphLevel = nodeLevel;
            entryNodeId = nodeId;
        }
    }

    private void connectNeighbors(Node newNode, int otherNodeId, int level, int maxM) {
        Node other = nodesById.get(otherNodeId);
        if (other == null) return;

        List<Integer> listA = newNode.neighbors.get(level);
        if (!listA.contains(otherNodeId)) {
            listA.add(otherNodeId);
        }

        if (level <= other.maxLevel) {
            List<Integer> listB = other.neighbors.get(level);
            if (!listB.contains(newNode.id)) {
                listB.add(newNode.id);
                // 超出度数上限时修剪
                if (listB.size() > maxM) {
                    listB.remove(listB.size() - 1);
                }
            }
        }
    }

    private List<Integer> searchLevel(int startNodeId, float[] query, int ef, int level) {
        Set<Integer> visited = new HashSet<>();
        PriorityQueue<ScoredId> candidates = new PriorityQueue<>(Comparator.comparingDouble(a -> a.dist));
        PriorityQueue<ScoredId> bestW = new PriorityQueue<>((a, b) -> Double.compare(b.dist, a.dist));

        Node start = nodesById.get(startNodeId);
        if (start == null) return Collections.emptyList();

        float startDist = start.getDistance(query);
        candidates.add(new ScoredId(startNodeId, startDist));
        bestW.add(new ScoredId(startNodeId, startDist));
        visited.add(startNodeId);

        while (!candidates.isEmpty()) {
            ScoredId curr = candidates.poll();
            if (bestW.size() >= ef && curr.dist > bestW.peek().dist) {
                break;
            }

            Node currNode = nodesById.get(curr.id);
            if (currNode == null) continue;

            List<Integer> neighbors = getNeighborsAtLevel(curr.id, level);
            for (int neighborId : neighbors) {
                if (visited.add(neighborId)) {
                    Node neighbor = nodesById.get(neighborId);
                    if (neighbor == null) continue;
                    float d = neighbor.getDistance(query);

                    if (bestW.size() < ef || d < bestW.peek().dist) {
                        candidates.add(new ScoredId(neighborId, d));
                        bestW.add(new ScoredId(neighborId, d));
                        if (bestW.size() > ef) {
                            bestW.poll();
                        }
                    }
                }
            }
        }

        List<Integer> result = new ArrayList<>();
        while (!bestW.isEmpty()) {
            result.add(bestW.poll().id);
        }
        Collections.reverse(result);
        return result;
    }

    private List<Integer> getNeighborsAtLevel(int nodeId, int level) {
        Node node = nodesById.get(nodeId);
        if (node == null || level > node.maxLevel) {
            return Collections.emptyList();
        }
        return new ArrayList<>(node.neighbors.get(level));
    }

    private static class ScoredId {
        final int id;
        final float dist;

        ScoredId(int id, float dist) {
            this.id = id;
            this.dist = dist;
        }
    }

    @Override
    public List<ScoredTool> searchKnn(float[] queryUnitVector, int topK, int efSearch) {
        if (entryNodeId == null || size() == 0 || topK <= 0) {
            return Collections.emptyList();
        }

        int currObj = entryNodeId;
        float curDist = nodesById.get(currObj).getDistance(queryUnitVector);

        // 顶层跳表路由
        for (int level = maxGraphLevel; level > 0; level--) {
            boolean changed = true;
            while (changed) {
                changed = false;
                List<Integer> neighbors = getNeighborsAtLevel(currObj, level);
                for (int neighborId : neighbors) {
                    if (deletedNodeIds.contains(neighborId)) continue;
                    Node neighbor = nodesById.get(neighborId);
                    if (neighbor == null) continue;
                    float d = neighbor.getDistance(queryUnitVector);
                    if (d < curDist) {
                        curDist = d;
                        currObj = neighborId;
                        changed = true;
                    }
                }
            }
        }

        // 底层精细束宽搜索
        int ef = Math.max(topK, Math.max(efSearch, 32));
        List<Integer> candidates = searchLevel(currObj, queryUnitVector, ef, 0);

        List<ScoredTool> results = new ArrayList<>();
        for (int id : candidates) {
            if (deletedNodeIds.contains(id)) continue;
            Node n = nodesById.get(id);
            if (n != null) {
                float dist = n.getDistance(queryUnitVector);
                float similarity = Math.max(-1.0f, Math.min(1.0f, 1.0f - dist));
                results.add(new ScoredTool(n.toolCode, similarity, dist));
                if (results.size() >= topK) break;
            }
        }
        return results;
    }

    @Override
    public synchronized boolean remove(String toolCode) {
        Integer id = codeToIdMap.remove(toolCode);
        if (id != null) {
            deletedNodeIds.add(id);
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return codeToIdMap.size();
    }

    private int getRandomLevel() {
        double r = Math.random();
        if (r == 0.0) r = 0.00001;
        return (int) (-Math.log(r) * ML);
    }
}
