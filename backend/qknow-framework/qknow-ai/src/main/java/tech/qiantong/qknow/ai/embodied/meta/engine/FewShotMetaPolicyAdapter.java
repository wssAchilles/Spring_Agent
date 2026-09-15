package tech.qiantong.qknow.ai.embodied.meta.engine;

import tech.qiantong.qknow.ai.embodied.meta.dto.SkillPrimitiveParameters;

import java.util.Arrays;

/**
 * Phase 69: 极速少样本一阶元策略梯度自适应求解器 (FewShotMetaPolicyAdapter)
 * 纯数学闭式解析求解，单步耗时 <= 10μs，绝无反向传播与本地大模型 (定理 1.2)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class FewShotMetaPolicyAdapter {

    private static final int MEMORY_WINDOW_SIZE = 5;
    private static final double LEARNING_RATE_ALPHA = 0.15; // 刚度一阶自适应步长
    private static final double LEARNING_RATE_BETA = 0.20;  // 期望力残差步长
    private static final double EPSILON = 1e-6;
    private static final double MAX_FORCE_LIMIT = 50.0;     // 物理防过载硬上限 50N
    private static final double WEDGING_VARIANCE_THRESH = 80.0; // 卡阻判定残差方差阈值

    private final double[][] residualHistory = new double[MEMORY_WINDOW_SIZE][6];
    private final double[][] displacementHistory = new double[MEMORY_WINDOW_SIZE][6];
    private int historyCount = 0;

    public void reset() {
        for (int i = 0; i < MEMORY_WINDOW_SIZE; i++) {
            Arrays.fill(residualHistory[i], 0.0);
            Arrays.fill(displacementHistory[i], 0.0);
        }
        historyCount = 0;
    }

    /**
     * 在线 5 步闭式快速自适应求解
     *
     * @param currentParams 当前技能参数
     * @param measuredForce 传感器当前测得 6 维接触力
     * @param displacementDx 相对上一控制周期的末端位移增量
     * @return 修正后的技能参数集与自锁判定结果
     */
    public AdaptationResult adapt(
            SkillPrimitiveParameters currentParams,
            double[] measuredForce,
            double[] displacementDx
    ) {
        long startNs = System.nanoTime();

        // 1. 计算 6 维当前力觉残差 r_k = F_meas - F_exp
        double[] r_k = new double[6];
        double[] f_exp = currentParams.referenceForce();
        for (int i = 0; i < 6; i++) {
            r_k[i] = measuredForce[i] - f_exp[i];
        }

        // 2. 压入滑动历史窗口 (定长 5 步)
        int idx = historyCount % MEMORY_WINDOW_SIZE;
        System.arraycopy(r_k, 0, residualHistory[idx], 0, 6);
        System.arraycopy(displacementDx, 0, displacementHistory[idx], 0, 6);
        historyCount++;
        int validSteps = Math.min(historyCount, MEMORY_WINDOW_SIZE);

        // 3. 计算当前步 6 维残差均方误差 MSE 与历史窗口 Z 轴方差
        double currentNormSq = 0.0;
        for (int i = 0; i < 6; i++) {
            currentNormSq += r_k[i] * r_k[i];
        }
        double fiveStepResidualMse = currentNormSq / 6.0;

        double rzMean = 0.0;
        for (int step = 0; step < validSteps; step++) {
            rzMean += residualHistory[step][2];
        }
        rzMean /= validSteps;
        double rzVariance = 0.0;
        for (int step = 0; step < validSteps; step++) {
            double diff = residualHistory[step][2] - rzMean;
            rzVariance += diff * diff;
        }
        rzVariance /= validSteps;

        // 4. 卡阻与自锁消除判定 (Anti-Wedging)
        boolean wedgingDetected = false;
        double dxNorm = 0.0;
        for (int i = 0; i < 3; i++) {
            dxNorm += displacementDx[i] * displacementDx[i];
        }
        dxNorm = Math.sqrt(dxNorm);

        // 若力觉残差剧烈波动 (方差超标) 且轴向位移基本停滞 (dx < 0.05mm)，判定为几何卡死
        if (rzVariance > WEDGING_VARIANCE_THRESH && dxNorm < 0.00005 && validSteps >= 3) {
            wedgingDetected = true;
        }

        // 5. 闭式解析更新刚度与参考力
        double[] newK = Arrays.copyOf(currentParams.stiffnessK(), 6);
        double[] newF = Arrays.copyOf(currentParams.referenceForce(), 6);

        if (wedgingDetected) {
            // 卡阻自愈模式：主动将法向力降至 0，降低横向刚度，释放微小晃动自由度
            newF[2] = 0.0;
            newK[0] *= 0.5; // X 轴刚度减半柔顺退让
            newK[1] *= 0.5; // Y 轴刚度减半柔顺退让
        } else {
            // 正常一阶残差泰勒对偶闭式更新
            double dispSq = 0.0;
            for (int i = 0; i < 6; i++) {
                dispSq += displacementDx[i] * displacementDx[i];
            }
            double denom = dispSq + EPSILON;

            for (int i = 0; i < 6; i++) {
                // 刚度增量: ΔK_i = - α * (r_k * dx_i) / (||dx||^2 + eps)
                double deltaK = -LEARNING_RATE_ALPHA * (r_k[i] * displacementDx[i]) / denom;
                // 限制单步刚度修正幅度在 ±20% 内，保持李雅普诺夫稳定性
                double maxStepK = currentParams.stiffnessK()[i] * 0.20;
                deltaK = Math.max(-maxStepK, Math.min(maxStepK, deltaK));
                newK[i] = Math.max(10.0, currentParams.stiffnessK()[i] + deltaK);

                // 参考力增量: ΔF_i = - β * r_k
                double deltaF = -LEARNING_RATE_BETA * r_k[i];
                newF[i] = currentParams.referenceForce()[i] + deltaF;

                // 物理上限硬截断防爆表
                if (Math.abs(newF[i]) > MAX_FORCE_LIMIT) {
                    newF[i] = Math.signum(newF[i]) * MAX_FORCE_LIMIT;
                }
            }
        }

        SkillPrimitiveParameters adaptedParams = currentParams.copyWithUpdatedStiffnessAndForce(newK, newF);
        long elapsedNs = System.nanoTime() - startNs;

        return new AdaptationResult(adaptedParams, fiveStepResidualMse, wedgingDetected, elapsedNs);
    }

    public record AdaptationResult(
            SkillPrimitiveParameters adaptedParams,
            double fiveStepResidualMse,
            boolean wedgingDetected,
            long computationTimeNs
    ) {
    }
}
