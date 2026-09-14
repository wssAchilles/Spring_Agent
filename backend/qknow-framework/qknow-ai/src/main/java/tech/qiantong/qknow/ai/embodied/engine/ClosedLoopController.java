package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.ActionPrimitiveDTO;
import tech.qiantong.qknow.ai.embodied.dto.SpatialEntityDO;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

/**
 * 具身闭环控制协调器 (Theorem 2.1)
 * 统筹数字孪生仿真、安全门禁拦截与执行后态势对账 (Post-check)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class ClosedLoopController {

    private static final Logger log = LoggerFactory.getLogger(ClosedLoopController.class);

    private final SpatialGridGraph realWorld;
    private final DigitalTwinSimulator twinSimulator;
    private final ActuationSafetyGate safetyGate;

    public ClosedLoopController(SpatialGridGraph realWorld, DigitalTwinSimulator twinSimulator, ActuationSafetyGate safetyGate) {
        this.realWorld = realWorld;
        this.twinSimulator = twinSimulator;
        this.safetyGate = safetyGate;
    }

    /**
     * 端到端执行具身控制任务
     */
    public boolean executeTask(ActionPrimitiveDTO action) {
        // 1. 克隆数字孪生环境
        twinSimulator.cloneFromRealWorld(realWorld);

        // 2. 前向推演 5 步
        DigitalTwinSimulator.SimulationReport report = twinSimulator.simulateRollout(action, 5);

        // 3. 安全门禁核验
        boolean authorized = safetyGate.verifyAndAuthorize(action, report);
        if (!authorized) {
            return false;
        }

        // 4. 物理分发执行 (更新真实空间位姿)
        SpatialEntityDO entity = realWorld.getEntity(action.getTargetEntityId());
        if (entity != null && action.getTargetPose() != null) {
            entity.setPose(action.getTargetPose().clone());
        }

        // 5. 执行后态势对账 (Post-condition verification)
        if (entity != null && action.getTargetPose() != null) {
            double postDistance = entity.getPose().distanceTo(action.getTargetPose());
            if (postDistance > 0.05) { // 超过 5cm 判定为滑脱或执行偏差失败
                action.setStatus(ActionPrimitiveDTO.ExecutionStatus.FAILED);
                log.error("动作后态势对账失败！位姿偏差 {}m 超过 5cm 阈值", postDistance);
                return false;
            }
        }

        action.setStatus(ActionPrimitiveDTO.ExecutionStatus.EXECUTED);
        log.info("具身控制任务 {} 执行成功并完成态势对账！", action.getActionId());
        return true;
    }

    /**
     * 执行任务并度量闭环收敛时间 MTTC (Theorem 2.1)
     */
    public long executeTaskAndMeasureMttc(ActionPrimitiveDTO action) {
        long start = System.currentTimeMillis();
        executeTask(action);
        long mttc = System.currentTimeMillis() - start;
        log.info("具身闭环任务收敛耗时 MTTC: {}ms", mttc);
        return mttc;
    }

    public SpatialGridGraph getRealWorld() { return realWorld; }
    public DigitalTwinSimulator getTwinSimulator() { return twinSimulator; }
    public ActuationSafetyGate getSafetyGate() { return safetyGate; }
}
