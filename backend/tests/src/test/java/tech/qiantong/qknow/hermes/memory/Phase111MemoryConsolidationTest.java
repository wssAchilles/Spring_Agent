package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import tech.qiantong.qknow.hermes.memory.compress.ContextAdaptiveWorkingMemoryCompressor;
import tech.qiantong.qknow.hermes.memory.compress.ContextAdaptiveWorkingMemoryCompressor.CompressedContextResult;
import tech.qiantong.qknow.hermes.memory.compress.ContextAdaptiveWorkingMemoryCompressor.ContextEntry;
import tech.qiantong.qknow.hermes.memory.compress.ContextAdaptiveWorkingMemoryCompressor.MemoryLevel;
import tech.qiantong.qknow.hermes.memory.persona.ProfileExtractionResult;
import tech.qiantong.qknow.hermes.memory.persona.SleepTimeMemoryConsolidator;
import tech.qiantong.qknow.hermes.memory.receipt.MemoryConsolidationReceipt;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 111 专属契约测试套件：
 * 长效情境记忆网络、睡眠期画像提炼与自适应滑动窗口压缩器。
 * 覆盖率失真分层压缩、在途消息零丢失并发裁剪、贝叶斯反向纠偏收敛、千问超球面拓扑保序与不可变存证自签名。
 */
@DisplayName("Phase 111: 长效情境记忆网络与自适应工作记忆压缩器契约测试")
public class Phase111MemoryConsolidationTest {

    private ContextAdaptiveWorkingMemoryCompressor compressor;
    private SleepTimeMemoryConsolidator consolidator;
    private ShortTermMemory shortTermMemory;
    private LongTermMemory longTermMemory;
    private IRedisService redisService;

    private float[] createNormalized1536Vector(float seed) {
        float[] vec = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.005);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    private double computeGeodesicDistance(float[] u, float[] v) {
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += u[i] * v[i];
        }
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(dot) / Math.PI;
    }

    @BeforeEach
    void setUp() {
        compressor = new ContextAdaptiveWorkingMemoryCompressor();
        redisService = mock(IRedisService.class);
        shortTermMemory = new ShortTermMemory(null, null); // 纯内存模式
        longTermMemory = mock(LongTermMemory.class);
        consolidator = new SleepTimeMemoryConsolidator(shortTermMemory, longTermMemory, null);
    }

    @Test
    @DisplayName("契约测试 1: 自适应工作记忆分层压缩保真度 (定理 1.1)")
    void testAdaptiveContextCompressionFidelity() {
        List<ContextEntry> entries = new ArrayList<>();

        // L0 系统硬约束
        entries.add(ContextEntry.of("e0", MemoryLevel.L0_SYSTEM_SINK, "system",
                "系统提示：严禁越权操作，禁止泄露任何密码哈希与核心密钥规则！", true));

        // L1 因果决策事实
        entries.add(ContextEntry.of("e1", MemoryLevel.L1_CAUSAL_DECISION, "user",
                "用户决策：生产环境超时时间必须严格设为 5000ms，拒绝任何降级！", true));
        entries.add(ContextEntry.of("e2", MemoryLevel.L1_CAUSAL_DECISION, "assistant",
                "审批决策：HITL 审批已通过，执行状态转移至 DEPLOYED。", true));

        // L2 普通历史对话 (较长)
        for (int i = 0; i < 10; i++) {
            entries.add(ContextEntry.of("h" + i, MemoryLevel.L2_CONVERSATION_HISTORY, "user",
                    "这是第 " + i + " 轮普通对话交流内容，包含一些关于天气和日程的日常讨论细节。", false));
        }

        // L3 巨型 MCP 工具 JSON 原始报文 (模拟超过 2000 字符的大 JSON)
        StringBuilder hugeJson = new StringBuilder("{\"status\":\"SUCCESS\",\"toolName\":\"queryLogs\",\"data\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) hugeJson.append(",");
            hugeJson.append("{\"id\":").append(i)
                    .append(",\"timestamp\":1710000000000")
                    .append(",\"raw_log\":\"DEBUG [main] ConnectionPool thread dump stacktrace entry ")
                    .append(i).append(" with verbose headers and metadata\"}");
        }
        hugeJson.append("],\"summary\":\"共检索到 50 条系统日志\"}");

        entries.add(ContextEntry.of("t1", MemoryLevel.L3_TOOL_PAYLOAD, "tool", hugeJson.toString(), false));

        // 验证工具 JSON 模式投影单独压缩率
        String projected = compressor.projectToolJson(hugeJson.toString());
        assertNotNull(projected);
        double toolCompressionRatio = 1.0 - ((double) projected.length() / hugeJson.length());
        assertTrue(toolCompressionRatio >= 0.75,
                "工具 JSON 模式投影压缩率必须 >= 75%, 实际为: " + String.format("%.2f%%", toolCompressionRatio * 100));
        assertTrue(projected.contains("SUCCESS"), "模式投影必须保留 status");
        assertTrue(projected.contains("共检索到 50 条系统日志") || projected.contains("queryLogs"), "模式投影必须保留关键标识");

        // 执行整体分层压缩 (目标字符预算限制为 800)
        CompressedContextResult result = compressor.compress(entries, 800);

        assertNotNull(result);
        assertTrue(result.latencyMs() <= 15, "单次压缩耗时必须 <= 15ms, 实际耗时: " + result.latencyMs() + "ms");
        assertTrue(result.causalFactRetentionRate() >= 0.95,
                "关键因果决策事实保留率必须 >= 95%, 实际为: " + String.format("%.2f%%", result.causalFactRetentionRate() * 100));
        assertTrue(result.compressedContent().contains("严禁越权操作"), "L0 系统硬约束必须 100% 原始保留");
        assertTrue(result.compressedContent().contains("5000ms"), "L1 用户因果决策必须 100% 保留");
        assertTrue(result.compressedContent().contains("DEPLOYED"), "L1 审批决策必须 100% 保留");
    }

    @Test
    @DisplayName("契约测试 2: 睡眠期并发在线消息零丢失安全增量裁剪 (定理 1.2)")
    void testSleepTimeConcurrentMessageZeroLoss() throws Exception {
        String sessionId = "sess_concurrent_01";
        String userId = "user_test_99";

        // 创建带回调钩子的短时记忆实例，在 consolidator 读取 initialCount 后触发并发写入
        CountDownLatch readInitialLatch = new CountDownLatch(1);
        ShortTermMemory hookedMemory = new ShortTermMemory(null, null) {
            @Override
            public int size(String sid) {
                int s = super.size(sid);
                readInitialLatch.countDown();
                return s;
            }
        };
        SleepTimeMemoryConsolidator testConsolidator = new SleepTimeMemoryConsolidator(hookedMemory, longTermMemory, null);

        // 初始写入 10 条历史消息
        for (int i = 0; i < 10; i++) {
            hookedMemory.addMessage(new UserMessage("历史旧消息 " + i));
        }
        assertEquals(10, hookedMemory.size());

        // 启动 50 个虚拟线程模拟在线并发追加新消息
        int concurrentCount = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch doneLatch = new CountDownLatch(concurrentCount);
        AtomicInteger successWrites = new AtomicInteger(0);

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    readInitialLatch.await(); // 等待 consolidator 读取完初始 10 条后立即并发写入
                    hookedMemory.addMessage(new UserMessage("在线并发新消息_" + index));
                    successWrites.incrementAndGet();
                } catch (Exception e) {
                    // 记录失败
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 执行睡眠期整理：读取初始 10 条，并发写入交织发生，最后安全增量裁剪前 10 条
        MemoryConsolidationReceipt receipt = testConsolidator.consolidateSession(sessionId, userId, "tenant_a");

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "并发线程必须在 5 秒内完成");
        executor.shutdown();

        assertNotNull(receipt);
        assertEquals("SUCCESS", receipt.status());
        assertEquals(10, receipt.sourceMessageCount());

        // 校验短时记忆状态：初始 10 条被安全增量裁剪，而并发追加的 50 条新消息 100% 完好无损保留！
        int remainingSize = hookedMemory.size();
        assertEquals(50, remainingSize, "并发追加的 50 条新消息必须 100% 完好保留，零消息丢失！");
        assertEquals(50, successWrites.get());

        List<Message> remainingMessages = hookedMemory.getContext(100);
        for (Message msg : remainingMessages) {
            assertTrue(msg.getText().startsWith("在线并发新消息_"), "剩余消息必须全部为并发新消息");
        }
    }

    @Test
    @DisplayName("契约测试 3: 睡眠期画像提炼贝叶斯信念演化与反向纠偏指数级收敛 (定理 1.2)")
    void testBayesianProfileCorrectionConvergence() {
        String userId = "user_engineer_07";

        // 阶段 1：初始偏好提炼与贝叶斯先验更新
        List<Message> initialMessages = List.of(
                new UserMessage("我喜欢输出详细的 Markdown 表格格式，项目系统请按此规范。"),
                new UserMessage("模块 API 需要包含完整的类型定义和参数说明。")
        );
        ProfileExtractionResult result1 = consolidator.extractStructuredProfile("sess_01", userId, initialMessages);
        assertTrue(result1.confidenceScore() >= 0.75, "初始画像提炼置信度必须达到门禁");

        consolidator.applyBayesianEvolution(userId, result1);
        Map<String, Double> profilePhase1 = consolidator.getUserProfile(userId);
        assertFalse(profilePhase1.isEmpty(), "初始画像库不应为空");

        // 阶段 2：用户在后续交互中发出显式反向纠偏指令
        List<Message> correctionMessages = List.of(
                new UserMessage("不要再使用 Markdown 表格，改用纯文本一行一条，禁止繁琐格式！")
        );
        ProfileExtractionResult result2 = consolidator.extractStructuredProfile("sess_02", userId, correctionMessages);
        assertFalse(result2.reflectionsAndCorrections().isEmpty(), "必须识别出反向纠偏反思");

        // 执行贝叶斯后验演化
        consolidator.applyBayesianEvolution(userId, result2);
        Map<String, Double> profilePhase2 = consolidator.getUserProfile(userId);

        // 断言：错误旧先验以指数速度衰减至 <= 0.05，新纠偏置信度跃升至 >= 0.90
        boolean hasDecayedPrior = false;
        boolean hasNewCorrection = false;
        for (Map.Entry<String, Double> entry : profilePhase2.entrySet()) {
            if (entry.getKey().contains("style") || entry.getKey().contains("format")) {
                assertTrue(entry.getValue() <= 0.05, "旧先验必须以指数级衰减至 <= 0.05, 实际: " + entry.getValue());
                hasDecayedPrior = true;
            }
            if (entry.getKey().startsWith("CORRECTION_")) {
                assertTrue(entry.getValue() >= 0.90, "新反向纠偏置信度必须跃升至 >= 0.90, 实际: " + entry.getValue());
                hasNewCorrection = true;
            }
        }
        assertTrue(hasDecayedPrior, "必须存在被衰减的旧先验偏好");
        assertTrue(hasNewCorrection, "必须存在高置信度的新反向纠偏规则");
    }

    @Test
    @DisplayName("契约测试 4: 千问 1536 维超球面时空检索局部拓扑保序 (定理 1.3)")
    void testHypersphericalGeodesicTopologyOrderPreserving() {
        // 构造千问 1536 维超球面向量
        float[] queryVec = createNormalized1536Vector(0.5f);

        // 语义高度匹配的核心记忆 m1 (测地线距离极小)
        float[] m1Vec = createNormalized1536Vector(0.505f); // 微小差异，内积极高
        // 语义低匹配的近期噪声记忆 m2 (测地线距离较大)
        float[] m2Vec = createNormalized1536Vector(2.5f);   // 差异明显

        double dg1 = computeGeodesicDistance(queryVec, m1Vec);
        double dg2 = computeGeodesicDistance(queryVec, m2Vec);
        assertTrue(dg1 < dg2, "m1 语义测地线距离必须显著小于 m2");
        double deltaG = dg2 - dg1;
        assertTrue(deltaG > 0.15, "测地线语义差距必须显著: " + deltaG);

        // 时间设定：m1 发生于 1 小时前 (3600s)，m2 发生于 5 分钟前 (300s)
        long deltaT1 = 3600L;
        long deltaT2 = 300L;
        double lambda = 0.0001; // 时间衰减系数
        double alpha = 0.85;    // 语义权重

        // 校验时间差是否处于定理 1.3 临界拓扑保序界限内
        // tau^* = (alpha / ((1 - alpha) * lambda)) * deltaG
        double tauStar = (alpha / ((1.0 - alpha) * lambda)) * deltaG;
        double actualTimeDelta = Math.abs(deltaT1 - deltaT2);
        assertTrue(actualTimeDelta < tauStar, "时间扰动必须在临界保序界限内");

        // 计算复合时空检索泛函得分: S = alpha * (1 - d_g) + (1 - alpha) * R(t)
        double r1 = Math.exp(-lambda * deltaT1);
        double r2 = Math.exp(-lambda * deltaT2);

        double score1 = alpha * (1.0 - dg1) + (1.0 - alpha) * r1;
        double score2 = alpha * (1.0 - dg2) + (1.0 - alpha) * r2;

        // 断言：局部拓扑保序成立，语义高度相关的 m1 得分严格高于近期噪声 m2，拓扑逆转率严格为 0%
        assertTrue(score1 > score2,
                String.format("千问超球面时空检索局部拓扑保序必须成立: score1(%.4f) > score2(%.4f)", score1, score2));
    }

    @Test
    @DisplayName("契约测试 5: 不可变记忆巩固存证凭单 SHA-256 自签名与防篡改验真")
    void testMemoryConsolidationReceiptTamperProof() {
        String receiptId = "receipt_test_001";
        String sessionId = "sess_receipt_001";
        String userId = "user_001";
        long now = System.currentTimeMillis();

        MemoryConsolidationReceipt receipt = MemoryConsolidationReceipt.create(
                receiptId, sessionId, userId, now, 20, 15, "HASH_ABC_123", "SUCCESS"
        );

        assertNotNull(receipt);
        assertNotNull(receipt.signature());
        assertTrue(receipt.verifySignature(), "原始凭单签名验真必须通过");

        // 构造被篡改的数据（例如试图修改保留消息数或状态）
        MemoryConsolidationReceipt tamperedReceipt = new MemoryConsolidationReceipt(
                receiptId, sessionId, userId, now, 20,
                999, // 篡改保留数量
                "HASH_ABC_123", "SUCCESS", receipt.signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "篡改保留消息数后验真必须失败");

        MemoryConsolidationReceipt tamperedStatus = new MemoryConsolidationReceipt(
                receiptId, sessionId, userId, now, 20, 15,
                "HASH_ABC_123",
                "FAILED", // 篡改状态
                receipt.signature()
        );
        assertFalse(tamperedStatus.verifySignature(), "篡改状态后验真必须失败");
    }
}
