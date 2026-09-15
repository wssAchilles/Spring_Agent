package tech.qiantong.qknow.ai.embodied.fluid.dto;

import java.util.Objects;

/**
 * 流体分注与微观界面控制指令 (Java 21 Record)
 * <p>
 * 封装分注任务标识、流体流变学介质类型、幂律稠度系数、流变指数、特征松弛时间、
 * 目标分注体积、针嘴几何尺寸、反转微步回抽位移与物理时间戳。
 *
 * @param commandId               分注控制指令唯一标识
 * @param liquidType              流体介质流变分类 ("NEWTONIAN", "SHEAR_THINNING", "SHEAR_THICKENING")
 * @param consistencyIndexK       Ostwald-de Waele 幂律稠度系数 K (Pa*s^n)
 * @param flowBehaviorIndexN      幂律流变指数 n (n=1: 牛顿, n<1: 剪切变稀, n>1: 剪切变稠)
 * @param relaxationTimeLambda    聚合物大分子链特征松弛时间 lambda_E (s)
 * @param targetVolumeMl          期望分注体积 (mL)
 * @param nozzleDiameterMm        分注针嘴内径 d_nozzle (mm)
 * @param suckBackDistanceMm      收胶微步反向回抽位移 delta_x_retract (mm)
 * @param timestampMs             指令签发物理时间戳 (ms)
 */
public record LiquidDispensingCommand(
        String commandId,
        String liquidType,
        double consistencyIndexK,
        double flowBehaviorIndexN,
        double relaxationTimeLambda,
        double targetVolumeMl,
        double nozzleDiameterMm,
        double suckBackDistanceMm,
        long timestampMs
) {
    public LiquidDispensingCommand {
        Objects.requireNonNull(commandId, "commandId 不能为空");
        Objects.requireNonNull(liquidType, "liquidType 不能为空");
        if (consistencyIndexK <= 0) {
            throw new IllegalArgumentException("稠度系数 K 必须大于 0");
        }
        if (flowBehaviorIndexN <= 0) {
            throw new IllegalArgumentException("流变指数 n 必须大于 0");
        }
        if (targetVolumeMl <= 0) {
            throw new IllegalArgumentException("目标分注体积必须大于 0");
        }
    }
}
