package tech.qiantong.qknow.hermes.coalition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.coalition.dto.BilevelSettlementScheme;
import tech.qiantong.qknow.hermes.coalition.dto.CoalitionMemberAgent;
import tech.qiantong.qknow.hermes.coalition.dto.CoalitionSettlementReceipt;
import tech.qiantong.qknow.hermes.coalition.dto.CrossDomainAuditResolution;
import tech.qiantong.qknow.hermes.coalition.dto.CrossDomainTransactionProposal;
import tech.qiantong.qknow.hermes.coalition.dto.DynamicCoalitionStructure;
import tech.qiantong.qknow.hermes.coalition.dto.OrganizationDomain;
import tech.qiantong.qknow.hermes.coalition.engine.BilevelCreditSettlementManifold;
import tech.qiantong.qknow.hermes.coalition.engine.CrossDomainCoalitionControlBus;
import tech.qiantong.qknow.hermes.coalition.engine.CrossDomainSovereignGovernanceGate;
import tech.qiantong.qknow.hermes.coalition.engine.DynamicCoalitionFormationEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 99 专属契约测试套件
 */
public class Phase99CrossDomainCoalitionContractTest {

    private DynamicCoalitionFormationEngine formationEngine;
    private BilevelCreditSettlementManifold settlementManifold;
    private CrossDomainSovereignGovernanceGate governanceGate;
    private CrossDomainCoalitionControlBus controlBus;

    @BeforeEach
    public void setUp() {
        formationEngine = new DynamicCoalitionFormationEngine();
        settlementManifold = new BilevelCreditSettlementManifold();
        governanceGate = new CrossDomainSovereignGovernanceGate();
        controlBus = new CrossDomainCoalitionControlBus();
    }

    private double[] createNormalizedEmbedding(double seed) {
        double[] emb = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            emb[i] = Math.sin((i + 1) * seed);
            sumSq += emb[i] * emb[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            emb[i] /= norm;
        }
        return emb;
    }

    @Test
    @DisplayName("测试01: 跨组织合作博弈动态联盟超可加性与核心解稳定性验证")
    public void test01_DynamicCoalitionFormation_SuperadditivityAndCoreStability() {
        List<CoalitionMemberAgent> members = new ArrayList<>();
        members.add(new CoalitionMemberAgent(
            "agent-primary-01",
            OrganizationDomain.PRIMARY_ENTERPRISE,
            "CORE_COORDINATOR",
            100.0,
            createNormalizedEmbedding(1.0),
            System.currentTimeMillis()
        ));
        members.add(new CoalitionMemberAgent(
            "agent-supplier-01",
            OrganizationDomain.SUPPLY_CHAIN_PARTNER,
            "INVENTORY_OPTIMIZER",
            80.0,
            createNormalizedEmbedding(1.0),
            System.currentTimeMillis()
        ));
        members.add(new CoalitionMemberAgent(
            "agent-fin-01",
            OrganizationDomain.FINANCIAL_INSTITUTION,
            "SETTLEMENT_ESCROW",
            120.0,
            createNormalizedEmbedding(1.0),
            System.currentTimeMillis()
        ));

        DynamicCoalitionStructure coalition = formationEngine.formCoalition("COALITION-ALPHA-01", 1L, members);

        assertNotNull(coalition);
        assertEquals("COALITION-ALPHA-01", coalition.coalitionId());
        assertEquals(3, coalition.memberAgentIds().size());
        assertTrue(coalition.isCoreStable(), "联盟结构必须满足核心解稳定性");
        assertTrue(coalition.superadditivityMargin() > 0.0, "超可加性协同裕度必须严格正定");
        assertTrue(coalition.totalCharacteristicValue() > 300.0, "总产值必须高于各组织基础信用之和");
    }

    @Test
    @DisplayName("测试02: 动态联盟形成与重组微秒级单步耗时验证")
    public void test02_DynamicCoalitionFormation_MicrosecondPerformance() {
        List<CoalitionMemberAgent> members = new ArrayList<>();
        members.add(new CoalitionMemberAgent("a1", OrganizationDomain.PRIMARY_ENTERPRISE, "CAP1", 50.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));
        members.add(new CoalitionMemberAgent("a2", OrganizationDomain.SUPPLY_CHAIN_PARTNER, "CAP2", 50.0, createNormalizedEmbedding(1.5), System.currentTimeMillis()));
        members.add(new CoalitionMemberAgent("a3", OrganizationDomain.FINANCIAL_INSTITUTION, "CAP3", 50.0, createNormalizedEmbedding(2.0), System.currentTimeMillis()));

        // 热身
        for (int i = 0; i < 100; i++) {
            formationEngine.formCoalition("WARMUP", i, members);
        }

        long start = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            formationEngine.formCoalition("PERF-TEST", i, members);
        }
        long durationUs = (System.nanoTime() - start) / 1000;
        double avgUs = (double) durationUs / iterations;

        assertTrue(avgUs <= 60.0, "单步联盟形成与重组平均耗时必须 <= 60微秒，实测: " + avgUs + "us");
    }

    @Test
    @DisplayName("测试03: 双层沙普利-纳什信贷清算公理化守恒与帕累托效用验证")
    public void test03_BilevelSettlement_AxiomaticConservationAndParetoOptimality() {
        List<CoalitionMemberAgent> members = new ArrayList<>();
        members.add(new CoalitionMemberAgent("org-a", OrganizationDomain.PRIMARY_ENTERPRISE, "DATA_PROVIDER", 100.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));
        members.add(new CoalitionMemberAgent("org-b", OrganizationDomain.SUPPLY_CHAIN_PARTNER, "COMPUTE_NODE", 100.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));
        members.add(new CoalitionMemberAgent("org-c", OrganizationDomain.FINANCIAL_INSTITUTION, "ESCROW_LEDGER", 100.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));

        DynamicCoalitionStructure coalition = formationEngine.formCoalition("COALITION-SETTLE", 1L, members);
        double[] targetTask = createNormalizedEmbedding(1.0);

        BilevelSettlementScheme scheme = settlementManifold.settleCredits(coalition, members, targetTask);

        assertNotNull(scheme);
        assertTrue(scheme.globalParetoUtility() > 0.0, "纳什议价全局帕累托效用必须严格正定");
        assertTrue(scheme.settlementResidual() <= 1e-6, "沙普利信贷清算守恒误差必须 <= 1e-6，实测: " + scheme.settlementResidual());

        // 验证总分配金额严格等于总特征值
        double sumAllocated = scheme.shapleyCreditAllocations().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(coalition.totalCharacteristicValue(), sumAllocated, 1e-5, "分配总额必须严格守恒");
    }

    @Test
    @DisplayName("测试04: 搭便车零贡献节点信贷清算严格归零验证")
    public void test04_BilevelSettlement_FreeRiderNullification() {
        List<CoalitionMemberAgent> members = new ArrayList<>();
        members.add(new CoalitionMemberAgent("producer-1", OrganizationDomain.PRIMARY_ENTERPRISE, "HIGH_COMPUTE", 200.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));
        members.add(new CoalitionMemberAgent("producer-2", OrganizationDomain.SUPPLY_CHAIN_PARTNER, "ACCURATE_DATA", 150.0, createNormalizedEmbedding(1.0), System.currentTimeMillis()));
        // 恶意搭便车观察者
        members.add(new CoalitionMemberAgent("free-rider-1", OrganizationDomain.EXTERNAL_REGULATOR, "DUMMY_OBSERVER", 100.0, createNormalizedEmbedding(5.0), System.currentTimeMillis()));

        DynamicCoalitionStructure coalition = formationEngine.formCoalition("COALITION-FR", 1L, members);
        double[] targetTask = createNormalizedEmbedding(1.0);

        BilevelSettlementScheme scheme = settlementManifold.settleCredits(coalition, members, targetTask);

        assertNotNull(scheme);
        assertTrue(scheme.freeRidersNullified(), "必须精准识别搭便车行为");
        assertEquals(0.0, scheme.shapleyCreditAllocations().get("free-rider-1"), 1e-9, "搭便车节点信贷分配必须严格归零");
        assertTrue(scheme.shapleyCreditAllocations().get("producer-1") > 0.0, "贡献节点信贷分配必须大于零");
        assertTrue(scheme.shapleyCreditAllocations().get("producer-2") > 0.0, "贡献节点信贷分配必须大于零");
    }

    @Test
    @DisplayName("测试05: 阿里千问 1536 维超球面向量维度与模长校验门禁")
    public void test05_BilevelSettlement_StrictDimensionAndNormalizationGuards() {
        // 非法维度
        assertThrows(IllegalArgumentException.class, () -> {
            new CoalitionMemberAgent("bad-dim", OrganizationDomain.PRIMARY_ENTERPRISE, "CAP", 50.0, new double[1024], System.currentTimeMillis());
        });

        // 非法模长 (非单位向量)
        assertThrows(IllegalArgumentException.class, () -> {
            double[] nonUnit = new double[1536];
            nonUnit[0] = 2.0;
            new CoalitionMemberAgent("bad-norm", OrganizationDomain.PRIMARY_ENTERPRISE, "CAP", 50.0, nonUnit, System.currentTimeMillis());
        });
    }

    @Test
    @DisplayName("测试06: 跨自治域主权控制屏障与防重放一票否决门禁")
    public void test06_CrossDomainSovereignGovernance_RelativeDegree2CBFSafetyVeto() {
        long now = System.currentTimeMillis();

        // 1. 外部监管机构跨域破坏性写操作拦截
        CrossDomainTransactionProposal p1 = new CrossDomainTransactionProposal(
            "P-VETO-01",
            "agent-ext-01",
            OrganizationDomain.EXTERNAL_REGULATOR,
            OrganizationDomain.PRIMARY_ENTERPRISE,
            "DROP_TABLE_MUTATION",
            50.0,
            true, // 破坏性写
            new double[]{1.0, 0.0},
            now,
            "NONCE-UNIQUE-01"
        );
        CrossDomainAuditResolution r1 = governanceGate.auditProposal(p1);
        assertFalse(r1.allowed(), "跨域破坏性写操作必须被硬拦截");
        assertTrue(r1.auditReason().contains("SOVEREIGN_VETO"));

        // 2. Nonce 重放攻击拦截
        CrossDomainTransactionProposal p2 = new CrossDomainTransactionProposal(
            "P-VETO-02",
            "agent-supplier-01",
            OrganizationDomain.SUPPLY_CHAIN_PARTNER,
            OrganizationDomain.PRIMARY_ENTERPRISE,
            "READ_SYNC",
            20.0,
            false,
            new double[]{1.0, 0.0},
            now,
            "NONCE-UNIQUE-01" // 重复 nonce
        );
        CrossDomainAuditResolution r2 = governanceGate.auditProposal(p2);
        assertFalse(r2.allowed(), "重复 Nonce 必须被防重放门禁硬拦截");
        assertTrue(r2.auditReason().contains("Nonce 重放攻击"));

        // 3. 时间戳过期拦截
        CrossDomainTransactionProposal p3 = new CrossDomainTransactionProposal(
            "P-VETO-03",
            "agent-supplier-01",
            OrganizationDomain.SUPPLY_CHAIN_PARTNER,
            OrganizationDomain.PRIMARY_ENTERPRISE,
            "READ_SYNC",
            20.0,
            false,
            new double[]{1.0, 0.0},
            now - 100_000L, // 超出 60s
            "NONCE-UNIQUE-02"
        );
        CrossDomainAuditResolution r3 = governanceGate.auditProposal(p3);
        assertFalse(r3.allowed(), "时间戳超时必须被硬拦截");
        assertTrue(r3.auditReason().contains("时间戳超出滑动窗口"));
    }

    @Test
    @DisplayName("测试07: 相对阶 r=2 控制屏障 QP 闭式解析安全投影软修补")
    public void test07_CrossDomainSovereignGovernance_CounterfactualSafetyProjection() {
        long now = System.currentTimeMillis();

        // 申请配额 150.0 (超额但 <= 200.0 且非破坏性)，应当触发安全二次规划 (QP) 投影修补
        double[] rawAction = new double[]{10.0, 20.0};
        CrossDomainTransactionProposal p = new CrossDomainTransactionProposal(
            "P-PROJECT-01",
            "agent-fin-01",
            OrganizationDomain.FINANCIAL_INSTITUTION,
            OrganizationDomain.PRIMARY_ENTERPRISE,
            "ESCROW_CREDIT_TRANSFER",
            150.0,
            false,
            rawAction,
            now,
            "NONCE-PROJECT-01"
        );

        CrossDomainAuditResolution res = governanceGate.auditProposal(p);
        assertTrue(res.allowed(), "可投影超额动作应获准通过");
        assertTrue(res.softProjected(), "必须标记为软投影修补");
        assertEquals(0.0, res.cbfMargin(), 1e-6, "投影后屏障裕度必须压降到安全边界 0.0");
        assertTrue(res.safeActionVector()[0] < rawAction[0], "动作向量必须经过比例安全缩放");
    }

    @Test
    @DisplayName("测试08: 1000Hz 4096 槽位 Disruptor 无锁总线与 JitterGuard 降级软着陆")
    public void test08_CrossDomainControlBus_DisruptorThroughputAndJitterGuard() {
        // 正常发布单帧
        for (int i = 0; i < 50; i++) {
            controlBus.publishEvent("COALITION_SYNC", "HASH-" + i, false);
        }
        assertEquals("STATUS_NORMAL", controlBus.getBusStatus());

        // 连续 3 帧时钟抖动
        controlBus.publishEvent("JITTER_FRAME_1", "H1", true);
        controlBus.publishEvent("JITTER_FRAME_2", "H2", true);
        controlBus.publishEvent("JITTER_FRAME_3", "H3", true);

        assertEquals("STATUS_DEGRADED_BUFFERED", controlBus.getBusStatus(), "连续 3 帧抖动后必须瞬切缓冲软着陆状态");

        // 签发密码学执行凭单并验真
        CoalitionSettlementReceipt receipt = controlBus.signReceipt(
            "COALITION-DISRUPTOR",
            "SETTLE-001",
            4,
            500.0,
            12.5,
            180.0,
            true,
            42L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "不可变存证凭单 SHA-256 自签名验真必须 100% 通过");
    }
}
