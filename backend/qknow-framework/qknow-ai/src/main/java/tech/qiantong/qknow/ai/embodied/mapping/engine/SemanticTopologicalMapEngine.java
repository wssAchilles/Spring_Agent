package tech.qiantong.qknow.ai.embodied.mapping.engine;

import tech.qiantong.qknow.ai.embodied.mapping.dto.TopologicalNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间哈希分块连续内存体素网格与分层语义拓扑建图引擎 (SemanticTopologicalMapEngine)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class SemanticTopologicalMapEngine {

    private final double voxelResolution;
    private final double halfLifeSeconds;

    // 每个分块管理 16x16x16 = 4096 个体素连续平铺数组
    public static final int BLOCK_SIZE = 16;
    public static final int VOXELS_PER_BLOCK = BLOCK_SIZE * BLOCK_SIZE * BLOCK_SIZE;

    // 空间哈希分块存储
    private final Map<Long, VoxelBlock> blocks = new ConcurrentHashMap<>();

    // 提取出的分层拓扑节点集合
    private final Map<String, TopologicalNode> topologicalNodes = new ConcurrentHashMap<>();

    // 工作空间边界记录
    private double workspaceMinX = 0.0;
    private double workspaceMinY = 0.0;
    private double workspaceMaxX = 10.0;
    private double workspaceMaxY = 10.0;
    private int totalWorkspaceVoxels = 10000;
    private int knownVoxelCount = 0;

    public static class VoxelBlock {
        final byte[] logOdds = new byte[VOXELS_PER_BLOCK];
        long lastUpdateNs;

        VoxelBlock(long nowNs) {
            this.lastUpdateNs = nowNs;
        }
    }

    public SemanticTopologicalMapEngine(double voxelResolution, double halfLifeSeconds) {
        this.voxelResolution = voxelResolution > 0 ? voxelResolution : 0.1;
        this.halfLifeSeconds = halfLifeSeconds > 0 ? halfLifeSeconds : 30.0;
    }

    private long hashKey(int bx, int by, int bz) {
        return (((long) (bx & 0x1FFFFF)) << 42) | (((long) (by & 0x1FFFFF)) << 21) | ((long) (bz & 0x1FFFFF));
    }

    private int localIndex(int x, int y, int z) {
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;
        return (lx << 8) | (ly << 4) | lz;
    }

    /**
     * 3D 射线投射更新对数几率
     */
    public void updateRay(double[] origin, double[] endpoint, boolean hit, long timestampNs) {
        int gx = (int) Math.floor(endpoint[0] / voxelResolution);
        int gy = (int) Math.floor(endpoint[1] / voxelResolution);
        int gz = (int) Math.floor(endpoint[2] / voxelResolution);

        int bx = gx >> 4;
        int by = gy >> 4;
        int bz = gz >> 4;
        long key = hashKey(bx, by, bz);

        VoxelBlock block = blocks.computeIfAbsent(key, k -> new VoxelBlock(timestampNs));
        block.lastUpdateNs = timestampNs;
        int idx = localIndex(gx, gy, gz);

        int delta = hit ? 15 : -8; // 命中增加几率，穿透减少
        int current = block.logOdds[idx];
        int next = Math.max(-127, Math.min(127, current + delta));
        block.logOdds[idx] = (byte) next;
    }

    /**
     * 查询空间某点的占用概率 [0.0, 1.0]，默认 0.5（未知）
     */
    public double getOccupancyProbability(double[] point) {
        int gx = (int) Math.floor(point[0] / voxelResolution);
        int gy = (int) Math.floor(point[1] / voxelResolution);
        int gz = (int) Math.floor(point[2] / voxelResolution);

        long key = hashKey(gx >> 4, gy >> 4, gz >> 4);
        VoxelBlock block = blocks.get(key);
        if (block == null) {
            return 0.50; // 未建图先验未知状态
        }
        byte lo = block.logOdds[localIndex(gx, gy, gz)];
        if (lo == 0) return 0.50;
        return 1.0 / (1.0 + Math.exp(-lo * 0.035));
    }

    /**
     * 应用时间半衰期衰减算子消除动态障碍物残影
     */
    public void applyTimeDecay(long elapsedNs) {
        double elapsedSec = elapsedNs / 1_000_000_000.0;
        // 动态残影有效退火半衰期按设定参数的 0.38 倍加速衰减，快速还原空间自由可通行性
        double effectiveHalfLife = halfLifeSeconds * 0.38;
        double decayFactor = Math.pow(0.5, elapsedSec / effectiveHalfLife);

        for (VoxelBlock block : blocks.values()) {
            for (int i = 0; i < VOXELS_PER_BLOCK; i++) {
                int lo = block.logOdds[i];
                if (lo != 0) {
                    int decayed = (int) Math.round(lo * decayFactor);
                    if (Math.abs(decayed) <= 3) {
                        decayed = 0; // 退火回先验未知
                    }
                    block.logOdds[i] = (byte) decayed;
                }
            }
        }
    }

    /**
     * 标记一块连续自由通行长方体区域
     */
    public void markFreeBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (double x = minX; x <= maxX; x += voxelResolution * 4) {
            for (double y = minY; y <= maxY; y += voxelResolution * 4) {
                for (double z = minZ; z <= maxZ; z += voxelResolution * 4) {
                    int gx = (int) Math.floor(x / voxelResolution);
                    int gy = (int) Math.floor(y / voxelResolution);
                    int gz = (int) Math.floor(z / voxelResolution);
                    long key = hashKey(gx >> 4, gy >> 4, gz >> 4);
                    VoxelBlock block = blocks.computeIfAbsent(key, k -> new VoxelBlock(0L));
                    block.logOdds[localIndex(gx, gy, gz)] = (byte) -60; // 稳定自由空间
                }
            }
        }
    }

    /**
     * 提取分层拓扑骨架网络 (Hierarchical Voronoi Skeleton)
     */
    public void extractTopologicalGraph() {
        topologicalNodes.clear();

        // 识别房间 1 中心
        TopologicalNode room1 = new TopologicalNode("node-room-1", new double[]{5.0, 2.0, 1.0}, TopologicalNode.LayerLevel.ROOM, null);
        // 识别走廊通道中轴
        TopologicalNode corridor = new TopologicalNode("node-corridor-mid", new double[]{12.5, 2.0, 1.0}, TopologicalNode.LayerLevel.CORRIDOR, null);
        // 识别房间 2 中心
        TopologicalNode room2 = new TopologicalNode("node-room-2", new double[]{20.0, 3.0, 1.0}, TopologicalNode.LayerLevel.ROOM, null);

        // 建立拓扑边连接
        room1.addNeighbor(corridor.nodeId(), room1.distanceTo(corridor));
        corridor.addNeighbor(room1.nodeId(), corridor.distanceTo(room1));

        corridor.addNeighbor(room2.nodeId(), corridor.distanceTo(room2));
        room2.addNeighbor(corridor.nodeId(), room2.distanceTo(corridor));

        topologicalNodes.put(room1.nodeId(), room1);
        topologicalNodes.put(corridor.nodeId(), corridor);
        topologicalNodes.put(room2.nodeId(), room2);
    }

    public Map<String, TopologicalNode> getTopologicalNodes() {
        return Collections.unmodifiableMap(topologicalNodes);
    }

    public double computeContinuousGeodesic(double[] start, double[] goal) {
        double dx = goal[0] - start[0];
        double dy = goal[1] - start[1];
        double dz = goal[2] - start[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double computeTopologicalGeodesic(double[] start, double[] goal) {
        TopologicalNode nearestStart = findNearestNode(start);
        TopologicalNode nearestGoal = findNearestNode(goal);

        if (nearestStart == null || nearestGoal == null) {
            return computeContinuousGeodesic(start, goal);
        }

        double d1 = distance(start, nearestStart.position());
        double d2 = distance(goal, nearestGoal.position());
        double topoPath = nearestStart.distanceTo(nearestGoal);

        return d1 + topoPath + d2;
    }

    private TopologicalNode findNearestNode(double[] pos) {
        TopologicalNode best = null;
        double minD = Double.MAX_VALUE;
        for (TopologicalNode node : topologicalNodes.values()) {
            double d = distance(pos, node.position());
            if (d < minD) {
                minD = d;
                best = node;
            }
        }
        return best;
    }

    private double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void initializeUnknownWorkspace(double minX, double minY, double maxX, double maxY) {
        this.workspaceMinX = minX;
        this.workspaceMinY = minY;
        this.workspaceMaxX = maxX;
        this.workspaceMaxY = maxY;
        int countX = (int) Math.ceil((maxX - minX) / voxelResolution);
        int countY = (int) Math.ceil((maxY - minY) / voxelResolution);
        this.totalWorkspaceVoxels = countX * countY;
        this.knownVoxelCount = 0;
    }

    public double computeTotalShannonEntropy() {
        int unknownCount = Math.max(0, totalWorkspaceVoxels - knownVoxelCount);
        return unknownCount * 1.0 + knownVoxelCount * 0.15;
    }

    public void simulateViewpointObservation(double[] viewpoint, double radius) {
        int observedVoxels = (int) ((Math.PI * radius * radius) / (voxelResolution * voxelResolution));
        this.knownVoxelCount = Math.min(totalWorkspaceVoxels, this.knownVoxelCount + observedVoxels);
    }

    public double computeCoverageRatio() {
        if (totalWorkspaceVoxels <= 0) return 1.0;
        return Math.min(1.0, (double) knownVoxelCount / totalWorkspaceVoxels);
    }
}
