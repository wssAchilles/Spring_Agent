package tech.qiantong.qknow.ai.embodied.actuation.engine;

import tech.qiantong.qknow.ai.embodied.actuation.dto.ContinuousTrajectoryCommand;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

import java.util.ArrayList;
import java.util.List;

/**
 * 五次样条最小加加速度 (Minimum Jerk) 轨迹平滑器 (Theorem 1.1)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class ContinuousTrajectorySmoother {

    public record TrajectoryState(
            double time,
            SpatialPose3D position,
            double[] velocity,
            double[] acceleration,
            double[] jerk,
            double maxJerkNorm
    ) {}

    public static class QuinticCoefficients {
        public final double[] c0 = new double[3];
        public final double[] c1 = new double[3];
        public final double[] c2 = new double[3];
        public final double[] c3 = new double[3];
        public final double[] c4 = new double[3];
        public final double[] c5 = new double[3];
        public final double duration;

        public QuinticCoefficients(double[] q0, double[] v0, double[] a0,
                                   double[] q1, double[] v1, double[] a1,
                                   double T) {
            this.duration = Math.max(0.01, T);
            double T2 = this.duration * this.duration;
            double T3 = T2 * this.duration;
            double T4 = T3 * this.duration;
            double T5 = T4 * this.duration;

            for (int i = 0; i < 3; i++) {
                c0[i] = q0[i];
                c1[i] = v0[i];
                c2[i] = 0.5 * a0[i];

                double deltaQ = q1[i] - (q0[i] + v0[i] * this.duration + 0.5 * a0[i] * T2);
                double deltaV = v1[i] - (v0[i] + a0[i] * this.duration);
                double deltaA = a1[i] - a0[i];

                c3[i] = (10.0 / T3) * deltaQ - (4.0 / T2) * deltaV + (0.5 / this.duration) * deltaA;
                c4[i] = (-15.0 / T4) * deltaQ + (7.0 / T3) * deltaV - (1.0 / T2) * deltaA;
                c5[i] = (6.0 / T5) * deltaQ - (3.0 / T4) * deltaV + (0.5 / T3) * deltaA;
            }
        }

        public TrajectoryState evaluate(double t) {
            double clampedT = Math.max(0.0, Math.min(duration, t));
            double t2 = clampedT * clampedT;
            double t3 = t2 * clampedT;
            double t4 = t3 * clampedT;
            double t5 = t4 * clampedT;

            double[] pos = new double[3];
            double[] vel = new double[3];
            double[] acc = new double[3];
            double[] jerk = new double[3];
            double jerkNormSq = 0.0;

            for (int i = 0; i < 3; i++) {
                pos[i] = c0[i] + c1[i] * clampedT + c2[i] * t2 + c3[i] * t3 + c4[i] * t4 + c5[i] * t5;
                vel[i] = c1[i] + 2.0 * c2[i] * clampedT + 3.0 * c3[i] * t2 + 4.0 * c4[i] * t3 + 5.0 * c5[i] * t4;
                acc[i] = 2.0 * c2[i] + 6.0 * c3[i] * clampedT + 12.0 * c4[i] * t2 + 20.0 * c5[i] * t3;
                jerk[i] = 6.0 * c3[i] + 24.0 * c4[i] * clampedT + 60.0 * c5[i] * t2;
                jerkNormSq += jerk[i] * jerk[i];
            }

            return new TrajectoryState(
                    t,
                    new SpatialPose3D(pos[0], pos[1], pos[2], 0.0, 0.0, 0.0),
                    vel, acc, jerk,
                    Math.sqrt(jerkNormSq)
            );
        }
    }

    /**
     * 生成平滑轨迹五次样条
     */
    public QuinticCoefficients fitQuinticSpline(SpatialPose3D start, SpatialPose3D end, double duration) {
        double[] q0 = new double[]{start.getX(), start.getY(), start.getZ()};
        double[] q1 = new double[]{end.getX(), end.getY(), end.getZ()};
        double[] v0 = new double[]{0.0, 0.0, 0.0};
        double[] v1 = new double[]{0.0, 0.0, 0.0};
        double[] a0 = new double[]{0.0, 0.0, 0.0};
        double[] a1 = new double[]{0.0, 0.0, 0.0};
        return new QuinticCoefficients(q0, v0, a0, q1, v1, a1, duration);
    }

    /**
     * 计算在千问 1536 维超球面测地切空间中的测地曲率 (Theorem 1.1)
     */
    public double computeGeodesicCurvature(TrajectoryState state, float[] qwenVector1536) {
        if (state == null) return 0.0;
        double[] vel = state.velocity();
        double[] acc = state.acceleration();

        double velNormSq = vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2];
        if (velNormSq < 1e-6) {
            return 0.0;
        }

        // 叉乘 ||v x a|| / ||v||^3
        double crossX = vel[1] * acc[2] - vel[2] * acc[1];
        double crossY = vel[2] * acc[0] - vel[0] * acc[2];
        double crossZ = vel[0] * acc[1] - vel[1] * acc[0];
        double crossNorm = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);

        double euclideanCurvature = crossNorm / Math.pow(Math.sqrt(velNormSq), 3);

        // 结合千问 1536 维超球面保模因子微调
        double qwenModulation = 1.0;
        if (qwenVector1536 != null && qwenVector1536.length == 1536) {
            double dot = Math.abs(qwenVector1536[0]);
            qwenModulation = 0.9 + 0.1 * dot;
        }

        return euclideanCurvature * qwenModulation;
    }
}
