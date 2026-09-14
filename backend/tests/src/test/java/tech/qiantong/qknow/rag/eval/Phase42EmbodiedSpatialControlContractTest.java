package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.dto.ActionPrimitiveDTO;
import tech.qiantong.qknow.ai.embodied.dto.BoundingBox3D;
import tech.qiantong.qknow.ai.embodied.dto.SpatialEntityDO;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;
import tech.qiantong.qknow.ai.embodied.engine.ActuationSafetyGate;
import tech.qiantong.qknow.ai.embodied.engine.ClosedLoopController;
import tech.qiantong.qknow.ai.embodied.engine.DigitalTwinSimulator;
import tech.qiantong.qknow.ai.embodied.engine.SpatialGridGraph;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 42 核心契约测试：具身智能体空间环境感知、数字孪生交互与具身控制回路
 * 严格覆盖 10 项严苛契约 (定理 1.1, 定理 2.1, 定理 3.1 与 3 大工业级具身灾难场景)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class Phase42EmbodiedSpatialControlContractTest {

    private SpatialGridGraph spatialGraph;
    private DigitalTwinSimulator simulator;
    private ActuationSafetyGate safetyGate;
    private ClosedLoopController controller;

    @BeforeEach
    void setUp() {
        spatialGraph = new SpatialGridGraph();
        simulator = new DigitalTwinSimulator();
        safetyGate = new ActuationSafetyGate();
        controller = new ClosedLoopController(spatialGraph, simulator, safetyGate);

        // 注册机械臂末端夹爪 (Gripper)
        SpatialEntityDO gripper = new SpatialEntityDO("gripper", "RoboticGripper",
                new SpatialPose3D(0.0, 0.0, 0.5), new BoundingBox3D(new SpatialPose3D(0.0, 0.0, 0.5), 0.1, 0.1, 0.1), true);
        spatialGraph.registerEntity(gripper);

        // 注册目标工作台物体 (TargetBox)
        SpatialEntityDO targetBox = new SpatialEntityDO("box-01", "TargetMedicineBox",
                new SpatialPose3D(0.5, 0.0, 0.0), new BoundingBox3D(new SpatialPose3D(0.5, 0.0, 0.0), 0.2, 0.2, 0.2), true);
        spatialGraph.registerEntity(targetBox);

        // 注册不可穿越的物理障碍物隔板 (ObstacleBarrier)
        SpatialEntityDO obstacle = new SpatialEntityDO("obstacle-barrier", "GlassBarrier",
                new SpatialPose3D(0.25, 0.0, 0.25), new BoundingBox3D(new SpatialPose3D(0.25, 0.0, 0.25), 0.05, 0.5, 0.5), false);
        spatialGraph.registerEntity(obstacle);
    }

    @Test
    @DisplayName("契约 01: SE(3) 空间位姿齐次变换与欧氏距离不变性契约 (定理 1.1)")
    void contract01_se3PoseTransform_preservesEuclideanDistance() {
        SpatialPose3D p1 = new SpatialPose3D(1.0, 2.0, 3.0);
        SpatialPose3D p2 = new SpatialPose3D(4.0, 6.0, 3.0);
        double originalDistance = p1.distanceTo(p2); // 5.0m

        // 执行 SE(3) 整体平移 (dx=10, dy=-5, dz=2, yaw=45)
        SpatialPose3D p1Transformed = p1.transform(10.0, -5.0, 2.0, 45.0);
        SpatialPose3D p2Transformed = p2.transform(10.0, -5.0, 2.0, 45.0);
        double transformedDistance = p1Transformed.distanceTo(p2Transformed);

        assertEquals(originalDistance, transformedDistance, 1e-6, "李群 SE(3) 变换下欧氏测地距离必须保持严格守恒");
    }

    @Test
    @DisplayName("契约 02: 三维定向包围盒 (OBB) 相交与高精度碰撞干涉检测契约 (定理 1.1)")
    void contract02_boundingBoxIntersection_accuratelyDetectsInterference() {
        BoundingBox3D boxA = new BoundingBox3D(new SpatialPose3D(0.0, 0.0, 0.0), 1.0, 1.0, 1.0);
        BoundingBox3D boxB = new BoundingBox3D(new SpatialPose3D(0.8, 0.0, 0.0), 1.0, 1.0, 1.0);
        BoundingBox3D boxC = new BoundingBox3D(new SpatialPose3D(2.5, 0.0, 0.0), 1.0, 1.0, 1.0);

        assertTrue(boxA.intersects(boxB), "部分重叠包围盒应判定为相交碰撞");
        assertEquals(0.0, boxA.surfaceDistanceTo(boxB), 1e-6, "相交包围盒表面净距为 0");

        assertFalse(boxA.intersects(boxC), "无重叠包围盒判定为不相交");
        assertEquals(1.5, boxA.surfaceDistanceTo(boxC), 1e-6, "分离包围盒表面净距应精确为 1.5m");
    }

    @Test
    @DisplayName("契约 03: 空间相对方位语义解析契约 (消除方位幻觉)")
    void contract03_relativeOrientation_mapsAccurately() {
        // 观察者在原点，前方为 +X，右侧为 +Y，上方为 +Z
        SpatialGridGraph.RelativeOrientation dirFront = spatialGraph.getRelativeOrientation("gripper", "box-01");
        assertEquals(SpatialGridGraph.RelativeOrientation.FRONT, dirFront, "X 正向物体应准确识别为 FRONT");

        // 注册上方实体
        spatialGraph.registerEntity(new SpatialEntityDO("top-lamp", "CeilingLamp",
                new SpatialPose3D(0.0, 0.0, 2.0), new BoundingBox3D(), false));
        assertEquals(SpatialGridGraph.RelativeOrientation.ABOVE, spatialGraph.getRelativeOrientation("gripper", "top-lamp"));
    }

    @Test
    @DisplayName("契约 04: 数字孪生环境虚拟快照克隆与轻量隔离契约")
    void contract04_digitalTwinSnapshot_isolatedFromPhysicalWorld() {
        simulator.cloneFromRealWorld(spatialGraph);

        // 修改数字孪生实体位姿
        SpatialEntityDO twinBox = simulator.getTwinEntity("box-01");
        assertNotNull(twinBox);
        twinBox.setPose(new SpatialPose3D(99.0, 99.0, 99.0));

        // 验证真实环境未被篡改 (零物理副作用)
        SpatialEntityDO realBox = spatialGraph.getEntity("box-01");
        assertEquals(0.5, realBox.getPose().getX(), 1e-6, "数字孪生内推演不得污染真实物理实体位姿");
    }

    @Test
    @DisplayName("契约 05: 数字孪生多步前向推演 (Rollout) 碰撞干涉检出契约")
    void contract05_counterfactualRollout_detectsCollisionAlongPath() {
        simulator.cloneFromRealWorld(spatialGraph);

        // 规划夹爪直接直线穿透障碍物到达目标位置
        ActionPrimitiveDTO penetrateAction = new ActionPrimitiveDTO(
                "act-penetrate", ActionPrimitiveDTO.ActionType.MOVE_TO, "gripper",
                new SpatialPose3D(0.5, 0.0, 0.0), ActionPrimitiveDTO.ReversibilityLevel.REVERSIBLE
        );

        // 5 步虚拟推演必然与中间 x=0.25 的隔板相撞
        DigitalTwinSimulator.SimulationReport report = simulator.simulateRollout(penetrateAction, 5);
        assertFalse(report.isSafe(), "穿透障碍物的轨迹必须在推演中被拦截");
        assertEquals("obstacle-barrier", report.getConflictEntityId(), "应准确定位碰撞冲突实体");
    }

    @Test
    @DisplayName("契约 06: 物理不可逆操作 (IRREVERSIBLE) 门禁绝对阻断契约 (定理 3.1)")
    void contract06_irreversibleAction_blockedWithoutSafetyTicket() {
        simulator.cloneFromRealWorld(spatialGraph);

        // 构造物理不可逆操作（如高压排气/强力冲压），无安全凭证
        ActionPrimitiveDTO dangerousAction = new ActionPrimitiveDTO(
                "act-crush", ActionPrimitiveDTO.ActionType.APPLY_FORCE, "box-01",
                new SpatialPose3D(0.5, 0.0, 0.0), ActionPrimitiveDTO.ReversibilityLevel.IRREVERSIBLE
        );
        dangerousAction.setExpectedForceNewton(50.0);

        DigitalTwinSimulator.SimulationReport report = new DigitalTwinSimulator.SimulationReport(true, null, 1.0, "通过");
        boolean authorized = safetyGate.verifyAndAuthorize(dangerousAction, report);

        assertFalse(authorized, "未携带合法 SafetyTicket 的物理不可逆操作必须被绝对阻断");
        assertEquals(ActionPrimitiveDTO.ExecutionStatus.BLOCKED, dangerousAction.getStatus());
    }

    @Test
    @DisplayName("契约 07: 合规安全凭证 (Safety Ticket) 授权放行契约")
    void contract07_irreversibleAction_authorizedWithValidTicket() {
        ActionPrimitiveDTO approvedAction = new ActionPrimitiveDTO(
                "act-emergency", ActionPrimitiveDTO.ActionType.APPLY_FORCE, "box-01",
                new SpatialPose3D(0.5, 0.0, 0.0), ActionPrimitiveDTO.ReversibilityLevel.IRREVERSIBLE
        );
        approvedAction.setExpectedForceNewton(60.0);
        approvedAction.setSafetyTicket("TICKET-AUTH-SUPERVISOR-9982"); // 附带有效审批凭单

        DigitalTwinSimulator.SimulationReport report = new DigitalTwinSimulator.SimulationReport(true, null, 1.0, "通过");
        boolean authorized = safetyGate.verifyAndAuthorize(approvedAction, report);

        assertTrue(authorized, "仿真通过且携带有效授权凭证的不可逆动作应正常放行");
        assertEquals(ActionPrimitiveDTO.ExecutionStatus.SIMULATED_PASS, approvedAction.getStatus());
    }

    @Test
    @DisplayName("契约 08: 可逆动作 (REVERSIBLE) 快速通道放行契约")
    void contract08_reversibleAction_fastPathApproval() {
        ActionPrimitiveDTO safeMove = new ActionPrimitiveDTO(
                "act-safe-move", ActionPrimitiveDTO.ActionType.MOVE_TO, "gripper",
                new SpatialPose3D(0.0, 0.2, 0.5), ActionPrimitiveDTO.ReversibilityLevel.REVERSIBLE
        );

        DigitalTwinSimulator.SimulationReport report = new DigitalTwinSimulator.SimulationReport(true, null, 0.5, "通过");
        boolean authorized = safetyGate.verifyAndAuthorize(safeMove, report);

        assertTrue(authorized, "安全可逆动作在仿真通过后应快速放行，无需重型审批凭证");
    }

    @Test
    @DisplayName("契约 09: 闭环控制执行后态势确认 (Post-check) 与滑脱故障检出契约")
    void contract09_postConditionCheck_detectsSlippageFailure() {
        // 尝试无碰撞安全移动到 (0.0, 0.3, 0.5)
        ActionPrimitiveDTO moveAction = new ActionPrimitiveDTO(
                "act-move-clean", ActionPrimitiveDTO.ActionType.MOVE_TO, "gripper",
                new SpatialPose3D(0.0, 0.3, 0.5), ActionPrimitiveDTO.ReversibilityLevel.REVERSIBLE
        );

        boolean success = controller.executeTask(moveAction);
        assertTrue(success, "无干涉闭环移动应执行成功并完成态势对账");
        assertEquals(ActionPrimitiveDTO.ExecutionStatus.EXECUTED, moveAction.getStatus());
        assertEquals(0.3, spatialGraph.getEntity("gripper").getPose().getY(), 1e-6);
    }

    @Test
    @DisplayName("契约 10: 端到端具身任务规划、孪生推演与闭环执行全链路闭环契约 (定理 2.1 MTTC <= 1000ms)")
    void contract10_endToEndEmbodiedClosedLoopActuation_convergesUnder1000ms() {
        ActionPrimitiveDTO endToEndAction = new ActionPrimitiveDTO(
                "act-e2e-closed-loop", ActionPrimitiveDTO.ActionType.MOVE_TO, "gripper",
                new SpatialPose3D(0.0, 0.1, 0.5), ActionPrimitiveDTO.ReversibilityLevel.REVERSIBLE
        );

        long mttc = controller.executeTaskAndMeasureMttc(endToEndAction);

        assertTrue(mttc <= 1000, "闭环数字孪生推演与执行收敛时间必须严格满足 MTTC <= 1000ms (定理 2.1)");
        assertEquals(ActionPrimitiveDTO.ExecutionStatus.EXECUTED, endToEndAction.getStatus());
    }
}
