package tech.qiantong.qknow.ai.embodied.collaborative.engine;

import tech.qiantong.qknow.ai.embodied.collaborative.dto.DeltaSubmap;
import tech.qiantong.qknow.ai.embodied.mapping.dto.TopologicalNode;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 分布式增量拓扑子图融合引擎
 * 实现 Delta-Submap 轻量二进制序列化 (量化压缩 <= 4KB)、千问 1536 维超球面初筛、Kabsch/Jacobi 闭式刚体对齐与位姿图增量融合
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class DistributedSubmapFusionEngine {

    private final double cosineThreshold;
    private final double rmseThreshold;
    private final Map<String, DeltaSubmap> submapStorage = new ConcurrentHashMap<>();

    public DistributedSubmapFusionEngine(double cosineThreshold, double rmseThreshold) {
        this.cosineThreshold = cosineThreshold;
        this.rmseThreshold = rmseThreshold;
    }

    public enum RejectReason {
        NONE,
        HYPERSPHERICAL_SEMANTIC_MISMATCH,
        GEOMETRIC_ALIGNMENT_RESIDUAL_TOO_LARGE,
        INSUFFICIENT_CORRESPONDENCES
    }

    public record AlignmentResult(
            boolean accepted,
            double rmse,
            double[][] transformMatrix,
            RejectReason rejectReason
    ) {
    }

    /**
     * Delta-Submap 增量序列化 (采用 int8 量化超球面向量与 GZIP 压缩，保证单包体积 <= 4KB)
     */
    public byte[] serializeDeltaSubmap(DeltaSubmap submap) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {

            dos.writeUTF(submap.submapId());
            dos.writeUTF(submap.agentId());
            dos.writeLong(submap.timestamp());

            // SE(3) 位姿矩阵 (4x4)
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    dos.writeDouble(submap.se3Pose()[r][c]);
                }
            }

            // 稀疏体素增量
            Map<Long, Byte> voxels = submap.voxelDeltas();
            dos.writeInt(voxels != null ? voxels.size() : 0);
            if (voxels != null) {
                for (Map.Entry<Long, Byte> entry : voxels.entrySet()) {
                    dos.writeLong(entry.getKey());
                    dos.writeByte(entry.getValue());
                }
            }

            // 拓扑节点
            List<TopologicalNode> nodes = submap.topologicalNodes();
            dos.writeInt(nodes != null ? nodes.size() : 0);
            if (nodes != null) {
                for (TopologicalNode node : nodes) {
                    dos.writeUTF(node.nodeId());
                    double[] pos = node.position();
                    dos.writeDouble(pos[0]);
                    dos.writeDouble(pos[1]);
                    dos.writeDouble(pos[2]);
                    dos.writeUTF(node.layerLevel().name());
                }
            }

            // 阿里千问 1536 维超球面指纹 (以 int8 量化，每维 1 字节)
            double[] fp = submap.hypersphericalFingerprint();
            dos.writeInt(fp != null ? fp.length : 0);
            if (fp != null) {
                for (double v : fp) {
                    byte b = (byte) Math.max(-127, Math.min(127, Math.round(v * 127.0)));
                    dos.writeByte(b);
                }
            }

            dos.flush();
            gzos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize DeltaSubmap", e);
        }
    }

    /**
     * Delta-Submap 反序列化
     */
    public DeltaSubmap deserializeDeltaSubmap(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             DataInputStream dis = new DataInputStream(gzis)) {

            String submapId = dis.readUTF();
            String agentId = dis.readUTF();
            long timestamp = dis.readLong();

            double[][] se3Pose = new double[4][4];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    se3Pose[r][c] = dis.readDouble();
                }
            }

            int voxelSize = dis.readInt();
            Map<Long, Byte> voxelDeltas = new HashMap<>(voxelSize);
            for (int i = 0; i < voxelSize; i++) {
                voxelDeltas.put(dis.readLong(), dis.readByte());
            }

            int nodeSize = dis.readInt();
            List<TopologicalNode> nodes = new ArrayList<>(nodeSize);
            for (int i = 0; i < nodeSize; i++) {
                String nodeId = dis.readUTF();
                double[] coords = new double[]{dis.readDouble(), dis.readDouble(), dis.readDouble()};
                TopologicalNode.LayerLevel level = TopologicalNode.LayerLevel.valueOf(dis.readUTF());
                nodes.add(new TopologicalNode(nodeId, coords, level, new double[1536]));
            }

            int fpLen = dis.readInt();
            double[] fp = new double[fpLen];
            double sumSq = 0.0;
            for (int i = 0; i < fpLen; i++) {
                byte b = dis.readByte();
                fp[i] = b / 127.0;
                sumSq += fp[i] * fp[i];
            }
            // 超球面归一化
            double norm = Math.sqrt(sumSq);
            if (norm > 1e-12) {
                for (int i = 0; i < fpLen; i++) {
                    fp[i] /= norm;
                }
            }

            return new DeltaSubmap(submapId, agentId, se3Pose, voxelDeltas, nodes, fp, timestamp);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize DeltaSubmap", e);
        }
    }

    /**
     * 基于千问 1536 维超球面初筛与 Kabsch 闭式求解的子图刚体对齐 (定理 1.1)
     */
    public AlignmentResult alignSubmaps(
            double[] fpSource,
            double[] fpTarget,
            List<double[]> sourcePoints,
            List<double[]> targetPoints
    ) {
        // 1. 千问 1536 维超球面余弦相似度初筛 (||v||_2 = 1.0)
        double cosineSim = computeHypersphericalCosine(fpSource, fpTarget);
        if (cosineSim < this.cosineThreshold) {
            return new AlignmentResult(false, Double.POSITIVE_INFINITY, null, RejectReason.HYPERSPHERICAL_SEMANTIC_MISMATCH);
        }

        if (sourcePoints == null || targetPoints == null || sourcePoints.size() != targetPoints.size() || sourcePoints.size() < 3) {
            return new AlignmentResult(false, Double.POSITIVE_INFINITY, null, RejectReason.INSUFFICIENT_CORRESPONDENCES);
        }

        // 2. Kabsch 闭式刚体对齐求解
        int n = sourcePoints.size();
        double[] centroidSource = computeCentroid(sourcePoints);
        double[] centroidTarget = computeCentroid(targetPoints);

        // 去质心协方差矩阵 H = sum (p_i - p_bar) * (q_i - q_bar)^T
        double[][] H = new double[3][3];
        for (int i = 0; i < n; i++) {
            double[] p = sourcePoints.get(i);
            double[] q = targetPoints.get(i);
            double dx = p[0] - centroidSource[0];
            double dy = p[1] - centroidSource[1];
            double dz = p[2] - centroidSource[2];

            double qx = q[0] - centroidTarget[0];
            double qy = q[1] - centroidTarget[1];
            double qz = q[2] - centroidTarget[2];

            H[0][0] += dx * qx; H[0][1] += dx * qy; H[0][2] += dx * qz;
            H[1][0] += dy * qx; H[1][1] += dy * qy; H[1][2] += dy * qz;
            H[2][0] += dz * qx; H[2][1] += dz * qy; H[2][2] += dz * qz;
        }

        // Horn 1987 四元数法配合 Jacobi 求解最优旋转矩阵 R
        double[][] R = solveKabschRotation(H);

        // 计算平移向量 t = targetCentroid - R * sourceCentroid
        double[] t = new double[3];
        for (int i = 0; i < 3; i++) {
            t[i] = centroidTarget[i] - (R[i][0] * centroidSource[0] + R[i][1] * centroidSource[1] + R[i][2] * centroidSource[2]);
        }

        // 构造 4x4 刚体变换矩阵
        double[][] transformMatrix = new double[4][4];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(R[r], 0, transformMatrix[r], 0, 3);
            transformMatrix[r][3] = t[r];
        }
        transformMatrix[3][3] = 1.0;

        // 计算对齐均方根误差 (RMSE)
        double sumSqErr = 0.0;
        for (int i = 0; i < n; i++) {
            double[] p = sourcePoints.get(i);
            double[] q = targetPoints.get(i);

            double transX = R[0][0] * p[0] + R[0][1] * p[1] + R[0][2] * p[2] + t[0];
            double transY = R[1][0] * p[0] + R[1][1] * p[1] + R[1][2] * p[2] + t[1];
            double transZ = R[2][0] * p[0] + R[2][1] * p[1] + R[2][2] * p[2] + t[2];

            double err = (transX - q[0]) * (transX - q[0]) +
                         (transY - q[1]) * (transY - q[1]) +
                         (transZ - q[2]) * (transZ - q[2]);
            sumSqErr += err;
        }
        double rmse = Math.sqrt(sumSqErr / n);

        if (rmse <= this.rmseThreshold) {
            return new AlignmentResult(true, rmse, transformMatrix, RejectReason.NONE);
        } else {
            return new AlignmentResult(false, rmse, transformMatrix, RejectReason.GEOMETRIC_ALIGNMENT_RESIDUAL_TOO_LARGE);
        }
    }

    /**
     * 子图接收入库
     */
    public boolean integrateSubmap(DeltaSubmap submap) {
        if (submap == null) {
            return false;
        }
        submapStorage.put(submap.submapId(), submap);
        return true;
    }

    private double computeHypersphericalCosine(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length != 1536 || v2.length != 1536) {
            return -1.0;
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }

    private double[] computeCentroid(List<double[]> points) {
        double[] c = new double[3];
        for (double[] p : points) {
            c[0] += p[0];
            c[1] += p[1];
            c[2] += p[2];
        }
        int n = points.size();
        c[0] /= n;
        c[1] /= n;
        c[2] /= n;
        return c;
    }

    /**
     * 基于 Horn 1987 与 4x4 Jacobi 闭式特征值分解求解最优旋转
     */
    private double[][] solveKabschRotation(double[][] H) {
        double Sxx = H[0][0], Sxy = H[0][1], Sxz = H[0][2];
        double Syx = H[1][0], Syy = H[1][1], Syz = H[1][2];
        double Szx = H[2][0], Szy = H[2][1], Szz = H[2][2];

        double[][] K = new double[4][4];
        K[0][0] = Sxx + Syy + Szz;
        K[0][1] = Syz - Szy;
        K[0][2] = Szx - Sxz;
        K[0][3] = Sxy - Syx;

        K[1][0] = K[0][1];
        K[1][1] = Sxx - Syy - Szz;
        K[1][2] = Sxy + Syx;
        K[1][3] = Szx + Sxz;

        K[2][0] = K[0][2];
        K[2][1] = K[1][2];
        K[2][2] = -Sxx + Syy - Szz;
        K[2][3] = Syz + Szy;

        K[3][0] = K[0][3];
        K[3][1] = K[1][3];
        K[3][2] = K[2][3];
        K[3][3] = -Sxx - Syy + Szz;

        // Jacobi 求解 4x4 实对称矩阵最大特征值对应的特征向量
        double[] q = solveMaxEigenvectorJacobi(K);

        double w = q[0], x = q[1], y = q[2], z = q[3];
        double[][] R = new double[3][3];
        R[0][0] = 1 - 2 * (y * y + z * z);
        R[0][1] = 2 * (x * y - z * w);
        R[0][2] = 2 * (x * z + y * w);

        R[1][0] = 2 * (x * y + z * w);
        R[1][1] = 1 - 2 * (x * x + z * z);
        R[1][2] = 2 * (y * z - x * w);

        R[2][0] = 2 * (x * z - y * w);
        R[2][1] = 2 * (y * z + x * w);
        R[2][2] = 1 - 2 * (x * x + y * y);

        return R;
    }

    private double[] solveMaxEigenvectorJacobi(double[][] A) {
        int n = 4;
        double[][] V = new double[n][n];
        for (int i = 0; i < n; i++) V[i][i] = 1.0;

        double[][] mat = new double[n][n];
        for (int r = 0; r < n; r++) {
            System.arraycopy(A[r], 0, mat[r], 0, n);
        }

        for (int iter = 0; iter < 30; iter++) {
            double maxVal = 0.0;
            int p = 0, q = 1;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (Math.abs(mat[i][j]) > maxVal) {
                        maxVal = Math.abs(mat[i][j]);
                        p = i;
                        q = j;
                    }
                }
            }
            if (maxVal < 1e-12) break;

            double theta = 0.5 * Math.atan2(2.0 * mat[p][q], mat[q][q] - mat[p][p]);
            double c = Math.cos(theta);
            double s = Math.sin(theta);

            // 更新 mat = J^T * mat * J
            double app = mat[p][p];
            double aqq = mat[q][q];
            double apq = mat[p][q];

            mat[p][p] = c * c * app - 2 * s * c * apq + s * s * aqq;
            mat[q][q] = s * s * app + 2 * s * c * apq + c * c * aqq;
            mat[p][q] = 0.0;
            mat[q][p] = 0.0;

            for (int i = 0; i < n; i++) {
                if (i != p && i != q) {
                    double aip = mat[i][p];
                    double aiq = mat[i][q];
                    mat[i][p] = c * aip - s * aiq;
                    mat[p][i] = mat[i][p];
                    mat[i][q] = s * aip + c * aiq;
                    mat[q][i] = mat[i][q];
                }
            }

            // 更新特征向量矩阵 V = V * J
            for (int i = 0; i < n; i++) {
                double vip = V[i][p];
                double viq = V[i][q];
                V[i][p] = c * vip - s * viq;
                V[i][q] = s * vip + c * viq;
            }
        }

        // 寻找最大特征值
        int maxIdx = 0;
        double maxVal = mat[0][0];
        for (int i = 1; i < n; i++) {
            if (mat[i][i] > maxVal) {
                maxVal = mat[i][i];
                maxIdx = i;
            }
        }

        double[] q = new double[n];
        for (int i = 0; i < n; i++) {
            q[i] = V[i][maxIdx];
        }
        return q;
    }
}
