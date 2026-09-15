package tech.qiantong.qknow.ai.embodied.cooperative.dto;

import java.util.List;

/**
 * 工件抓取拓扑矩阵 G in R^{6 x 6m} 封装
 * 将各接触点施加的力/力矩映射至工件质心坐标系下的合外力/合力矩
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class GraspTopologyMatrix {

    private final int agentCount;
    private final double[][] graspMatrix; // 6 x (6 * agentCount)

    public GraspTopologyMatrix(List<double[]> contactPointsFromCoM) {
        if (contactPointsFromCoM == null || contactPointsFromCoM.isEmpty()) {
            throw new IllegalArgumentException("Contact points list must not be empty");
        }
        this.agentCount = contactPointsFromCoM.size();
        this.graspMatrix = new double[6][6 * agentCount];

        for (int i = 0; i < agentCount; i++) {
            double[] p = contactPointsFromCoM.get(i); // [px, py, pz]
            int colOffset = i * 6;

            // 1. 线力传递: 单位矩阵 I_{3x3}
            graspMatrix[0][colOffset] = 1.0;
            graspMatrix[1][colOffset + 1] = 1.0;
            graspMatrix[2][colOffset + 2] = 1.0;

            // 2. 扭矩传递: [p x] 反对称矩阵
            // [p x] 矩阵:
            //  0   -pz   py
            //  pz   0   -px
            // -py   px   0
            double px = p[0], py = p[1], pz = p[2];
            graspMatrix[3][colOffset + 1] = -pz;
            graspMatrix[3][colOffset + 2] = py;

            graspMatrix[4][colOffset] = pz;
            graspMatrix[4][colOffset + 2] = -px;

            graspMatrix[5][colOffset] = -py;
            graspMatrix[5][colOffset + 1] = px;

            // 3. 接触点力矩直接传递: 单位矩阵 I_{3x3}
            graspMatrix[3][colOffset + 3] = 1.0;
            graspMatrix[4][colOffset + 4] = 1.0;
            graspMatrix[5][colOffset + 5] = 1.0;
        }
    }

    public int getAgentCount() {
        return agentCount;
    }

    public double[][] getMatrix() {
        return graspMatrix;
    }
}
