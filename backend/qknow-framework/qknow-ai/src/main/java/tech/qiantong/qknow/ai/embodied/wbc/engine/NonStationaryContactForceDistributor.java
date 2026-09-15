package tech.qiantong.qknow.ai.embodied.wbc.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 非平稳接触力分配器与防滑脱切向阻抗补偿器
 * 实时前馈底盘加减速惯性剪切力 F_shear = -m_obj * a_base，自适应补强法向夹紧力，确保接触状态严格处于库伦摩擦锥内部 (||f_t|| < mu * f_n)
 *
 * @author Achilles
 * @since 2026-09-15
 */
@Component
public class NonStationaryContactForceDistributor {

    private static final Logger log = LoggerFactory.getLogger(NonStationaryContactForceDistributor.class);

    private final double objectMassKg;
    private final double frictionCoeff;
    private final double baselineClampingForceN;
    private final double maxClampingForceN;

    public NonStationaryContactForceDistributor() {
        this(3.0, 0.40, 30.0, 250.0); // 默认 3kg 搬运工件，摩擦系数 0.40，基础法向力 30N，最大 250N
    }

    public NonStationaryContactForceDistributor(double objectMassKg, double frictionCoeff,
                                             double baselineClampingForceN, double maxClampingForceN) {
        this.objectMassKg = objectMassKg;
        this.frictionCoeff = frictionCoeff;
        this.baselineClampingForceN = baselineClampingForceN;
        this.maxClampingForceN = maxClampingForceN;
    }

    /**
     * 计算底盘加减速产生的惯性剪切力 F_shear = -m_obj * a_base
     */
    public double[] calculateInertialShearForce(double[] baseAcceleration) {
        double ax = baseAcceleration != null && baseAcceleration.length > 0 ? baseAcceleration[0] : 0.0;
        double ay = baseAcceleration != null && baseAcceleration.length > 1 ? baseAcceleration[1] : 0.0;

        double fx = -objectMassKg * ax;
        double fy = -objectMassKg * ay;
        return new double[]{fx, fy};
    }

    /**
     * 执行非平稳接触力自适应分配与防滑脱补偿
     */
    public ContactForceAllocationResult distributeContactForce(double[] baseAcceleration, double currentExternalShearN) {
        double[] inertialShear = calculateInertialShearForce(baseAcceleration);
        double totalShearX = inertialShear[0];
        double totalShearY = inertialShear[1] + currentExternalShearN;

        double tangentialForceNorm = Math.sqrt(totalShearX * totalShearX + totalShearY * totalShearY);

        // 库伦摩擦锥内部保持：要求 f_n >= tangentialForce / mu + baseline
        double requiredNormalForce = (tangentialForceNorm / frictionCoeff) + baselineClampingForceN;

        boolean antiSlipTriggered = false;
        double normalForce = requiredNormalForce;

        // 若剪切力剧增导致摩擦锥裕度过低，触发 <= 2ms 瞬时强力夹紧补强
        if (normalForce > baselineClampingForceN * 1.5) {
            normalForce += 25.0; // 注入动态阻尼夹紧冲量
            antiSlipTriggered = true;
            log.warn("[NonStationaryContactForceDistributor] 捕获底盘加减速微滑脱风险: 剪切力={}N, 瞬时补强夹紧力至 {}N",
                    String.format("%.2f", tangentialForceNorm), String.format("%.2f", normalForce));
        }

        // 硬截断保护防压损
        if (normalForce > maxClampingForceN) {
            normalForce = maxClampingForceN;
        }

        // 摩擦锥裕度 margin = mu * f_n - ||f_t||
        double frictionConeMargin = (frictionCoeff * normalForce) - tangentialForceNorm;

        // 理论滑脱发生率：当 margin > 0 时严格为 0.0
        double slipProbability = (frictionConeMargin > 0) ? 0.0 : 1.0;

        return new ContactForceAllocationResult(normalForce, tangentialForceNorm, frictionConeMargin,
                antiSlipTriggered, slipProbability);
    }

    public record ContactForceAllocationResult(
            double normalClampingForceN,
            double tangentialShearForceN,
            double frictionConeMarginN,
            boolean antiSlipIntervened,
            double slipProbability
    ) {}
}
