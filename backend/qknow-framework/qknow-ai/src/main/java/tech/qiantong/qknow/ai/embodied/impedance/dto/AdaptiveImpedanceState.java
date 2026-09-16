package tech.qiantong.qknow.ai.embodied.impedance.dto;

/**
 * 自适应阻抗控制器瞬态状态快照 (Java 21 Record)
 * 记录三阶段运行状态、阻抗刚度/阻尼范数、储能罐能量余量、法向力与安全门禁指标。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public record AdaptiveImpedanceState(
        String phaseName,              // FREE_MOTION / TACTILE_CAPTURE / PRECISION_ASSEMBLY
        double currentStiffnessNorm,   // 当前末端阻抗刚度范数 (N/m)
        double currentDampingNorm,     // 当前末端阻抗阻尼范数 (N*s/m)
        double energyTankLevelJoules,  // 虚拟储能罐当前能量剩余 (J)
        double normalContactForceN,    // 瞬态法向接触力 (N)
        double shearForceMargin,       // 剪切滑移安全裕度 (0.0 ~ 1.0)
        double hocbfSafetyMargin,      // 高阶控制屏障裕度 (>= 0 为绝对安全)
        boolean safetyGateActive       // 是否触发 QP 正交安全投影修正
) {}
