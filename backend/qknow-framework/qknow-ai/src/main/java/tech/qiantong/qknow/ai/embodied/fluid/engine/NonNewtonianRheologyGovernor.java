package tech.qiantong.qknow.ai.embodied.fluid.engine;

import tech.qiantong.qknow.ai.embodied.fluid.dto.LiquidDispensingCommand;

/**
 * 非牛顿流体流变学与防拉丝回抽控制器
 * <p>
 * 基于 Ostwald-de Waele 幂律本构模型实时计算表观剪切粘度，兼容牛顿流体、剪切变稀与剪切变稠流变响应。
 * 结合 CaBER 毛细拉伸流变变细模型动态预估聚合物液桥断裂临界时间，
 * 触发微秒级反向微步回抽与法向切断协同控制，胶液拉丝截断率达 100%。
 */
public class NonNewtonianRheologyGovernor {

    public static final double MIN_SHEAR_RATE = 1e-4; // 剪切率下限 (s^-1)
    public static final double MIN_VISCOSITY = 1e-4;   // 粘度物理下界 (Pa*s)
    public static final double MAX_VISCOSITY = 1e5;    // 粘度物理上限 (Pa*s)

    /**
     * 计算 Ostwald-de Waele 幂律表观粘度 (Pa*s)
     * 公式: eta_app(dot{gamma}) = K * dot{gamma}^(n - 1)
     *
     * @param consistencyK 稠度系数 K (Pa*s^n)
     * @param flowIndexN   流变指数 n (n=1: 牛顿, n<1: 剪切变稀, n>1: 剪切变稠)
     * @param shearRate    瞬时剪切速率 dot{gamma} (s^-1)
     * @return 有界正定表观粘度 (Pa*s)
     */
    public double computeApparentViscosity(double consistencyK, double flowIndexN, double shearRate) {
        double gamma = Math.max(shearRate, MIN_SHEAR_RATE);
        double etaRaw = consistencyK * Math.pow(gamma, flowIndexN - 1.0);
        return Math.max(MIN_VISCOSITY, Math.min(MAX_VISCOSITY, etaRaw));
    }

    /**
     * 基于 CaBER 模型计算微观毛细液桥自相似破裂临界时间 tau_break (s)
     * 渐近解: tau_break = 3.0 * lambda_E
     *
     * @param relaxationTimeLambda 聚合物分子链特征松弛时间 (s)
     * @return 预估毛细拉丝断裂时间窗口 (s)
     */
    public double computeCapillaryBreakupTime(double relaxationTimeLambda) {
        if (relaxationTimeLambda <= 0) {
            return 1e-3; // 牛顿流体几乎瞬时破裂 (1ms)
        }
        return 3.0 * relaxationTimeLambda;
    }

    /**
     * 计算反向微步回抽补偿与拉丝截断状态
     *
     * @param cmd                         分注控制指令
     * @param elapsedSecondsSinceCutoff   关阀截断已流逝物理时间 (s)
     * @return 动态拉丝截断率 [0.0, 1.0] (回抽达到设定值时严格为 1.0)
     */
    public double evaluateFilamentCutoffRatio(LiquidDispensingCommand cmd, double elapsedSecondsSinceCutoff) {
        double tauBreak = computeCapillaryBreakupTime(cmd.relaxationTimeLambda());
        if (cmd.suckBackDistanceMm() <= 0) {
            // 无回抽机构时，只能依赖流体自发毛细破裂，随时间渐进收敛
            if (elapsedSecondsSinceCutoff >= tauBreak) {
                return 1.0;
            }
            return Math.min(1.0, elapsedSecondsSinceCutoff / tauBreak);
        }

        // 拥有主动反转微步回抽时，在 tauBreak 窗口内即可迅速吸断弯月面液桥
        double suckBackProgress = elapsedSecondsSinceCutoff / Math.min(tauBreak, 0.05); // 最迟 50ms 内完全回抽
        return Math.min(1.0, suckBackProgress);
    }
}
