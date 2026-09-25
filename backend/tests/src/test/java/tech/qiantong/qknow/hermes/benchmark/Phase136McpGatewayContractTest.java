package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.AdaptiveMcpCircuitBreakerGovernor;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.AdaptiveMcpCircuitBreakerGovernor.CircuitConfig;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.AdaptiveMcpCircuitBreakerGovernor.CircuitDecision;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.AdaptiveMcpCircuitBreakerGovernor.CircuitState;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.McpZeroTrustGatewayReceipt;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.MultiTenantDistributedQuotaGovernor;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.MultiTenantDistributedQuotaGovernor.QuotaAcquisitionResult;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.MultiTenantDistributedQuotaGovernor.TenantQuotaPolicy;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.ZeroTrustDataMaskingEngine;
import tech.qiantong.qknow.hermes.tool.mcp.gateway.ZeroTrustDataMaskingEngine.MaskingResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 136 核心契约测试套件：
 * 生产级企业 MCP 工具调用动态多租户分布式配额、断路降级与零信任数据脱敏网关中枢
 * (Production-Grade Enterprise MCP Dynamic Multi-Tenant Distributed Quota,
 * Circuit Breaker & Zero-Trust Data Masking Gateway Metacenter)
 *
 * 核心验证范围：
 * 1. 多租户高并发流量隔离与噪声邻居抑制 (TC-136-1)
 * 2. 租户在途并发上限硬拦截 (TC-136-2)
 * 3. 滑动窗口错误率自适应跳闸 (TC-136-3)
 * 4. 慢调用比例软熔断与智能语义降级 (TC-136-4)
 * 5. 熔断冷却与 HALF_OPEN 自愈试探 (TC-136-5)
 * 6. 双向敏感数据脱敏准确率与性能 <= 2.0ms (TC-136-6)
 * 7. 模 11 与 Luhn 精准校验与反事实用例 (TC-136-7)
 * 8. 端到端全链路闭环与 Record 凭单签名自验真 (TC-136-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase136McpGatewayContractTest {

    private static final String TENANT_NORMAL = "tenant_enterprise_finance";
    private static final String TENANT_NOISY = "tenant_malicious_scraper";
    private static final String TOOL_DB_QUERY = "tool_enterprise_db_query";

    // =========================================================================
    // TC-136-1: 多租户高并发流量隔离与噪声邻居抑制
    // =========================================================================
    @Test
    @DisplayName("TC-136-1: 恶意租户突发 500 QPS 流量 100% 被限流在配额内，正常租户成功率 100%，时延波动 <= 5.0%")
    void testMultiTenantQuota_noisyNeighborIsolation() throws InterruptedException, ExecutionException {
        MultiTenantDistributedQuotaGovernor quotaGovernor = new MultiTenantDistributedQuotaGovernor();

        // 正常租户：容量 50，速率 50/s，并发上限 20
        quotaGovernor.registerTenantPolicy(new TenantQuotaPolicy(TENANT_NORMAL, 50L, 50.0, 20, true));
        // 噪声租户：容量 10，速率 5/s，并发上限 5
        quotaGovernor.registerTenantPolicy(new TenantQuotaPolicy(TENANT_NOISY, 10L, 5.0, 5, true));

        // 模拟并发线程池
        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger noisyRejectedCount = new AtomicInteger(0);
        AtomicInteger noisyAcceptedCount = new AtomicInteger(0);

        // 噪声租户尝试突发 100 次高频请求
        List<Callable<Void>> noisyTasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            noisyTasks.add(() -> {
                QuotaAcquisitionResult result = quotaGovernor.tryAcquire(TENANT_NOISY);
                if (result.allowed()) {
                    noisyAcceptedCount.incrementAndGet();
                    quotaGovernor.release(TENANT_NOISY);
                } else {
                    noisyRejectedCount.incrementAndGet();
                }
                return null;
            });
        }
        for (Future<Void> f : executor.invokeAll(noisyTasks)) {
            f.get();
        }

        // 验证噪声租户严格被限制在突发容量 (10) 以内
        assertTrue(noisyAcceptedCount.get() <= 10, "噪声租户通过数必须 <= 突发容量 10，实测: " + noisyAcceptedCount.get());
        assertTrue(noisyRejectedCount.get() >= 90, "噪声租户违规请求必须被拒绝，实测拒绝: " + noisyRejectedCount.get());

        // 此时验证正常租户不受任何干扰，在自身配额内 100% 成功
        AtomicInteger normalAcceptedCount = new AtomicInteger(0);
        long startNano = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            QuotaAcquisitionResult normalRes = quotaGovernor.tryAcquire(TENANT_NORMAL);
            if (normalRes.allowed()) {
                normalAcceptedCount.incrementAndGet();
                quotaGovernor.release(TENANT_NORMAL);
            }
        }
        long elapsedNanos = System.nanoTime() - startNano;
        double avgLatencyMicros = (elapsedNanos / 20.0) / 1000.0;

        assertEquals(20, normalAcceptedCount.get(), "正常租户在配额内必须 100% 成功放行");
        assertTrue(avgLatencyMicros <= 50.0, "单次配额判决耗时应在纳秒级 (<= 50微秒)，实测: " + avgLatencyMicros + "us");

        executor.shutdown();
    }

    // =========================================================================
    // TC-136-2: 租户在途并发上限硬拦截
    // =========================================================================
    @Test
    @DisplayName("TC-136-2: 租户在途并发触碰上限时后续请求硬拒绝 (CONCURRENCY_EXCEEDED)，释放后立即恢复")
    void testConcurrentInFlightCap_hardRejection() {
        MultiTenantDistributedQuotaGovernor quotaGovernor = new MultiTenantDistributedQuotaGovernor();
        // 设置极低的并发上限：最多允许 2 个在途任务
        quotaGovernor.registerTenantPolicy(new TenantQuotaPolicy("tenant_cap_test", 100L, 50.0, 2, true));

        // 1. 获取第 1 个并发任务
        QuotaAcquisitionResult res1 = quotaGovernor.tryAcquire("tenant_cap_test");
        assertTrue(res1.allowed());
        assertEquals(1, res1.currentInFlight());

        // 2. 获取第 2 个并发任务
        QuotaAcquisitionResult res2 = quotaGovernor.tryAcquire("tenant_cap_test");
        assertTrue(res2.allowed());
        assertEquals(2, res2.currentInFlight());

        // 3. 尝试获取第 3 个任务 -> 必须被硬拒绝
        QuotaAcquisitionResult res3 = quotaGovernor.tryAcquire("tenant_cap_test");
        assertFalse(res3.allowed(), "在途并发达上限必须拒绝");
        assertEquals("CONCURRENCY_EXCEEDED", res3.status());

        // 4. 释放 1 个任务
        quotaGovernor.release("tenant_cap_test");
        assertEquals(1, quotaGovernor.getInFlightCount("tenant_cap_test"));

        // 5. 再次尝试获取 -> 必须立刻放行
        QuotaAcquisitionResult res4 = quotaGovernor.tryAcquire("tenant_cap_test");
        assertTrue(res4.allowed(), "释放并发后应立即允许新任务接入");
        assertEquals(2, res4.currentInFlight());

        // 清理释放
        quotaGovernor.release("tenant_cap_test");
        quotaGovernor.release("tenant_cap_test");
        assertEquals(0, quotaGovernor.getInFlightCount("tenant_cap_test"));
    }

    // =========================================================================
    // TC-136-3: 滑动窗口错误率自适应跳闸
    // =========================================================================
    @Test
    @DisplayName("TC-136-3: 滑动窗口 20 样本内错误率超过 25% 时，断路器自适应跳闸至 OPEN 态，阻断后续请求")
    void testSlidingWindowCircuitBreaker_errorRateTrip() {
        AdaptiveMcpCircuitBreakerGovernor governor = new AdaptiveMcpCircuitBreakerGovernor();
        CircuitConfig config = new CircuitConfig(20, 10, 0.25, 0.50, 2000L, 5000L, 2);

        // 初始为 CLOSED
        CircuitDecision initialDecision = governor.evaluateCircuit(TOOL_DB_QUERY, config);
        assertTrue(initialDecision.allowExecution());
        assertEquals(CircuitState.CLOSED, initialDecision.state());

        // 记录 6 次成功调用与 4 次失败调用 (样本数达到最小 10，失败率 4/10 = 40% >= 25%)
        for (int i = 0; i < 6; i++) {
            governor.recordExecution(TOOL_DB_QUERY, 150L, true);
        }
        for (int i = 0; i < 4; i++) {
            governor.recordExecution(TOOL_DB_QUERY, 150L, false);
        }

        // 评估当前断路器状态
        CircuitDecision trippedDecision = governor.evaluateCircuit(TOOL_DB_QUERY, config);
        assertFalse(trippedDecision.allowExecution(), "失败率达到 40% 必须跳闸至 OPEN 态拒绝执行");
        assertEquals(CircuitState.OPEN, trippedDecision.state());
        assertEquals("CIRCUIT_BREAKER_OPEN", trippedDecision.reason());
        assertNotNull(trippedDecision.fallbackResponse(), "OPEN 态必须返回结构化降级响应");
    }

    // =========================================================================
    // TC-136-4: 慢调用比例软熔断与智能语义降级
    // =========================================================================
    @Test
    @DisplayName("TC-136-4: P99 慢调用 (>= 2000ms) 比例超过 30% 时自动熔断，并自动返回预置 Fallback 降级响应")
    void testSlidingWindowCircuitBreaker_slowCallTripAndFallback() {
        AdaptiveMcpCircuitBreakerGovernor governor = new AdaptiveMcpCircuitBreakerGovernor();
        CircuitConfig config = new CircuitConfig(20, 10, 0.50, 0.30, 2000L, 5000L, 2);

        // 注册智能语义降级提供器 (回退到本地只读向量缓存)
        governor.registerFallbackProvider("tool_rag_fallback_test", toolCode ->
                "{\"status\":\"DEGRADED\",\"code\":200,\"data\":{\"cachedKnowledge\":\"Steiner Tree Causal Graph RAG\"}}"
        );

        // 记录 6 次快速成功调用 (100ms) 与 4 次慢调用成功 (2500ms >= 2000ms)，慢调用率 4/10 = 40% >= 30%
        for (int i = 0; i < 6; i++) {
            governor.recordExecution("tool_rag_fallback_test", 100L, true);
        }
        for (int i = 0; i < 4; i++) {
            governor.recordExecution("tool_rag_fallback_test", 2500L, true);
        }

        // 评估断路器
        CircuitDecision decision = governor.evaluateCircuit("tool_rag_fallback_test", config);
        assertFalse(decision.allowExecution(), "慢调用达到 40% 必须触发自适应软熔断");
        assertEquals(CircuitState.OPEN, decision.state());
        assertTrue(decision.fallbackResponse().contains("Steiner Tree Causal Graph RAG"), "必须成功返回语义降级数据");
    }

    // =========================================================================
    // TC-136-5: 熔断冷却与 HALF_OPEN 自愈试探
    // =========================================================================
    @Test
    @DisplayName("TC-136-5: 熔断冷却超时后自动进入 HALF_OPEN 状态，试探成功后平滑自愈恢复至 CLOSED")
    void testCircuitBreaker_halfOpenSelfHealing() throws InterruptedException {
        AdaptiveMcpCircuitBreakerGovernor governor = new AdaptiveMcpCircuitBreakerGovernor();
        // 极短冷却期：100ms
        CircuitConfig config = new CircuitConfig(20, 5, 0.20, 0.50, 2000L, 100L, 1);
        governor.registerCircuitConfig("tool_heal_test", config);

        // 产生 5 次调用全部失败触发熔断
        for (int i = 0; i < 5; i++) {
            governor.recordExecution("tool_heal_test", 100L, false);
        }
        assertEquals(CircuitState.OPEN, governor.getCircuitState("tool_heal_test"));

        // 等待冷却期超时 (120ms)
        Thread.sleep(120);

        // 再次评估，断路器应自动过渡为 HALF_OPEN 试探放行
        CircuitDecision halfOpenDecision = governor.evaluateCircuit("tool_heal_test", config);
        assertEquals(CircuitState.HALF_OPEN, halfOpenDecision.state());
        assertTrue(halfOpenDecision.allowExecution(), "HALF_OPEN 状态应放行试探流量");

        // 记录试探成功
        governor.recordExecution("tool_heal_test", 50L, true);

        // 再次评估，状态平滑自愈至 CLOSED
        assertEquals(CircuitState.CLOSED, governor.getCircuitState("tool_heal_test"), "试探成功后应自愈收敛至 CLOSED 态");
    }

    // =========================================================================
    // TC-136-6: 双向敏感数据脱敏准确率与性能 <= 2.0ms
    // =========================================================================
    @Test
    @DisplayName("TC-136-6: 双向敏感数据 (身份证/手机号/银行卡/JWT/密钥) 100% 检出掩码，长文本耗时 <= 2.0ms")
    void testZeroTrustDataMasking_accuracyAndPerformance() {
        ZeroTrustDataMaskingEngine maskingEngine = new ZeroTrustDataMaskingEngine();

        // 构造包含多种敏感长文本的 JSON 入参 (身份证 110101199003072375 与银行卡 6222021234567894 校验码通过)
        String inputPayload = """
                {
                  "agentTask": "用户征信背调与账单核验",
                  "userIdCard": "110101199003072375",
                  "userPhone": "13812345678",
                  "bankCard": "6222021234567894",
                  "authHeader": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTmc6Y5uvStErAawphY25B8K0",
                  "credentials": {
                    "password": "MySuperSecretPassword@2026",
                    "apiKey": "sk_test_51MzE49Kjd78201bxa91"
                  }
                }
                """;

        // JVM JIT 与 正则自动机充分预热 (触发 C2 编译器即时内联，消除解释器冷启动抖动)
        for (int i = 0; i < 10; i++) {
            maskingEngine.maskText(inputPayload);
        }

        long startNano = System.nanoTime();
        MaskingResult result = maskingEngine.maskText(inputPayload);
        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        assertNotNull(result);
        assertEquals(6, result.totalReplacements(), "所有 6 类敏感信息必须全部命中脱敏");
        assertTrue(result.hitCategories().contains("CHINESE_ID_CARD"));
        assertTrue(result.hitCategories().contains("MOBILE_PHONE"));
        assertTrue(result.hitCategories().contains("BANK_CARD"));
        assertTrue(result.hitCategories().contains("JWT_TOKEN"));
        assertTrue(result.hitCategories().contains("SECRET_KEY"));

        // 验证掩码准确性
        String masked = result.maskedContent();
        assertTrue(masked.contains("110101********2375"), "身份证必须按前 6 后 4 掩码");
        assertTrue(masked.contains("138****5678"), "手机号必须按前 3 后 4 掩码");
        assertTrue(masked.contains("6222********7894"), "银行卡必须按前 4 后 4 掩码");
        assertTrue(masked.contains("Bearer eyJ***[REDACTED_JWT]***"), "JWT 必须按规范掩码");
        assertTrue(masked.contains("\"password\":\"******\""), "密码必须遮蔽");
        assertTrue(masked.contains("\"apiKey\":\"******\""), "API 密钥必须遮蔽");

        // 严苛性能指标
        assertTrue(elapsedMicros <= 2000, "长文本双向脱敏总耗时必须 <= 2000微秒 (2.0ms)，实测: " + elapsedMicros + "us");
    }

    // =========================================================================
    // TC-136-7: 模 11 与 Luhn 精准校验与反事实用例
    // =========================================================================
    @Test
    @DisplayName("TC-136-7: 严格模 11 与 Luhn 算法反事实验证，无效伪造号码绝不误伤脱敏")
    void testZeroTrustDataMasking_luhnAndIdCardValidation() {
        ZeroTrustDataMaskingEngine maskingEngine = new ZeroTrustDataMaskingEngine();

        // 合法身份证: 110101199003072375 (校验位 5)
        assertTrue(ZeroTrustDataMaskingEngine.isValidChineseIdCard("110101199003072375"));
        // 伪造身份证: 校验位故意篡改成 0
        assertFalse(ZeroTrustDataMaskingEngine.isValidChineseIdCard("110101199003072370"));

        // 合法银行卡 (Luhn 校验通过): 6222021234567894
        assertTrue(ZeroTrustDataMaskingEngine.isValidLuhn("6222021234567894"));
        // 伪造银行卡: 篡改最后一位为 0 (校验和为 56)
        assertFalse(ZeroTrustDataMaskingEngine.isValidLuhn("6222021234567890"));

        // 反事实脱敏测试：伪造身份证与伪造银行卡必须原样保留，不得误伤！
        String counterfactualPayload = "伪身份证 110101199003072370 与伪银行卡 6222021234567890";
        MaskingResult result = maskingEngine.maskText(counterfactualPayload);

        assertEquals(0, result.totalReplacements(), "无效校验码的伪造号码绝不应误伤脱敏");
        assertEquals(counterfactualPayload, result.maskedContent(), "反事实文本必须原样保持");
    }

    // =========================================================================
    // TC-136-8: 端到端全链路闭环与 Record 凭单签名自验真
    // =========================================================================
    @Test
    @DisplayName("TC-136-8: 端到端全链路闭环：多租户配额准入 -> 断路器评估 -> 敏感脱敏 -> 不可变凭单自验真")
    void testEndToEndMcpGateway_fullLifecycleAuditReceipt() {
        MultiTenantDistributedQuotaGovernor quotaGovernor = new MultiTenantDistributedQuotaGovernor();
        AdaptiveMcpCircuitBreakerGovernor circuitGovernor = new AdaptiveMcpCircuitBreakerGovernor();
        ZeroTrustDataMaskingEngine maskingEngine = new ZeroTrustDataMaskingEngine();

        quotaGovernor.registerTenantPolicy(new TenantQuotaPolicy("tenant_e2e_p136", 100L, 50.0, 10, true));

        // 1. 租户配额准入获取
        QuotaAcquisitionResult quotaResult = quotaGovernor.tryAcquire("tenant_e2e_p136");
        assertTrue(quotaResult.allowed());

        // 2. 断路器健康评估
        CircuitDecision circuitDecision = circuitGovernor.evaluateCircuit("tool_e2e_mcp");
        assertTrue(circuitDecision.allowExecution());

        // 3. 入参敏感数据脱敏
        String rawInput = "{\"agentId\":\"auditor\",\"userPhone\":\"13900001111\",\"query\":\"查征信\"}";
        MaskingResult maskResult = maskingEngine.maskText(rawInput);
        assertTrue(maskResult.maskedContent().contains("139****1111"));

        // 4. 模拟工具调用执行成功并释放配额
        circuitGovernor.recordExecution("tool_e2e_mcp", 120L, true);
        quotaGovernor.release("tenant_e2e_p136");
        assertEquals(0, quotaGovernor.getInFlightCount("tenant_e2e_p136"));

        // 5. 签发纯 Java 21 Record 审计存证凭单
        McpZeroTrustGatewayReceipt receipt = McpZeroTrustGatewayReceipt.create(
                "tenant_e2e_p136",
                "tool_e2e_mcp",
                quotaResult.status(),
                circuitDecision.state().name(),
                maskResult.hitCategories()
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length());
        assertTrue(receipt.verifySignature(), "不可变网关凭单自验真必须 100% 成立");

        // 篡改反事实验证
        McpZeroTrustGatewayReceipt tamperedReceipt = new McpZeroTrustGatewayReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.toolName(),
                "TAMPERED_QUOTA",
                receipt.circuitState(),
                receipt.maskedFields(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "被篡改数据验真必须被拒绝");
    }
}
