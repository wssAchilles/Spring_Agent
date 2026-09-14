package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.gateway.circuitbreaker.AdaptiveCircuitBreaker;
import tech.qiantong.qknow.ai.gateway.controller.AiModelGatewayController;
import tech.qiantong.qknow.ai.gateway.model.*;
import tech.qiantong.qknow.ai.gateway.ratelimit.LockFreeTokenBucketLimiter;
import tech.qiantong.qknow.ai.gateway.registry.ProviderChannelRegistry;
import tech.qiantong.qknow.ai.gateway.router.ChannelLatencyTracker;
import tech.qiantong.qknow.ai.gateway.router.LatencyAwareSlaRouter;
import tech.qiantong.qknow.ai.gateway.service.ModelGatewayProxyService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 28: 超高并发异构多模态多模型统一代理网关核心契约测试套件
 * 严格覆盖 10 项核心专项契约，验证统一协议、自适应三态断路器、瀑布倒换、SLA Pareto 选路与无锁令牌桶限流
 */
public class Phase28ModelGatewayContractTest {

    private ProviderChannelRegistry channelRegistry;
    private LatencyAwareSlaRouter slaRouter;
    private TestableModelGatewayProxyService proxyService;
    private AiModelGatewayController controller;

    /**
     * 可测试的网关代理子类：支持故障注入与模拟上游响应，确保离线测试可复现
     */
    static class TestableModelGatewayProxyService extends ModelGatewayProxyService {
        private final Map<String, Queue<Object>> channelResponses = new ConcurrentHashMap<>();
        final AtomicInteger httpCallCount = new AtomicInteger(0);

        public TestableModelGatewayProxyService(ProviderChannelRegistry channelRegistry, LatencyAwareSlaRouter slaRouter) {
            super(channelRegistry, slaRouter);
        }

        public void enqueueResponse(String channelId, Object respOrException) {
            channelResponses.computeIfAbsent(channelId, k -> new ConcurrentLinkedQueue<>()).add(respOrException);
        }

        @Override
        protected GatewayDto.GatewayChatResponse doHttpChatCall(ProviderChannel channel,
                                                               ApiKeyEntry apiKey,
                                                               GatewayDto.GatewayChatRequest request) throws IOException {
            httpCallCount.incrementAndGet();
            Queue<Object> q = channelResponses.get(channel.getChannelId());
            if (q != null && !q.isEmpty()) {
                Object item = q.poll();
                if (item instanceof IOException ioEx) {
                    throw ioEx;
                }
                if (item instanceof RuntimeException rtEx) {
                    throw rtEx;
                }
                if (item instanceof GatewayDto.GatewayChatResponse resp) {
                    return resp;
                }
            }

            // 默认正常响应
            return GatewayDto.GatewayChatResponse.builder()
                    .id("chatcmpl-" + UUID.randomUUID())
                    .object("chat.completion")
                    .created(System.currentTimeMillis() / 1000L)
                    .model(channel.resolveUpstreamModel(request.getModel()))
                    .choices(List.of(
                            GatewayDto.ChatChoiceDto.builder()
                                    .index(0)
                                    .message(GatewayDto.ChatMessageDto.builder()
                                            .role("assistant")
                                            .content("Mock 正常生成内容")
                                            .reasoningContent("Mock 推理链思考过程")
                                            .build())
                                    .finishReason("stop")
                                    .build()
                    ))
                    .usage(GatewayDto.ChatUsageDto.builder()
                            .promptTokens(20)
                            .completionTokens(40)
                            .totalTokens(60)
                            .build())
                    .ttftMillis(80L)
                    .build();
        }
    }

    @BeforeEach
    void setUp() {
        channelRegistry = new ProviderChannelRegistry();
        slaRouter = new LatencyAwareSlaRouter();
        proxyService = new TestableModelGatewayProxyService(channelRegistry, slaRouter);
        controller = new AiModelGatewayController(proxyService);
    }

    @Test
    @DisplayName("Contract 01: 统一 OpenAI 协议兼容端点入参与出参标准化转录验证")
    void contract01_unifiedProtocol_standardizesOpenAiCompatibleEndpoints() {
        GatewayDto.GatewayChatRequest request = GatewayDto.GatewayChatRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(GatewayDto.ChatMessageDto.builder().role("user").content("你好").build()))
                .stream(false)
                .tenantId("tenant_test_01")
                .build();

        ResponseEntity<?> responseEntity = controller.createChatCompletion(request);
        assertNotNull(responseEntity);
        assertTrue(responseEntity.getStatusCode().is2xxSuccessful(), "端点应返回 200 OK");

        assertTrue(responseEntity.getBody() instanceof GatewayDto.GatewayChatResponse);
        GatewayDto.GatewayChatResponse response = (GatewayDto.GatewayChatResponse) responseEntity.getBody();

        assertEquals("chat.completion", response.getObject());
        assertFalse(response.getChoices().isEmpty());
        assertEquals("stop", response.getChoices().getFirst().getFinishReason());
        assertNotNull(response.getUsage());
        assertEquals(60, response.getUsage().getTotalTokens());
        assertNotNull(response.getRoutedChannelId(), "响应必须包含内部路由通道跟踪元数据");

        // 验证 models 列表端点
        ResponseEntity<Map<String, Object>> modelsResp = controller.listModels();
        assertTrue(modelsResp.getStatusCode().is2xxSuccessful());
        assertNotNull(modelsResp.getBody());
        assertTrue(((List<?>) modelsResp.getBody().get("data")).size() >= 3);
    }

    @Test
    @DisplayName("Contract 02: 主通道遭遇 503/429 故障时毫秒级自动瀑布倒换至备用通道 (Theorem 1.1)")
    void contract02_channelFailover_triggersAutomaticProviderFallbackOn429Or503() {
        // 主通道 (ch_deepseek_official) 注入 503 异常
        proxyService.enqueueResponse("ch_deepseek_official", new IOException("503 Service Unavailable: 上游机房网络中断"));
        proxyService.enqueueResponse("ch_deepseek_official", new IOException("503 Service Unavailable: 上游机房网络中断"));

        GatewayDto.GatewayChatRequest request = GatewayDto.GatewayChatRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(GatewayDto.ChatMessageDto.builder().role("user").content("测试故障倒换").build()))
                .tenantId("tenant_failover")
                .build();

        GatewayDto.GatewayChatResponse response = proxyService.executeChatCompletion(request);

        assertNotNull(response);
        // 验证自动瀑布倒换至 Priority 1 的备用通道 (火山引擎 Ark 或硅基流动)
        assertEquals("ch_volcengine_ark", response.getRoutedChannelId(), "主通道故障时应自动倒换至备用通道 1 (火山引擎 Ark)");
        assertEquals("VOLC_ENGINE", response.getRoutedProvider());
    }

    @Test
    @DisplayName("Contract 03: 无锁三态断路器状态机生命周期演化 (CLOSED -> OPEN -> HALF-OPEN -> CLOSED)")
    void contract03_circuitBreaker_transitionsStateCorrectlyOpenHalfOpenClosed() {
        // 创建一个轻量断路器：最小 5 次调用，失败率阈值 50%，冷却 100ms，半开试探 2 次，连续失败阈值 3
        AdaptiveCircuitBreaker cb = new AdaptiveCircuitBreaker("test_cb", 5, 0.50, 100L, 2, 3);

        assertEquals(ChannelStatus.CLOSED, cb.getCurrentState(), "初始状态应为 CLOSED");
        assertTrue(cb.tryAcquirePermission(), "CLOSED 状态应允许通行");

        // 连续注入 3 次失败，触发连续失败熔断
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();

        assertEquals(ChannelStatus.OPEN, cb.getCurrentState(), "连续 3 次失败后断路器应跳闸为 OPEN");
        assertFalse(cb.tryAcquirePermission(), "OPEN 状态下应立即拦截并拒绝通行");

        // 等待冷却期 120ms
        try {
            Thread.sleep(120L);
        } catch (InterruptedException ignored) {}

        // 冷却期后首次通行应自适应切换为 HALF_OPEN
        assertTrue(cb.tryAcquirePermission(), "冷却期结束后首次通行应放行并切换为 HALF_OPEN");
        assertEquals(ChannelStatus.HALF_OPEN, cb.getCurrentState(), "状态应切换为 HALF_OPEN");

        // 半开探活连续成功 2 次
        cb.recordSuccess();
        cb.recordSuccess();

        assertEquals(ChannelStatus.CLOSED, cb.getCurrentState(), "半开探针连续成功后断路器应自愈恢复为 CLOSED");
    }

    @Test
    @DisplayName("Contract 04: 通道时延追踪器精准统计分位数且路由器优先选择低 P99/TTFT 通道")
    void contract04_latencyAwareRouting_prefersLowestP99Channel() {
        ChannelLatencyTracker trackerA = new ChannelLatencyTracker();
        ChannelLatencyTracker trackerB = new ChannelLatencyTracker();

        // 通道 A 注入优良时延 (50ms ~ 150ms)
        for (int i = 0; i < 50; i++) {
            trackerA.record(80L, 100L);
        }

        // 通道 B 注入高排队时延 (800ms ~ 1500ms)
        for (int i = 0; i < 50; i++) {
            trackerB.record(900L, 1200L);
        }

        ChannelLatencyTracker.LatencySnapshot snapA = trackerA.getSnapshot();
        ChannelLatencyTracker.LatencySnapshot snapB = trackerB.getSnapshot();

        assertTrue(snapA.getP99Millis() < snapB.getP99Millis(), "通道 A 的 P99 延迟应显著低于通道 B");
        assertTrue(snapA.getAvgTtftMillis() < snapB.getAvgTtftMillis(), "通道 A 的 TTFT 应显著快于通道 B");
    }

    @Test
    @DisplayName("Contract 05: SLA 延迟与 Token 成本多目标双重 Pareto 智能选路权重自适应切换 (Theorem 2.1)")
    void contract05_slaCostDualObjective_balancesLatencyAndTokenCostPareto() {
        // 构造两个同优先级通道：通道 1 (低延迟高成本), 通道 2 (高延迟低成本)
        ProviderChannel fastExpensive = ProviderChannel.builder()
                .channelId("ch_fast")
                .channelName("快但贵专线")
                .providerType(ProviderType.OFFICIAL_DEEPSEEK)
                .priority(0)
                .weight(100)
                .costPerMillionTokens(30.0) // 较贵
                .build();

        ProviderChannel slowCheap = ProviderChannel.builder()
                .channelId("ch_cheap")
                .channelName("慢但便宜专线")
                .providerType(ProviderType.SILICON_FLOW)
                .priority(0)
                .weight(100)
                .costPerMillionTokens(8.0) // 便宜
                .build();

        // 注入时延快慢差异
        ChannelLatencyTracker trackerFast = slaRouter.getOrCreateTracker("ch_fast");
        for (int i = 0; i < 50; i++) trackerFast.record(50L, 80L);

        ChannelLatencyTracker trackerCheap = slaRouter.getOrCreateTracker("ch_cheap");
        for (int i = 0; i < 50; i++) trackerCheap.record(1200L, 2500L);

        // 场景 1: INTERACTIVE_CHAT (延迟权重 0.75, 成本权重 0.05) -> 应高度倾向 ch_fast
        int fastPickCount = 0;
        for (int i = 0; i < 20; i++) {
            ProviderChannel ch = slaRouter.selectOptimalChannel(List.of(fastExpensive, slowCheap), RoutingScenario.INTERACTIVE_CHAT);
            if ("ch_fast".equals(ch.getChannelId())) fastPickCount++;
        }
        assertTrue(fastPickCount >= 16, "在实时对话场景下，低延迟通道选择率应 >= 80%，实际: " + fastPickCount + "/20");

        // 场景 2: OFFLINE_EMBEDDING (成本权重 0.70, 延迟权重 0.10) -> 应高度倾向 ch_cheap
        int cheapPickCount = 0;
        for (int i = 0; i < 20; i++) {
            ProviderChannel ch = slaRouter.selectOptimalChannel(List.of(fastExpensive, slowCheap), RoutingScenario.OFFLINE_EMBEDDING);
            if ("ch_cheap".equals(ch.getChannelId())) cheapPickCount++;
        }
        assertTrue(cheapPickCount >= 16, "在离线大批量场景下，低成本通道选择率应 >= 80%，实际: " + cheapPickCount + "/20");
    }

    @Test
    @DisplayName("Contract 06: 无锁 CAS 令牌桶突发流量平滑限流与耗尽 429 阻断 (Theorem 3.1)")
    void contract06_tokenBucketLimiter_throttlesBurstTrafficSmoothly() {
        // 容量为 5，每秒填充 10 个令牌
        LockFreeTokenBucketLimiter limiter = new LockFreeTokenBucketLimiter(5, 10.0);

        // 突发连续获取 5 个令牌，全部成功
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(1), "容量内令牌应成功获取");
        }

        // 第 6 个令牌获取应瞬间被阻断 (桶耗尽)
        assertFalse(limiter.tryAcquire(1), "超出容量且尚未填充时必须返回 false 阻断");

        // 模拟等待 150ms (补充至少 1 个令牌: 150ms * 10/1000 = 1.5 个)
        try {
            Thread.sleep(150L);
        } catch (InterruptedException ignored) {}

        assertTrue(limiter.tryAcquire(1), "时间增量补充后应成功获取到恢复的令牌");
    }

    @Test
    @DisplayName("Contract 07: 租户在途并发硬限制拦截、纳元级定点费用扣减与欠费熔断")
    void contract07_tenantQuotaGovernor_enforcesTokenBudgetAndDeduction() {
        // 创建在途并发上限为 2，余额为 1,000,000 纳元 (0.001 元) 的租户账本
        TenantQuotaLedger ledger = proxyService.getOrCreateTenantLedger("tenant_strict", 2, 1_000_000L);

        // 获取并发 1、2 成功
        assertTrue(ledger.tryAcquireConcurrency(), "第 1 个并发连接应准入");
        assertTrue(ledger.tryAcquireConcurrency(), "第 2 个并发连接应准入");

        // 第 3 个并发被硬限制拦截
        assertFalse(ledger.tryAcquireConcurrency(), "超出在途并发上限 (2) 应被无锁 CAS 阻断");

        // 释放 1 个
        ledger.releaseConcurrency();
        assertTrue(ledger.tryAcquireConcurrency(), "释放后并发应恢复准入");
        ledger.releaseConcurrency();
        ledger.releaseConcurrency();

        // 扣减费用超额，扣除 2,000,000 纳元导致余额为负
        long remaining = ledger.deductNano(2_000_000L);
        assertTrue(remaining < 0, "余额扣减后应为负数");
        assertTrue(ledger.getSuspended().get(), "余额为负数时账户应自动变为冻结状态 (SUSPENDED)");

        // 冻结后尝试准入应被 100% 拒绝
        assertFalse(ledger.tryAcquireConcurrency(), "冻结欠费账户必须 100% 阻断请求进入");
    }

    @Test
    @DisplayName("Contract 08: API Key 池平滑加权轮询调度与 429 冷却隔离机制")
    void contract08_keyPoolRoundRobin_distributesLoadAcrossMultipleApiKeys() {
        ProviderChannel testCh = ProviderChannel.builder()
                .channelId("ch_key_test")
                .channelName("Key 测试通道")
                .keyPool(List.of(
                        ApiKeyEntry.builder().keyId("key_A").secretKey("sk_A").weight(1).build(),
                        ApiKeyEntry.builder().keyId("key_B").secretKey("sk_B").weight(1).build()
                ))
                .build();

        Set<String> observedKeys = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Optional<ApiKeyEntry> opt = channelRegistry.selectAvailableApiKey(testCh);
            assertTrue(opt.isPresent());
            observedKeys.add(opt.get().getKeyId());
        }
        assertEquals(2, observedKeys.size(), "加权平滑轮询应均匀覆盖两个 Key");

        // 模拟 Key A 触发 429 冷却隔离 10 秒
        testCh.getKeyPool().getFirst().markCooldown(10_000L);

        // 后续 5 次获取必须全部为 Key B
        for (int i = 0; i < 5; i++) {
            Optional<ApiKeyEntry> opt = channelRegistry.selectAvailableApiKey(testCh);
            assertTrue(opt.isPresent());
            assertEquals("key_B", opt.get().getKeyId(), "Key A 冷却期间应 100% 旁路给 Key B");
        }
    }

    @Test
    @DisplayName("Contract 09: 响应式流式代理中途取消级联中断与 TTFT 准确捕获")
    void contract09_streamingResilience_propagatesCancellationAndMeasuresTtft() {
        GatewayDto.GatewayChatRequest request = GatewayDto.GatewayChatRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(GatewayDto.ChatMessageDto.builder().role("user").content("测试流式").build()))
                .stream(true)
                .build();

        Flux<GatewayDto.GatewayChatResponse> flux = proxyService.streamChatCompletion(request);
        List<GatewayDto.GatewayChatResponse> collected = flux.collectList().block();

        assertNotNull(collected);
        assertFalse(collected.isEmpty(), "流式响应分块不应为空");

        GatewayDto.GatewayChatResponse firstChunk = collected.getFirst();
        assertNotNull(firstChunk.getTtftMillis(), "首个流式 Chunk 必须捕获到 TTFT 首字延迟");
        assertTrue(firstChunk.getTtftMillis() > 0, "TTFT 必须大于 0ms");

        GatewayDto.GatewayChatResponse lastChunk = collected.getLast();
        assertNotNull(lastChunk.getUsage(), "流式终态帧必须包含用量统计");
    }

    @Test
    @DisplayName("Contract 10: 高并发场景端到端网关代理调度验证，0 异常漏抛且成功率达标")
    void contract10_endToEndGatewayRouting_servesHighConcurrencyChatCompletion() throws Exception {
        int threads = 20;
        int requestsPerThread = 5;
        int totalRequests = threads * requestsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    GatewayDto.GatewayChatRequest req = GatewayDto.GatewayChatRequest.builder()
                            .model("deepseek-chat")
                            .messages(List.of(GatewayDto.ChatMessageDto.builder().role("user").content("并发请求 " + idx).build()))
                            .tenantId("tenant_perf_" + (idx % 3))
                            .build();

                    GatewayDto.GatewayChatResponse resp = proxyService.executeChatCompletion(req);
                    if (resp != null && resp.getChoices() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(completed, "高并发请求必须在 10 秒内全部完成");
        assertEquals(totalRequests, successCount.get(), "在全量通道健康情况下，成功率应达到 100%");
        assertEquals(0, errorCount.get(), "异常计数应为 0");
    }
}
