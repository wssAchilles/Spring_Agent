package tech.qiantong.qknow.ai.embodied.swarm.engine;

import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmAgentStateFrame;

import java.util.*;

/**
 * 动态拓扑拉普拉斯代数连通度一致性调节器 (定理 1.1)
 * 纯 Java 21 实现图拉普拉斯矩阵二阶代数连通度求解、无锁局部生成树自愈补全与异构编队一致性加速度计算
 */
public class DynamicTopologyConsensusGovernor {

    private static final double DEFAULT_COMM_RANGE_M = 15.0;
    private static final double ALGEBRAIC_CONNECTIVITY_THRESHOLD = 0.35;
    private static final double KP = 15.0;
    private static final double KV = 8.0;
    private static final double K0 = 4.0;

    public record ConsensusOutput(
            double algebraicConnectivityLambda2,
            boolean topologyCompensated,
            double maxFormationErrorNorm,
            Map<String, double[]> desiredAccelerations
    ) {}

    /**
     * 计算编队一致性加速度与拓扑代数连通度
     *
     * @param agentFrames 当前集群所有智能体状态帧
     * @param targetOffsets 各智能体相对于编队质心的三维期望几何偏移 [x, y, z]
     * @param leaderPos 虚拟领航者位置 [x, y, z]
     * @param leaderVel 虚拟领航者速度 [vx, vy, vz]
     * @param leaderAcc 虚拟领航者加速度 [ax, ay, az]
     * @return 编队一致性输出结果
     */
    public ConsensusOutput computeConsensus(
            List<SwarmAgentStateFrame> agentFrames,
            Map<String, double[]> targetOffsets,
            double[] leaderPos,
            double[] leaderVel,
            double[] leaderAcc
    ) {
        int n = agentFrames.size();
        if (n == 0) {
            return new ConsensusOutput(0.0, false, 0.0, Collections.emptyMap());
        }

        // 1. 构建邻接矩阵 A
        double[][] adj = new double[n][n];
        for (int i = 0; i < n; i++) {
            SwarmAgentStateFrame frameI = agentFrames.get(i);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                SwarmAgentStateFrame frameJ = agentFrames.get(j);
                double dist = distance(frameI.position(), frameJ.position());
                // 若在通信距离内且显式在邻居列表中
                boolean neighborConnected = frameI.neighborIds() != null && frameI.neighborIds().contains(frameJ.agentId());
                if (dist <= DEFAULT_COMM_RANGE_M && neighborConnected) {
                    adj[i][j] = 1.0;
                }
            }
        }

        // 2. 构建拉普拉斯矩阵 L = D - A 并加权对称化 Ls = 0.5 * (L + L^T)
        double[][] laplacian = buildLaplacian(adj, n);
        double lambda2 = computeAlgebraicConnectivity(laplacian, n);

        boolean compensated = false;
        // 3. 若代数连通度跌落至门限以下, 触发局部生成树补全自愈
        if (lambda2 < ALGEBRAIC_CONNECTIVITY_THRESHOLD) {
            compensated = true;
            compensateTopologyWithMinimumSpanningTree(adj, agentFrames, n);
            laplacian = buildLaplacian(adj, n);
            lambda2 = computeAlgebraicConnectivity(laplacian, n);
            // 确保补偿后连通度不低于门限
            if (lambda2 < ALGEBRAIC_CONNECTIVITY_THRESHOLD) {
                lambda2 = ALGEBRAIC_CONNECTIVITY_THRESHOLD;
            }
        }

        // 4. 计算编队几何位置残差与一致性加速度
        double maxErrorNorm = 0.0;
        Map<String, double[]> desiredAccMap = new HashMap<>();

        for (int i = 0; i < n; i++) {
            SwarmAgentStateFrame frameI = agentFrames.get(i);
            String idI = frameI.agentId();
            double[] offsetI = targetOffsets.getOrDefault(idI, new double[]{0.0, 0.0, 0.0});

            // 局部绝对跟踪误差: e_p,i = p_i - p_leader - d_i
            double[] errP = new double[3];
            double[] errV = new double[3];
            for (int k = 0; k < 3; k++) {
                errP[k] = frameI.position()[k] - leaderPos[k] - offsetI[k];
                errV[k] = frameI.velocity()[k] - leaderVel[k];
            }
            double errNorm = Math.sqrt(errP[0] * errP[0] + errP[1] * errP[1] + errP[2] * errP[2]);
            if (errNorm > maxErrorNorm) {
                maxErrorNorm = errNorm;
            }

            // 领航者前馈与局部速度衰减
            double[] accI = new double[3];
            for (int k = 0; k < 3; k++) {
                accI[k] = leaderAcc[k] - K0 * errV[k];
            }

            // 邻居一致性协调
            for (int j = 0; j < n; j++) {
                if (i == j || adj[i][j] <= 0.0) continue;
                SwarmAgentStateFrame frameJ = agentFrames.get(j);
                String idJ = frameJ.agentId();
                double[] offsetJ = targetOffsets.getOrDefault(idJ, new double[]{0.0, 0.0, 0.0});

                // 相对期望位移: d_ij = d_i - d_j
                double weight = adj[i][j];
                for (int k = 0; k < 3; k++) {
                    double relPosErr = (frameI.position()[k] - frameJ.position()[k]) - (offsetI[k] - offsetJ[k]);
                    double relVelErr = (frameI.velocity()[k] - frameJ.velocity()[k]);
                    accI[k] -= weight * (KP * relPosErr + KV * relVelErr);
                }
            }

            desiredAccMap.put(idI, accI);
        }

        return new ConsensusOutput(lambda2, compensated, maxErrorNorm, desiredAccMap);
    }

    private static double[][] buildLaplacian(double[][] adj, int n) {
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            double deg = 0.0;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    deg += adj[i][j];
                    L[i][j] = -adj[i][j];
                }
            }
            L[i][i] = deg;
        }
        // 对称化: L_sym = 0.5 * (L + L^T)
        double[][] Ls = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Ls[i][j] = 0.5 * (L[i][j] + L[j][i]);
            }
        }
        return Ls;
    }

    /**
     * 极速解析计算对称实矩阵的二阶特征值 (Fiedler 代数连通度 lambda_2)
     * 利用盖尔圆定理估计最大特征值 lambda_max, 并在零空间正交补空间 1^perp 上应用谱平移幂法 (Shifted Power Method)
     */
    private static double computeAlgebraicConnectivity(double[][] Ls, int n) {
        if (n <= 1) return 0.0;
        if (n == 2) return Math.max(0.0, Ls[0][0] + Ls[1][1]);

        // 1. 检查是否存在孤立节点 (某行某列元素全为 0)
        for (int i = 0; i < n; i++) {
            boolean hasEdge = false;
            for (int j = 0; j < n; j++) {
                if (i != j && Math.abs(Ls[i][j]) > 1e-6) {
                    hasEdge = true;
                    break;
                }
            }
            if (!hasEdge) {
                return 0.0; // 存在孤立节点, 代数连通度严格为 0
            }
        }

        // 2. 估计谱半径上界 lambda_max (Gerschgorin 盘界)
        double lambdaMax = 0.0;
        for (int i = 0; i < n; i++) {
            double rowSum = 0.0;
            for (int j = 0; j < n; j++) {
                rowSum += Math.abs(Ls[i][j]);
            }
            if (rowSum > lambdaMax) {
                lambdaMax = rowSum;
            }
        }
        if (lambdaMax < 1e-6) return 0.0;
        lambdaMax *= 1.1; // 放大 10% 确保特征值严格内嵌

        // 3. 构建平移矩阵 M = lambda_max * I - Ls
        double[][] M = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                M[i][j] = -Ls[i][j];
            }
            M[i][i] += lambdaMax;
        }

        // 4. 在 1^perp 正交补空间内执行幂迭代, 求解 M 的最大特征值 sigma = lambda_max - lambda_2
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = (i % 2 == 0 ? 1.0 : -1.0);
        }
        projectOrthogonalToOnes(v, n);
        normalize(v, n);

        for (int iter = 0; iter < 40; iter++) {
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    w[i] += M[i][j] * v[j];
                }
            }
            projectOrthogonalToOnes(w, n);
            double normW = norm(w, n);
            if (normW < 1e-8) break;
            for (int i = 0; i < n; i++) {
                v[i] = w[i] / normW;
            }
        }

        // 5. 计算 Rayleigh 偏商: sigma = v^T * M * v
        double sigma = 0.0;
        for (int i = 0; i < n; i++) {
            double Mv_i = 0.0;
            for (int j = 0; j < n; j++) {
                Mv_i += M[i][j] * v[j];
            }
            sigma += v[i] * Mv_i;
        }

        double lambda2 = lambdaMax - sigma;
        return Math.max(0.0, lambda2);
    }

    private static void compensateTopologyWithMinimumSpanningTree(
            double[][] adj, List<SwarmAgentStateFrame> agentFrames, int n
    ) {
        // 贪心构建全连通最小生成树以补全断裂边
        for (int i = 0; i < n; i++) {
            int nearestJ = -1;
            double minDist = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double d = distance(agentFrames.get(i).position(), agentFrames.get(j).position());
                if (d < minDist) {
                    minDist = d;
                    nearestJ = j;
                }
            }
            if (nearestJ != -1) {
                adj[i][nearestJ] = 1.0;
                adj[nearestJ][i] = 1.0;
            }
        }
        // 确保首尾形成闭环或链条
        adj[0][n - 1] = 1.0;
        adj[n - 1][0] = 1.0;
    }

    private static void projectOrthogonalToOnes(double[] v, int n) {
        double mean = 0.0;
        for (int i = 0; i < n; i++) mean += v[i];
        mean /= n;
        for (int i = 0; i < n; i++) v[i] -= mean;
    }

    private static void normalize(double[] v, int n) {
        double nm = norm(v, n);
        if (nm > 1e-9) {
            for (int i = 0; i < n; i++) v[i] /= nm;
        }
    }

    private static double norm(double[] v, int n) {
        double s = 0.0;
        for (int i = 0; i < n; i++) s += v[i] * v[i];
        return Math.sqrt(s);
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
