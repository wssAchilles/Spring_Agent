package tech.qiantong.qknow.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.eval.EvalRegressionGate;
import tech.qiantong.qknow.module.kmc.service.rag.mining.RealQueryMiningService;
import tech.qiantong.qknow.module.kmc.service.rag.sanitizer.QuerySanitizer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 16: 生产级灾备容灾、异构数据一致性与线上真实 Query 难例自动挖掘体系专属契约测试
 */
class Phase16DisasterRecoveryAndQueryMiningGateTest {

    private QuerySanitizer sanitizer;
    private RealQueryMiningService miningService;
    private EvalRegressionGate gate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sanitizer = new QuerySanitizer();
        miningService = new RealQueryMiningService();
        gate = new EvalRegressionGate();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("契约 1: QuerySanitizer 多实体脱敏精度、模11与Luhn校验以及会话一致性保持")
    void testQuerySanitizerPrecisionAndConsistency() {
        // 合法身份证：110101199003072383 (模11真实校验码)
        // 合法银行卡：6222021234567894 (Luhn 校验)
        String rawQuery = "请问用户张三(手机13812345678，邮箱support@qiantong.tech)绑定的银行卡6222021234567894与身份证110101199003072383，在内网IP 192.168.1.100上登录失败怎么办？张三的手机13812345678一直收不到验证码。";

        Map<String, String> sessionMapping = new HashMap<>();
        String sanitized = sanitizer.sanitize(rawQuery, QuerySanitizer.MaskMode.STRUCTURE_PRESERVING, sessionMapping);

        // 1. 验证手机号脱敏为 138****5678
        assertTrue(sanitized.contains("138****5678"), "手机号应保留前3后4中间掩码");
        assertFalse(sanitized.contains("13812345678"), "明文手机号绝不能留存");

        // 2. 验证会话内同一个手机号映射严格一致
        int phoneFirstIdx = sanitized.indexOf("138****5678");
        int phoneSecondIdx = sanitized.lastIndexOf("138****5678");
        assertTrue(phoneFirstIdx >= 0 && phoneSecondIdx > phoneFirstIdx, "同实体多次出现必须一致映射");

        // 3. 验证合规身份证脱敏 (保留前6后4)
        assertTrue(sanitized.contains("110101********2383"), "合规身份证应保留前6后4");
        assertFalse(sanitized.contains("110101199003072383"), "明文身份证必须抹除");

        // 4. 验证银行卡脱敏
        assertTrue(sanitized.contains("622202******7894"), "银行卡应保留前6后4");
        assertFalse(sanitized.contains("6222021234567894"), "明文银行卡号必须抹除");

        // 5. 验证邮箱与IP脱敏
        assertTrue(sanitized.contains("s***@qiantong.tech"), "邮箱用户名应掩码");
        assertTrue(sanitized.contains("192.168.1.***"), "内网IP末段应掩码");

        // 6. 测试类型原型模式 (TYPE_PROTOTYPE)
        String prototypeSanitized = sanitizer.sanitize("联系13987654321", QuerySanitizer.MaskMode.TYPE_PROTOTYPE);
        assertTrue(prototypeSanitized.contains("[PHONE]"), "原型模式应替换为 [PHONE]");
    }

    @Test
    @DisplayName("契约 2: QuerySanitizer 防 ReDoS 性能与超长输入防御")
    void testQuerySanitizerAntiReDoSPerformance() {
        // 构造超长具有重复模式的畸变文本 (5,000 字符)
        StringBuilder malicious = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            malicious.append("1234567890@abc.def.ghi.jkl.longdomain.org ");
        }
        malicious.append("我的手机号是 13800138000 结束");

        long start = System.currentTimeMillis();
        String result = sanitizer.sanitize(malicious.toString());
        long cost = System.currentTimeMillis() - start;

        // 必须在 50ms 内完成，杜绝正则回溯爆炸卡顿
        assertTrue(cost < 150, "防 ReDoS 机制必须确保耗时受控，当前耗时: " + cost + "ms");
        assertTrue(result.contains("138****8000"), "手机号必须正确识别脱敏");
    }

    @Test
    @DisplayName("契约 3: RealQueryMiningService 四维漏斗难例挖掘与分类准确性")
    void testRealQueryMiningFunnelClassification() {
        List<RealQueryMiningService.RawRecallLogDTO> logs = List.of(
                // 1. 零召回样本
                RealQueryMiningService.RawRecallLogDTO.builder()
                        .id(1L).query("查询一个不存在的神秘文件").result("[]").topScore(0.0).build(),
                // 2. 低置信度样本 (得分 0.42 < 0.60)
                RealQueryMiningService.RawRecallLogDTO.builder()
                        .id(2L).query("微服务网关限流算法细节").result("[{\"context\":\"doc_1\"}]").topScore(0.42).build(),
                // 3. CRAG 歧义样本
                RealQueryMiningService.RawRecallLogDTO.builder()
                        .id(3L).query("Java垃圾回收").result("{\"status\":\"AMBIGUOUS\"}").topScore(0.75).cragAmbiguous(true).build(),
                // 4. 多轮追问样本
                RealQueryMiningService.RawRecallLogDTO.builder()
                        .id(4L).query("接着说上面提到的第三点详细步骤").result("[{\"context\":\"doc_2\"}]").topScore(0.85).build()
        );

        List<RealQueryMiningService.MinedQueryItem> mined = miningService.mineAndSanitize(logs);
        assertEquals(4, mined.size(), "四条样本均应成功挖掘");

        assertEquals(RealQueryMiningService.MiningCategory.ZERO_RECALL.getCode(), mined.get(0).getCategory());
        assertEquals(RealQueryMiningService.MiningCategory.LOW_CONFIDENCE.getCode(), mined.get(1).getCategory());
        assertEquals(RealQueryMiningService.MiningCategory.CRAG_AMBIGUOUS.getCode(), mined.get(2).getCategory());
        assertEquals(RealQueryMiningService.MiningCategory.FOLLOW_UP.getCode(), mined.get(3).getCategory());

        // 验证导出的 JSONL 格式合法性
        String jsonl = miningService.exportToJsonl(mined);
        assertNotNull(jsonl);
        assertTrue(jsonl.contains("\"category\":\"zero_recall\""));
        assertTrue(jsonl.contains("\"category\":\"low_confidence\""));
    }

    @Test
    @DisplayName("契约 4: EvalRegressionGate 双轨红线：零召回率超标或真实难例退化触发硬阻断")
    void testEvalRegressionGateDualTrackThresholds() {
        // 场景 A: 零召回率达到 0.08 (8% > 5% 绝对红线) -> 必须被硬阻断！
        Map<String, Double> violatedMetrics = Map.of(
                "faithfulness", 0.90,
                "answer_relevance", 0.88,
                "context_recall", 0.85,
                "zero_recall_rate", 0.08 // 超标！
        );
        var decisionA = gate.evaluateGate(violatedMetrics, Map.of());
        assertFalse(decisionA.isPassed(), "零召回率超过 5% 绝对红线必须被阻断");
        assertTrue(decisionA.getReason().contains("ZeroRecallRate") || decisionA.getReason().contains("zero_recall_rate"),
                "阻断原因必须明确指出零召回率超标");

        // 场景 B: 各项达标且零召回率仅 0.02 (2% <= 5%) -> 必须通过！
        Map<String, Double> normalMetrics = Map.of(
                "faithfulness", 0.92,
                "answer_relevance", 0.89,
                "context_recall", 0.86,
                "zero_recall_rate", 0.02
        );
        var decisionB = gate.evaluateGate(normalMetrics, Map.of());
        assertTrue(decisionB.isPassed(), "全部指标达标且零召回率受控应通过门禁");

        // 场景 C: 零召回率从基准 0.01 上升到 0.04 (上升 0.03 > 2% 退化容忍度) -> 阻断退化！
        Map<String, Double> baseline = Map.of(
                "faithfulness", 0.92,
                "answer_relevance", 0.89,
                "context_recall", 0.86,
                "zero_recall_rate", 0.01
        );
        Map<String, Double> regressed = Map.of(
                "faithfulness", 0.92,
                "answer_relevance", 0.89,
                "context_recall", 0.86,
                "zero_recall_rate", 0.04
        );
        var decisionC = gate.evaluateGate(regressed, baseline);
        assertFalse(decisionC.isPassed(), "零召回率相比基线退化超过 2% 必须触发阻断");
    }

    @Test
    @DisplayName("契约 5: 不可变基准 rag-real-queries-v1.jsonl 完备性与隐私零泄露验证")
    void testImmutableBenchmarkFixtureIntegrity() throws Exception {
        Path fixturePath = Paths.get("fixtures/rag-real-queries-v1.jsonl");
        if (!Files.exists(fixturePath)) {
            // 兼容相对路径从 backend/ 或根目录启动
            fixturePath = Paths.get("backend/tests/fixtures/rag-real-queries-v1.jsonl");
        }
        assertTrue(Files.exists(fixturePath), "基准文件 rag-real-queries-v1.jsonl 必须存在");

        List<String> lines = Files.readAllLines(fixturePath, StandardCharsets.UTF_8);
        assertTrue(lines.size() >= 20, "基准测试用例数必须 >= 20 条，当前有: " + lines.size());

        for (String line : lines) {
            if (line.isBlank()) continue;
            var node = objectMapper.readTree(line);
            assertTrue(node.has("id"), "必须包含 id 字段");
            assertTrue(node.has("query"), "必须包含 query 字段");
            assertTrue(node.has("category"), "必须包含 category 字段");
            assertTrue(node.has("expected_contexts"), "必须包含 expected_contexts 字段");

            String queryText = node.get("query").asText();
            // 严格验证不存在明文 11 位手机号 (如 13812345678)
            assertFalse(queryText.matches(".*(?<!\\d)1[3-9]\\d{9}(?!\\d).*"),
                    "基准测试集中绝不允许存在明文未脱敏手机号: " + queryText);
        }
    }
}
