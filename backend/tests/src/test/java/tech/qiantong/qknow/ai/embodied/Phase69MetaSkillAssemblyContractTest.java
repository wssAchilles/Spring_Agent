package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.meta.dto.ContactSkillPrimitiveType;
import tech.qiantong.qknow.ai.embodied.meta.dto.MetaSkillExecutionReceipt;
import tech.qiantong.qknow.ai.embodied.meta.dto.SkillPrimitiveParameters;
import tech.qiantong.qknow.ai.embodied.meta.engine.ContactSkillPrimitiveCatalog;
import tech.qiantong.qknow.ai.embodied.meta.engine.CrossMorphologyMapper;
import tech.qiantong.qknow.ai.embodied.meta.engine.FewShotMetaPolicyAdapter;
import tech.qiantong.qknow.ai.embodied.meta.engine.MetaSkillControlBus;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 69 专属契约单元测试：具身智能体高维接触丰富操作的自适应技能元强化学习与跨实体策略泛化中枢
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class Phase69MetaSkillAssemblyContractTest {

    private ContactSkillPrimitiveCatalog skillCatalog;
    private FewShotMetaPolicyAdapter policyAdapter;
    private CrossMorphologyMapper morphologyMapper;
    private MetaSkillControlBus controlBus;

    @BeforeEach
    void setUp() {
        skillCatalog = new ContactSkillPrimitiveCatalog();
        policyAdapter = new FewShotMetaPolicyAdapter();
        morphologyMapper = new CrossMorphologyMapper();
        controlBus = new MetaSkillControlBus(4096, 2.0); // 4096 定长槽位, 最大容许时钟抖动 2.0ms
    }

    @Test
    @DisplayName("契约 1: 存证凭单 SHA-256 自签名完整性与防篡改雪崩测试")
    void test01_MetaSkillExecutionReceiptSha256IntegrityAndTamperProof() {
        MetaSkillExecutionReceipt receipt = MetaSkillExecutionReceipt.generate(
                "session-meta-9901",
                "precision-bearing-69",
                "arm-ur10e-6dof",
                "arm-franka-7dof",
                ContactSkillPrimitiveType.INSERTION,
                new double[]{280.0, 280.0, 750.0, 18.0, 18.0, 18.0},
                0.045, // 5 步残差 MSE 0.045 <= 0.1
                true,  // 自锁消除成功
                "LOCKED"
        );

        assertNotNull(receipt.signatureSha256(), "存证凭单 SHA-256 签名不得为空");
        assertTrue(receipt.verifyIntegrity(), "原始存证凭单 SHA-256 自验必须为 true");

        // 篡改攻击 1: 伪造残差 MSE
        MetaSkillExecutionReceipt tamperedMse = new MetaSkillExecutionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.workpieceId(),
                receipt.sourceEntityId(),
                receipt.targetEntityId(),
                receipt.primitiveType(),
                receipt.adaptedStiffness(),
                0.001, // 伪造更优残差
                receipt.wedgingEliminated(),
                receipt.fsmState(),
                receipt.timestampNs(),
                receipt.signatureSha256()
        );
        assertFalse(tamperedMse.verifyIntegrity(), "篡改残差 MSE 后凭单自验必须失败");

        // 篡改攻击 2: 篡改目标实体本体
        MetaSkillExecutionReceipt tamperedEntity = new MetaSkillExecutionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.workpieceId(),
                receipt.sourceEntityId(),
                "arm-kuka-iiwa-7dof", // 篡改本体
                receipt.primitiveType(),
                receipt.adaptedStiffness(),
                receipt.fiveStepResidualMse(),
                receipt.wedgingEliminated(),
                receipt.fsmState(),
                receipt.timestampNs(),
                receipt.signatureSha256()
        );
        assertFalse(tamperedEntity.verifyIntegrity(), "篡改目标实体本体后凭单自验必须失败");
    }

    @Test
    @DisplayName("契约 2: 千问 1536 维超球面技能嵌入匹配与四类基准参数召回 (定理 1.1)")
    void test02_HypersphericalSkillPrimitiveRetrievalAndParameterRecall() {
        for (ContactSkillPrimitiveType type : ContactSkillPrimitiveType.values()) {
            float[] baseEmbedding = skillCatalog.getEmbedding(type);
            assertNotNull(baseEmbedding, "基准超球面嵌入不得为空");
            assertEquals(1536, baseEmbedding.length, "必须严格为千问 1536 维特征向量");

            // 验证单位超球面保模性: ||v||_2 == 1.0
            double norm = computeL2Norm(baseEmbedding);
            assertEquals(1.0, norm, 1e-4, "超球面嵌入模长必须精确归一化为 1.0");

            // 施加轻微测地扰动 (cos >= 0.90) 测试鲁棒召回
            float[] query = perturbVector(baseEmbedding, 0.95);
            ContactSkillPrimitiveType matched = skillCatalog.matchSkillPrimitive(query);
            assertEquals(type, matched, "高相似度测地查询必须精确召回对应的技能元类型");

            // 校验召回的物理参数合理性
            SkillPrimitiveParameters params = skillCatalog.getParameters(matched);
            assertNotNull(params);
            assertEquals(6, params.stiffnessK().length);
            assertEquals(6, params.dampingD().length);
            assertEquals(6, params.referenceForce().length);

            if (type == ContactSkillPrimitiveType.INSERTION) {
                assertEquals(800.0, params.stiffnessK()[2], 1e-3, "插拔技能 Z 轴刚度应为 800.0 N/m");
                assertEquals(-15.0, params.referenceForce()[2], 1e-3, "插拔轴向推力应为 -15.0 N");
                assertEquals(0.0, params.pitchLeadMeter(), 1e-6);
            } else if (type == ContactSkillPrimitiveType.SCREWING) {
                assertEquals(0.0015, params.pitchLeadMeter(), 1e-6, "旋拧导程应为 0.0015 m/rad");
                assertEquals(0.8, params.referenceForce()[5], 1e-3, "旋拧拧紧扭矩应为 0.8 Nm");
            } else if (type == ContactSkillPrimitiveType.POLISHING) {
                assertEquals(1200.0, params.stiffnessK()[2], 1e-3, "打磨法向刚度应为 1200.0 N/m");
                assertEquals(-30.0, params.referenceForce()[2], 1e-3, "打磨恒力应为 -30.0 N");
            } else if (type == ContactSkillPrimitiveType.ALIGNMENT) {
                assertEquals(100.0, params.stiffnessK()[0], 1e-3, "找平径向柔顺刚度应为 100.0 N/m");
                assertEquals(-5.0, params.referenceForce()[2], 1e-3, "找平微贴合力应为 -5.0 N");
            }
        }
    }

    @Test
    @DisplayName("契约 3: 在线 5 步力觉残差一阶闭式微调与残差 MSE 收敛 (定理 1.2)")
    void test03_FewShotFirstOrderMetaPolicyClosedFormAdaptation() {
        policyAdapter.reset();
        SkillPrimitiveParameters currentParams = skillCatalog.getParameters(ContactSkillPrimitiveType.INSERTION);

        // 模拟遭遇初始工件装配偏差: 测得力为 -25.0N (比期望 -15.0N 多了 10N 阻力)
        double initialMse = 0.0;
        double finalMse = 0.0;
        double measuredForceZ = -25.0;

        for (int step = 0; step < 5; step++) {
            double[] measuredForce = new double[]{0.0, 0.0, measuredForceZ, 0.0, 0.0, 0.0};
            double[] displacementDx = new double[]{0.0, 0.0, 0.001, 0.0, 0.0, 0.0}; // 单步推进 1mm

            FewShotMetaPolicyAdapter.AdaptationResult result = policyAdapter.adapt(currentParams, measuredForce, displacementDx);

            // 单步计算耗时必须在微秒级 (<= 50,000ns)
            assertTrue(result.computationTimeNs() <= 50_000,
                    "一阶闭式解析微调单步耗时必须 <= 50μs, 实际为: " + result.computationTimeNs() + " ns");

            if (step == 0) {
                initialMse = result.fiveStepResidualMse();
            }
            finalMse = result.fiveStepResidualMse();
            currentParams = result.adaptedParams();

            // 验证物理保护硬限幅: 参考推力绝对值不可超出 50.0N
            assertTrue(Math.abs(currentParams.referenceForce()[2]) <= 50.0,
                    "参考推力必须被物理硬限幅约束在 50.0N 以内");

            // 闭环接触力学演化: 实测力在阻抗自适应调整下指数逼近期望力
            measuredForceZ += 0.70 * (currentParams.referenceForce()[2] - measuredForceZ);
        }

        // 验证 5 步内残差 MSE 衰减率 >= 80% (定理 1.2 指数收敛)
        double attenuationRate = 1.0 - (finalMse / (initialMse + 1e-9));
        assertTrue(attenuationRate >= 0.80,
                "5步一阶闭式微调力觉残差 MSE 衰减率必须 >= 80%, 实际为: " + (attenuationRate * 100) + "%");
    }

    @Test
    @DisplayName("契约 4: 装配卡塞与楔形自锁判定与 Anti-Wedging 自愈消除 (定理 1.3)")
    void test04_AntiWedgingSelfHealingAndJammingElimination() {
        policyAdapter.reset();
        SkillPrimitiveParameters baseParams = skillCatalog.getParameters(ContactSkillPrimitiveType.INSERTION);

        // 模拟陷入 Whitney 楔形自锁 (Wedging): 轴向位移微小停滞 (dx < 0.05mm)，力觉残差剧烈振荡 (方差 > 80.0)
        double[] displacementStalled = new double[]{0.0, 0.0, 0.00001, 0.0, 0.0, 0.0}; // 仅微米级位移
        FewShotMetaPolicyAdapter.AdaptationResult lastResult = null;

        for (int i = 0; i < 4; i++) {
            // 产生高频交变接触冲击力 (模拟卡死碰撞)
            double shockForceZ = (i % 2 == 0) ? -45.0 : -5.0;
            double[] measured = new double[]{15.0, -15.0, shockForceZ, 0.0, 0.0, 0.0};
            lastResult = policyAdapter.adapt(baseParams, measured, displacementStalled);
        }

        assertNotNull(lastResult);
        assertTrue(lastResult.wedgingDetected(), "残差方差剧烈且位移停滞时必须准确判定自锁卡阻");

        // 校验 Anti-Wedging 自愈机制: 轴向推力归零，横向刚度软化减半以释放接触应力
        SkillPrimitiveParameters healed = lastResult.adaptedParams();
        assertEquals(0.0, healed.referenceForce()[2], 1e-4, "卡塞自锁时轴向推进力必须强制归零退让");
        assertTrue(healed.stiffnessK()[0] <= baseParams.stiffnessK()[0] * 0.6, "X 轴横向刚度必须主动软化顺应");
        assertTrue(healed.stiffnessK()[1] <= baseParams.stiffnessK()[1] * 0.6, "Y 轴横向刚度必须主动软化顺应");
    }

    @Test
    @DisplayName("契约 5: 跨实体 6-DoF/7-DoF 运动学与阻抗自适应映射及 DLS 奇异点保护 (定理 1.3)")
    void test05_CrossMorphology6DofTo7DofDlsSingularityProtection() {
        // 1. 正常工作区 6-DoF 机械臂测试
        double[][] J_6dof = createOrthogonalJacobian(6, 6);
        double[] F_task = new double[]{0.0, 0.0, -20.0, 0.0, 0.0, 0.0};

        CrossMorphologyMapper.TorqueMappingResult res6 = morphologyMapper.mapCartesianToJointTorque(J_6dof, F_task, 6);
        assertNotNull(res6);
        assertEquals(6, res6.jointTorques().length);
        assertFalse(res6.dlsActivated(), "正常工作空间下不应误触发 DLS 阻尼截断");

        // 2. 奇异点区域测试 (可操作度接近 0)
        double[][] J_singular = createSingularJacobian(6, 6);
        CrossMorphologyMapper.TorqueMappingResult resSingular = morphologyMapper.mapCartesianToJointTorque(J_singular, F_task, 6);
        assertTrue(resSingular.dlsActivated(), "逼近奇异点时必须自动激活 DLS 阻尼截断保护");
        assertTrue(resSingular.dampingLambdaSquared() > 0.0, "阻尼因子必须为正");

        // 校验关节力矩严格有界，绝不产生数百 Nm 的奇异性发散
        for (double tau : resSingular.jointTorques()) {
            assertTrue(Math.abs(tau) <= 150.0, "DLS 保护下关节期望力矩必须严格 <= 150Nm, 实际为: " + tau);
        }
    }

    @Test
    @DisplayName("契约 6: 7-DoF 冗余机械臂人工势场零空间硬投影测试 (定理 1.3)")
    void test06_Redundant7DofNullSpaceArtificialPotentialProjection() {
        // 构造 6 x 7 雅可比矩阵
        double[][] J_7dof = createOrthogonalJacobian(6, 7);
        double[] jointPos = new double[]{0.0, 0.5, 0.0, -1.8, 0.0, 1.2, 0.0};
        double[] jointLimitsLower = new double[]{-2.8, -1.7, -2.8, -3.0, -2.8, -0.0, -2.8};
        double[] jointLimitsUpper = new double[]{2.8, 1.7, 2.8, -0.1, 2.8, 3.7, 2.8};

        // 计算零空间优化力矩
        double[] tauNull = morphologyMapper.computeNullSpaceTorque(J_7dof, jointPos, jointLimitsLower, jointLimitsUpper);
        assertEquals(7, tauNull.length);

        // 核心定理 1.3 验证: 零空间力矩对末端任务力零干扰，即 J * tau_null == 0
        double[] taskDisturbance = multiplyMatrixVector(J_7dof, tauNull);
        for (int r = 0; r < 6; r++) {
            assertEquals(0.0, taskDisturbance[r], 1e-4,
                    "零空间投影必须完全正交于任务空间 J * tau_null == 0, 行 " + r + " 实际为: " + taskDisturbance[r]);
        }
    }

    @Test
    @DisplayName("契约 7: 闭环跨实体接触阻抗李雅普诺夫无源性能量单调耗散测试 (定理 1.3)")
    void test07_LyapunovStrictOutputPassivityAndEnergyDissipation() {
        // 初始装配位姿误差 15mm, 速度 20mm/s
        double error = 0.015;
        double velocity = 0.02;
        double prevEnergy = 100.0;

        for (int step = 0; step < 100; step++) {
            CrossMorphologyMapper.LyapunovStepResult stepResult = morphologyMapper.simulateLyapunovStep(error, velocity, 0.001);
            error = stepResult.nextError();
            velocity = stepResult.nextVelocity();

            // 验证严格输出无源性能量单调递减
            assertTrue(stepResult.storageEnergy() <= prevEnergy + 1e-6,
                    "李雅普诺夫储能函数沿轨迹必须单调衰减 (无源性保证), step: " + step);
            prevEnergy = stepResult.storageEnergy();
        }

        // 100ms 模拟后误差指数衰减率 >= 90%
        double attenuation = 1.0 - (error / 0.015);
        assertTrue(attenuation >= 0.90, "跨实体阻抗闭环装配误差衰减率必须 >= 90%, 实际为: " + (attenuation * 100) + "%");
    }

    @Test
    @DisplayName("契约 8: 1000Hz 定长无锁总线吞吐与时钟抖动熔断软着陆自愈测试")
    void test08_MetaSkillControlBus1000HzLockFreeAndJitterGuard() {
        controlBus.reset();

        // 1. 模拟 1 秒 1000Hz 无锁并发事件写入
        for (int i = 0; i < 1000; i++) {
            boolean ok = controlBus.publish(
                    "skill-cmd-" + i,
                    ContactSkillPrimitiveType.INSERTION,
                    new double[]{300.0, 300.0, 800.0, 20.0, 20.0, 20.0},
                    new double[]{0.0, 0.0, -15.0, 0.0, 0.0, 0.0}
            );
            assertTrue(ok);
        }
        assertEquals(1000, controlBus.getPublishedCount());
        assertEquals(MetaSkillControlBus.BusState.NORMAL, controlBus.getState());

        // 2. 模拟网络通信严重时钟抖动 (连续 3 帧到达间隔 3.5ms > 2.0ms)
        controlBus.recordCycleInterval(3.5);
        controlBus.recordCycleInterval(3.8);
        controlBus.recordCycleInterval(4.0);

        // 验证自动切入 DEGRADED_SOFT_LANDING 软着陆熔断状态
        assertEquals(MetaSkillControlBus.BusState.DEGRADED_SOFT_LANDING, controlBus.getState(),
                "连续时钟抖动必须自动触发软着陆柔顺降级保护");

        // 3. 生成存证凭单并防伪核验
        MetaSkillExecutionReceipt receipt = controlBus.generateAuditReceipt("session-bus-verify");
        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "熔断自愈存证凭单 SHA-256 自验必须为 true");
    }

    // ==================== 数学与矩阵辅助工具 ====================

    private double computeL2Norm(float[] vec) {
        double sum = 0.0;
        for (float v : vec) sum += v * v;
        return Math.sqrt(sum);
    }

    private float[] perturbVector(float[] base, double targetCos) {
        Random rand = new Random(12345);
        int d = base.length;
        float[] randU = new float[d];
        for (int i = 0; i < d; i++) randU[i] = (float) rand.nextGaussian();

        double dot = 0.0;
        for (int i = 0; i < d; i++) dot += randU[i] * base[i];
        double uSumSq = 0.0;
        for (int i = 0; i < d; i++) {
            randU[i] -= (float) (dot * base[i]);
            uSumSq += randU[i] * randU[i];
        }
        double uNorm = Math.sqrt(uSumSq);
        for (int i = 0; i < d; i++) randU[i] /= (float) uNorm;

        double sinVal = Math.sqrt(Math.max(0.0, 1.0 - targetCos * targetCos));
        float[] res = new float[d];
        for (int i = 0; i < d; i++) {
            res[i] = (float) (targetCos * base[i] + sinVal * randU[i]);
        }
        return res;
    }

    private double[][] createOrthogonalJacobian(int rows, int cols) {
        double[][] J = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            J[r][r % cols] = 1.0;
            if (cols > rows && r == 0) {
                J[r][cols - 1] = 0.5; // 冗余列
            }
        }
        return J;
    }

    private double[][] createSingularJacobian(int rows, int cols) {
        double[][] J = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                J[r][c] = 0.001 * (r + 1); // 奇异退化，秩极低
            }
        }
        return J;
    }

    private double[] multiplyMatrixVector(double[][] M, double[] v) {
        int rows = M.length;
        int cols = M[0].length;
        double[] res = new double[rows];
        for (int r = 0; r < rows; r++) {
            double sum = 0.0;
            for (int c = 0; c < cols; c++) {
                sum += M[r][c] * v[c];
            }
            res[r] = sum;
        }
        return res;
    }
}
