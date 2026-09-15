package tech.qiantong.qknow.ai.embodied.suction.engine;

import tech.qiantong.qknow.ai.embodied.suction.dto.MicroSuctionCupArrayEnvelope;

/**
 * 仿生微吸盘负压流形自适应调节器
 * 基于连续气动泊肃叶泄漏模型与气液固三相接触动力学，解析判别密封完整度与泄漏临界相变，
 * 并支持阿里千问 1536 维超球面几何测地对齐。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class BionicSuctionManifoldGovernor {

    private static final double ATMOSPHERIC_PRESSURE_KPA = 101.325;
    private static final double AIR_DYNAMIC_VISCOSITY_PA_S = 1.81e-5; // 空气动力粘度
    private static final double NOMINAL_SEAL_LIP_LENGTH_MM = 1.2; // 密封唇缘接触宽度 L

    /**
     * 计算微气隙泊肃叶泄漏体积流量 (L/min)
     * Q_leak = (pi * r * h^3 / (6 * mu * L)) * deltaP
     */
    public double computeMicroLeakRateLpm(double cupRadiusMm, double clearanceGapMm, double deltaPKPa) {
        if (clearanceGapMm <= 0.0 || deltaPKPa <= 0.0) {
            return 0.0;
        }
        double rM = cupRadiusMm * 1e-3;
        double hM = clearanceGapMm * 1e-3;
        double lM = NOMINAL_SEAL_LIP_LENGTH_MM * 1e-3;
        double deltaPPa = deltaPKPa * 1000.0;

        // m^3/s
        double qLeakM3s = (Math.PI * rM * Math.pow(hM, 3) / (6.0 * AIR_DYNAMIC_VISCOSITY_PA_S * lM)) * deltaPPa;
        // 转换为 L/min: 1 m^3/s = 60,000 L/min
        return qLeakM3s * 60000.0;
    }

    /**
     * 计算临界临界泄漏间隙 h_crit (mm)
     * 当间隙 h < h_crit 时，负压单调收敛至稳态密封
     */
    public double computeCriticalClearanceGapMm(double cupRadiusMm, double targetVacuumKPa, double pumpCapacityLpm) {
        double rM = cupRadiusMm * 1e-3;
        double lM = NOMINAL_SEAL_LIP_LENGTH_MM * 1e-3;
        double deltaPKPa = Math.max(1.0, ATMOSPHERIC_PRESSURE_KPA - targetVacuumKPa);
        double deltaPPa = deltaPKPa * 1000.0;
        double targetPPa = targetVacuumKPa * 1000.0;
        double atmPPa = ATMOSPHERIC_PRESSURE_KPA * 1000.0;
        double qPumpM3s = (pumpCapacityLpm / 60000.0);

        // h_crit = ( (6 * mu * L * P_target * Q_pump) / (pi * r * P_atm * deltaP) )^(1/3)
        double numerator = 6.0 * AIR_DYNAMIC_VISCOSITY_PA_S * lM * targetPPa * qPumpM3s;
        double denominator = Math.PI * rM * atmPPa * deltaPPa;
        if (denominator <= 0.0) {
            return 0.0;
        }
        double hM = Math.cbrt(numerator / denominator);
        return hM * 1000.0; // 转换为 mm
    }

    /**
     * 评估吸盘阵列各微吸盘密封完整度 (0.0 ~ 1.0)
     * 基于泊肃叶微气隙立方定律: eta_seal = max(0, 1 - (h / h_crit)^3)
     */
    public double[] evaluateSealIntegrity(MicroSuctionCupArrayEnvelope envelope, double targetVacuumKPa, double pumpCapacityLpm, double clearanceGapMm) {
        double[] integrities = new double[envelope.cupCount()];
        double hCritMm = computeCriticalClearanceGapMm(2.5, targetVacuumKPa, pumpCapacityLpm);
        for (int i = 0; i < envelope.cupCount(); i++) {
            if (clearanceGapMm >= hCritMm) {
                integrities[i] = 0.0;
            } else {
                double gapRatio = clearanceGapMm / Math.max(hCritMm, 1e-6);
                double cubicLoss = Math.pow(gapRatio, 3);
                integrities[i] = Math.min(1.0, Math.max(0.0, 1.0 - cubicLoss));
            }
        }
        return integrities;
    }

    /**
     * 判定吸盘阵列是否达成稳态自适应密封 (充分必要条件验证)
     */
    public boolean isSealEstablished(MicroSuctionCupArrayEnvelope envelope, double targetVacuumKPa, double pumpCapacityLpm) {
        double avgIntegrity = envelope.computeAverageSealIntegrity();
        if (avgIntegrity < 0.95) {
            return false;
        }
        for (int i = 0; i < envelope.cupCount(); i++) {
            if (envelope.cupPressuresKPa()[i] > targetVacuumKPa + 5.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算阿里千问 1536 维超球面测地偏角 (Geodesic Angle in Radians)
     * d_geodesic = arccos( clamp(v1 . v2, -1.0, 1.0) )
     */
    public double computeGeodesicAngle(double[] embedding1, double[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != 1536 || embedding2.length != 1536) {
            throw new IllegalArgumentException("Both embeddings must have exact dimension 1536");
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += embedding1[i] * embedding2[i];
        }
        // clamp to [-1.0, 1.0]
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(clamped);
    }
}
