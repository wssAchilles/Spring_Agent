package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.qiantong.qknow.module.kmc.service.rag.cache.EnhancedSemanticCacheService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 11: 增强语义缓存弹性与防漂移契约测试
 */
@ExtendWith(MockitoExtension.class)
class EnhancedSemanticCacheContractTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EmbeddingModel embeddingModel;

    private EnhancedSemanticCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new EnhancedSemanticCacheService(jdbcTemplate);
    }

    @Test
    @DisplayName("契约验证：高并发读写下无锁竞争死锁，且命中率稳定")
    void testExactCacheHighConcurrency() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger hitCount = new AtomicInteger(0);

        // 先预热写入一条数据
        cacheService.putExactCache(1L, 1L, List.of(10L), "java", "Spring 事务传播机制是什么",
                "支持 REQUIRED, REQUIRES_NEW 等", "[]", Duration.ofMinutes(10));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        var res = cacheService.findExact(1L, 1L, List.of(10L), "java", "Spring 事务传播机制是什么");
                        if (res.isPresent()) {
                            hitCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "高并发读写必须在 5 秒内无死锁完成");
        assertEquals(threadCount * operationsPerThread, hitCount.get(), "并发读取全部必须成功命中");
    }

    @Test
    @DisplayName("契约验证：语义漂移防护门禁，否定词/反向意图必须被拦截 (开启 vs 关闭)")
    void testSemanticDriftNegationRejected() {
        // 传入查询：“如何开启声明式事务”
        String incomingQuery = "如何开启声明式事务";
        // 缓存中原有条目：“如何关闭声明式事务”
        String cachedQuery = "如何关闭声明式事务";

        // 验证门禁判定方法
        boolean passed = cacheService.passSemanticGating(incomingQuery, cachedQuery);

        assertFalse(passed, "存在'开'与'关'反向动作极性差异时，必须判定门禁未通过，杜绝反义误命中！");
    }

    @Test
    @DisplayName("契约验证：相同语气与相近词汇正常通过语义漂移门禁")
    void testSemanticDriftConsistentPasses() {
        String incomingQuery = "Spring 的声明式事务怎么用";
        String cachedQuery = "Spring 声明式事务如何使用";

        boolean passed = cacheService.passSemanticGating(incomingQuery, cachedQuery);

        assertTrue(passed, "语气一致且核心词相近时应正常通过门禁");
    }

    @Test
    @DisplayName("契约验证：缓存防穿透空哨兵机制，空结果短期拦截")
    void testCachePenetrationEmptySentinel() {
        String query = "一个知识库中绝对不存在的超长随机测试问题xyz987";

        // 第一次查询无果，写入空哨兵
        cacheService.putEmptySentinel(1L, 1L, List.of(10L), "deepseek-chat", query, Duration.ofSeconds(60));

        // 第二次查询，应当直接命中空哨兵并被拦截为 Optional.empty()，但标记为已拦截
        boolean isSentinel = cacheService.isBlockedBySentinel(1L, 1L, List.of(10L), "deepseek-chat", query);

        assertTrue(isSentinel, "连续查询无解问题时必须直接被空哨兵拦截，杜绝反复穿透至后端模型");
    }

    @Test
    @DisplayName("契约验证：缓存防雪崩 Jitter 随机抖动生成")
    void testCacheAvalancheJitter() {
        Duration baseTtl = Duration.ofHours(24);
        Duration jittered1 = cacheService.calculateJitteredTtl(baseTtl);
        Duration jittered2 = cacheService.calculateJitteredTtl(baseTtl);

        // 断言在 baseTtl 的 85% ~ 115% 范围内浮动
        long minSeconds = (long) (baseTtl.toSeconds() * 0.85);
        long maxSeconds = (long) (baseTtl.toSeconds() * 1.15);

        assertTrue(jittered1.toSeconds() >= minSeconds && jittered1.toSeconds() <= maxSeconds, "TTL 必须在合理扰动区间内");
        assertTrue(jittered2.toSeconds() >= minSeconds && jittered2.toSeconds() <= maxSeconds, "TTL 必须在合理扰动区间内");
    }
}
