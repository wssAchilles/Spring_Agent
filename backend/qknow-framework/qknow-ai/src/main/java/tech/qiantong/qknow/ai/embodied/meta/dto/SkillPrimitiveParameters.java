package tech.qiantong.qknow.ai.embodied.meta.dto;

import java.util.Arrays;

/**
 * 技能元动力学与阻抗参数集 (Java 21 Record)
 * 包含 6 维对角刚度矩阵 K、阻尼矩阵 D、期望参考力 F_ref 以及螺距导程 Pitch
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record SkillPrimitiveParameters(
        double[] stiffnessK,        // 6 维对角刚度 [Kx, Ky, Kz, Krx, Kry, Krz] (N/m, Nm/rad)
        double[] dampingD,          // 6 维阻尼比对角 [Dx, Dy, Dz, Drx, Dry, Drz] (Ns/m, Nms/rad)
        double[] referenceForce,    // 6 维期望目标力 [Fx, Fy, Fz, Tx, Ty, Tz] (N, Nm)
        double pitchLeadMeter       // 螺旋导程 (仅 SCREWING 技能有效，单位 m/rad，非旋拧设为 0.0)
) {
    public SkillPrimitiveParameters {
        if (stiffnessK == null || stiffnessK.length != 6) {
            throw new IllegalArgumentException("stiffnessK must be an array of length 6");
        }
        if (dampingD == null || dampingD.length != 6) {
            throw new IllegalArgumentException("dampingD must be an array of length 6");
        }
        if (referenceForce == null || referenceForce.length != 6) {
            throw new IllegalArgumentException("referenceForce must be an array of length 6");
        }
    }

    public SkillPrimitiveParameters copyWithUpdatedStiffnessAndForce(double[] newK, double[] newF) {
        return new SkillPrimitiveParameters(
                Arrays.copyOf(newK, 6),
                Arrays.copyOf(dampingD, 6),
                Arrays.copyOf(newF, 6),
                pitchLeadMeter
        );
    }
}
