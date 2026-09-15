package tech.qiantong.qknow.ai.embodied.suction.dto;

import java.util.Arrays;

/**
 * 仿生微吸盘阵列气动拓扑包络数据模型 (Java 21 Record)
 * 封装多微吸盘三维坐标、法向分布、腔内压强、微气隙泄漏率与密封完整度，绑定阿里千问 1536 维超球面单位向量。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record MicroSuctionCupArrayEnvelope(
        int cupCount,
        double[][] cupPositions,
        double[][] normalVectors,
        double[] cupPressuresKPa,
        double[] microLeakFlowRates,
        double[] sealIntegrity,
        double[] qwenEmbedding1536
) {
    public MicroSuctionCupArrayEnvelope {
        if (cupCount < 4) {
            throw new IllegalArgumentException("Micro suction cup array requires at least 4 cups, got: " + cupCount);
        }
        if (cupPositions == null || cupPositions.length != cupCount) {
            throw new IllegalArgumentException("Cup positions array length must match cup count");
        }
        if (normalVectors == null || normalVectors.length != cupCount) {
            throw new IllegalArgumentException("Normal vectors array length must match cup count");
        }
        if (cupPressuresKPa == null || cupPressuresKPa.length != cupCount) {
            throw new IllegalArgumentException("Cup pressures array length must match cup count");
        }
        if (microLeakFlowRates == null || microLeakFlowRates.length != cupCount) {
            throw new IllegalArgumentException("Micro leak flow rates array length must match cup count");
        }
        if (sealIntegrity == null || sealIntegrity.length != cupCount) {
            throw new IllegalArgumentException("Seal integrity array length must match cup count");
        }
        if (qwenEmbedding1536 == null || qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("Qwen embedding must have exact dimension 1536");
        }
    }

    /**
     * 校验阿里千问 1536 维超球面单位范数约束 ||v||_2 = 1.0 +- 1e-5
     */
    public boolean verifyQwenHypersphereInvariant() {
        double sumSq = 0.0;
        for (double v : qwenEmbedding1536) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-5;
    }

    /**
     * 计算吸盘阵列平均密封完整度
     */
    public double computeAverageSealIntegrity() {
        double sum = 0.0;
        for (double s : sealIntegrity) {
            sum += s;
        }
        return sum / cupCount;
    }

    /**
     * 估算吸盘阵列法向净吸附总力 (N)
     * 假设每个微吸盘标称受力面积为 nominalAreaMm2
     */
    public double computeTotalSuctionForceN(double atmPressureKPa, double cupRadiusMm) {
        double cupAreaM2 = Math.PI * Math.pow(cupRadiusMm * 1e-3, 2);
        double totalForceN = 0.0;
        for (int i = 0; i < cupCount; i++) {
            double deltaPKPa = Math.max(0.0, atmPressureKPa - cupPressuresKPa[i]);
            // F = deltaP (Pa) * Area (m^2) * sealIntegrity
            double forceN = (deltaPKPa * 1000.0) * cupAreaM2 * sealIntegrity[i];
            totalForceN += forceN;
        }
        return totalForceN;
    }
}
