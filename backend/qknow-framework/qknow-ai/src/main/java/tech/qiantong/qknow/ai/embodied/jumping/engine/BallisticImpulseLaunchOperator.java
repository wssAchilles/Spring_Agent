package tech.qiantong.qknow.ai.embodied.jumping.engine;

import java.util.Objects;

/**
 * 纯 Java 21 非线性刚柔耦合储能爆发起跳动力学算子 (定理 1.1)。
 * 解析求解串联弹性执行器 (SEA) 非线性四阶势能蓄积与爆发释能反向映射，
 * 并基于目标抛物线弹道反解起跳线速度矢量与下蹲净推伸冲量。
 */
public class BallisticImpulseLaunchOperator {

    public static final double DEFAULT_GRAVITY = 9.80665;
    private final double robotMassKg;
    private final double k1LinearStiffness;
    private final double k2NonlinearStiffness;
    private final int numSeaActuators;
    private final double gravity;

    public record LaunchSolution(
            double[] desiredTakeoffVelocity,
            double[] desiredLaunchImpulseNs,
            double requiredElasticEnergyJoules,
            double perActuatorCompressionMeters,
            double flightTimeSeconds,
            double apexHeightMeters,
            double[] predictedLandingPos,
            double landingPositionErrorMeters,
            long latencyMicros
    ) {}

    public BallisticImpulseLaunchOperator(double robotMassKg, double k1LinearStiffness,
                                          double k2NonlinearStiffness, int numSeaActuators, double gravity) {
        if (robotMassKg <= 0 || k1LinearStiffness <= 0 || k2NonlinearStiffness <= 0 || numSeaActuators <= 0) {
            throw new IllegalArgumentException("动力学参数必须为严格正数");
        }
        this.robotMassKg = robotMassKg;
        this.k1LinearStiffness = k1LinearStiffness;
        this.k2NonlinearStiffness = k2NonlinearStiffness;
        this.numSeaActuators = numSeaActuators;
        this.gravity = gravity > 0 ? gravity : DEFAULT_GRAVITY;
    }

    public BallisticImpulseLaunchOperator() {
        this(45.0, 12000.0, 85000.0, 4, DEFAULT_GRAVITY);
    }

    /**
     * 抛物线弹道逆运动学解析求解与四阶非线性势能闭式求根 (定理 1.1)。
     *
     * @param initialPos 当前起跳质心坐标 [x0, y0, z0]
     * @param targetLandingPos 期望着陆目标点坐标 [xf, yf, zf]
     * @param targetApexHeight 期望弹道最高顶点高度 (m, 必须 > max(z0, zf))
     * @param pushStrokeMeters 腿部推伸垂直行程 (m, 预压缩到离地质心位移)
     * @return 包含起跳速度、冲量、预压量与落点精度的解析解
     */
    public LaunchSolution solveLaunchTrajectory(double[] initialPos, double[] targetLandingPos,
                                                double targetApexHeight, double pushStrokeMeters) {
        long startNanos = System.nanoTime();
        Objects.requireNonNull(initialPos, "initialPos 不能为空");
        Objects.requireNonNull(targetLandingPos, "targetLandingPos 不能为空");

        double x0 = initialPos[0];
        double y0 = initialPos[1];
        double z0 = initialPos[2];

        double xf = targetLandingPos[0];
        double yf = targetLandingPos[1];
        double zf = targetLandingPos[2];

        double apex = Math.max(targetApexHeight, Math.max(z0, zf) + 0.05);

        // 1. 垂直弹道解析反求初速度 vz0*
        double vz0 = Math.sqrt(2.0 * gravity * (apex - z0));

        // 2. 飞行总时间 tf* 解析求解 (求二次方程正实根: -0.5*g*t^2 + vz0*t + (z0 - zf) = 0)
        double discr = vz0 * vz0 + 2.0 * gravity * (z0 - zf);
        if (discr < 0) {
            discr = 0.0;
        }
        double flightTime = (vz0 + Math.sqrt(discr)) / gravity;

        // 3. 水平初速度反解
        double vx0 = flightTime > 1e-4 ? (xf - x0) / flightTime : 0.0;
        double vy0 = flightTime > 1e-4 ? (yf - y0) / flightTime : 0.0;
        double[] desiredTakeoffVel = new double[]{vx0, vy0, vz0};

        // 4. 起跳净推力冲量 p_launch = m * v_takeoff
        double[] desiredImpulse = new double[]{
                robotMassKg * vx0,
                robotMassKg * vy0,
                robotMassKg * vz0
        };

        // 5. 机械能守恒反解所需弹性储能: E_req = 0.5 * m * ||v||^2 + m * g * pushStroke
        double vNormSq = vx0 * vx0 + vy0 * vy0 + vz0 * vz0;
        double kineticEnergy = 0.5 * robotMassKg * vNormSq;
        double gravityWork = robotMassKg * gravity * Math.max(0.0, pushStrokeMeters);
        double totalRequiredEnergy = kineticEnergy + gravityWork;

        // 6. 双二次方程闭式一阶求根导出单执行器预压缩量 x0* (定理 1.1)
        // n * (0.5 * k1 * x^2 + 0.25 * k2 * x^4) = E_target
        // k2 * u^2 + 2 * k1 * u - (4 * E_target / n) = 0, where u = x^2
        double energyPerActuator = totalRequiredEnergy / numSeaActuators;
        double discrU = 4.0 * k1LinearStiffness * k1LinearStiffness + 16.0 * k2NonlinearStiffness * energyPerActuator;
        double uStar = (-2.0 * k1LinearStiffness + Math.sqrt(discrU)) / (2.0 * k2NonlinearStiffness);
        double perActuatorCompression = uStar > 0 ? Math.sqrt(uStar) : 0.0;

        // 7. 闭式前向推演落点并计算理论误差界
        double predX = x0 + vx0 * flightTime;
        double predY = y0 + vy0 * flightTime;
        double predZ = z0 + vz0 * flightTime - 0.5 * gravity * flightTime * flightTime;
        double[] predLandingPos = new double[]{predX, predY, predZ};

        double errX = predX - xf;
        double errY = predY - yf;
        double errZ = predZ - zf;
        double posErrorMeters = Math.sqrt(errX * errX + errY * errY + errZ * errZ);

        long latencyMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);

        return new LaunchSolution(
                desiredTakeoffVel,
                desiredImpulse,
                totalRequiredEnergy,
                perActuatorCompression,
                flightTime,
                apex,
                predLandingPos,
                posErrorMeters,
                latencyMicros
        );
    }
}
