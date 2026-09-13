package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tech.qiantong.qknow.common.core.domain.entity.SysRole;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.CrossKbScoreCalibrator;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKbRetrievalCoordinator;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcKnowledgeBaseRespDTO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeBaseService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRoleService;
import tech.qiantong.qknow.module.kmc.service.rag.PermissionFilter;
import tech.qiantong.qknow.module.kmc.service.rag.cache.EnhancedSemanticCacheService;
import tech.qiantong.qknow.module.system.service.ISysRoleService;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 17: 多知识库联合检索并发编排、跨库得分校准重排、多租户 RBAC 零泄露隔离与统一全局上下文预算熔断治理专属契约测试
 */
class Phase17MultiKbRetrievalAndTenantGateTest {

    private MultiKbRetrievalCoordinator coordinator;
    private CrossKbScoreCalibrator calibrator;
    private PermissionFilter permissionFilter;
    private EnhancedSemanticCacheService semanticCacheService;

    private IKmcApiService mockKmcApi;
    private ThreadPoolTaskExecutor executor;

    private IKmcKnowledgeRoleService mockKnowledgeRoleService;
    private IKmcKnowledgeBaseService mockKnowledgeBaseService;
    private ISysRoleService mockSysRoleService;

    @BeforeEach
    void setUp() {
        mockKmcApi = mock(IKmcApiService.class);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("test-multi-kb-");
        executor.initialize();

        coordinator = new MultiKbRetrievalCoordinator(mockKmcApi, executor);
        calibrator = new CrossKbScoreCalibrator();

        mockKnowledgeRoleService = mock(IKmcKnowledgeRoleService.class);
        mockKnowledgeBaseService = mock(IKmcKnowledgeBaseService.class);
        mockSysRoleService = mock(ISysRoleService.class);

        permissionFilter = new PermissionFilter();
        org.springframework.test.util.ReflectionTestUtils.setField(permissionFilter, "kmcKnowledgeRoleService", mockKnowledgeRoleService);
        org.springframework.test.util.ReflectionTestUtils.setField(permissionFilter, "kmcKnowledgeBaseService", mockKnowledgeBaseService);
        org.springframework.test.util.ReflectionTestUtils.setField(permissionFilter, "sysRoleService", mockSysRoleService);

        semanticCacheService = new EnhancedSemanticCacheService();
    }

    private RetrieveResult createHit(Long id, Long docId, String docName, String content, double score) {
        RetrieveResult r = new RetrieveResult();
        r.setId(String.valueOf(id));
        r.setDocumentId(String.valueOf(docId));
        r.setDocumentName(docName);
        r.setContent(content);
        r.setScore(score);
        return r;
    }

    private KmcKnowledgeBaseRespDTO createKb(Long id, String name) {
        KmcKnowledgeBaseRespDTO kb = new KmcKnowledgeBaseRespDTO();
        kb.setId(id);
        kb.setName(name);
        return kb;
    }

    @Test
    @DisplayName("契约 1: 多库并发分发与单库 2500ms 软超时 Fail-Open 降级")
    void testMultiKbConcurrentRetrievalAndSoftTimeoutFallback() {
        // 设置较短超时进行快速单元测试验证
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator, "singleKbTimeoutMs", 300L);
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator, "globalTimeoutMs", 600L);

        KmcKnowledgeBaseRespDTO kb1 = createKb(1L, "研发规范库");
        KmcKnowledgeBaseRespDTO kb2 = createKb(2L, "故障慢查询库");
        KmcKnowledgeBaseRespDTO kb3 = createKb(3L, "安全规范库");
        KmcKnowledgeBaseRespDTO kb4 = createKb(4L, "异常网络库");

        // KB1: 快速成功 (50ms)
        when(mockKmcApi.recallTest(eq(1L), any(), any())).thenAnswer(inv -> {
            Thread.sleep(50);
            return List.of(createHit(101L, 10L, "Git规范.md", "Git分支管理流程", 0.88));
        });

        // KB2: 超时慢调用 (800ms > 300ms 软超时)
        when(mockKmcApi.recallTest(eq(2L), any(), any())).thenAnswer(inv -> {
            Thread.sleep(800);
            return List.of(createHit(201L, 20L, "慢SQL.md", "全表扫描优化", 0.90));
        });

        // KB3: 快速成功 (80ms)
        when(mockKmcApi.recallTest(eq(3L), any(), any())).thenAnswer(inv -> {
            Thread.sleep(80);
            return List.of(createHit(301L, 30L, "SSRF防护.md", "网络隔离", 0.85));
        });

        // KB4: 抛出异常
        when(mockKmcApi.recallTest(eq(4L), any(), any())).thenThrow(new RuntimeException("Remote DB Timeout"));

        long start = System.currentTimeMillis();
        List<MultiKbRetrievalCoordinator.SingleKbRecallResult> results =
                coordinator.coordinateRetrieval(List.of(kb1, kb2, kb3, kb4), "测试问题", Collections.emptyList());
        long elapsed = System.currentTimeMillis() - start;

        // 验证并发耗时：由于并发执行且 KB2 在 300ms 被软降级，整体耗时应在 600ms 以内（远小于串行等待 50+800+80=930ms）
        assertTrue(elapsed < 700, "并发编排总耗时应小于 700ms，实际: " + elapsed + "ms");
        assertEquals(4, results.size(), "应收集 4 个库的结果或降级对象");

        // 验证各库降级状态
        MultiKbRetrievalCoordinator.SingleKbRecallResult resKb1 = results.stream().filter(r -> r.getKnowledgeId().equals(1L)).findFirst().orElseThrow();
        assertTrue(resKb1.isSuccess());
        assertFalse(resKb1.isTimedOut());
        assertEquals(1, resKb1.getResults().size());

        MultiKbRetrievalCoordinator.SingleKbRecallResult resKb2 = results.stream().filter(r -> r.getKnowledgeId().equals(2L)).findFirst().orElseThrow();
        assertFalse(resKb2.isSuccess());
        assertTrue(resKb2.isTimedOut(), "KB2 必须被标记为 timedOut");
        assertTrue(resKb2.getResults().isEmpty(), "超时库结果应降级为空列表");

        MultiKbRetrievalCoordinator.SingleKbRecallResult resKb3 = results.stream().filter(r -> r.getKnowledgeId().equals(3L)).findFirst().orElseThrow();
        assertTrue(resKb3.isSuccess());
        assertEquals(1, resKb3.getResults().size());

        MultiKbRetrievalCoordinator.SingleKbRecallResult resKb4 = results.stream().filter(r -> r.getKnowledgeId().equals(4L)).findFirst().orElseThrow();
        assertFalse(resKb4.isSuccess());
        assertFalse(resKb4.isTimedOut());
        assertTrue(resKb4.getResults().isEmpty());
    }

    @Test
    @DisplayName("契约 2: 跨库置信度底线门禁 (0.40) 与 RRF (k=60) 融合精排去偏")
    void testCrossKbScoreFloorAndRrfRanking() {
        // 知识库 A（大库，噪点多，但部分分数高）
        MultiKbRetrievalCoordinator.SingleKbRecallResult kbA = MultiKbRetrievalCoordinator.SingleKbRecallResult.builder()
                .knowledgeId(10L)
                .knowledgeName("大容量知识库A")
                .success(true)
                .results(List.of(
                        createHit(1L, 101L, "DocA1.md", "高分正文A1", 0.95), // Rank 1
                        createHit(2L, 102L, "DocA2.md", "低分噪点A2", 0.35), // 低于 0.40 底线门禁
                        createHit(3L, 103L, "DocA3.md", "次高分正文A3", 0.85) // Rank 3
                ))
                .build();

        // 知识库 B（专业小库，切片精炼，Rank 1 优先）
        MultiKbRetrievalCoordinator.SingleKbRecallResult kbB = MultiKbRetrievalCoordinator.SingleKbRecallResult.builder()
                .knowledgeId(20L)
                .knowledgeName("专业小知识库B")
                .success(true)
                .results(List.of(
                        createHit(4L, 201L, "DocB1.md", "专业小库精炼正文B1", 0.88), // Rank 1
                        createHit(5L, 202L, "DocB2.md", "与A1重复的内容", 0.70)
                ))
                .build();

        CrossKbScoreCalibrator.MergedContextResult merged = calibrator.calibrateAndAssemble(List.of(kbA, kbB));

        // 1. 验证底线门禁：低于 0.40 的 DocA2 必须被彻底剔除
        boolean containsDocA2 = merged.getEmittedSegments().stream()
                .anyMatch(s -> "2".equals(s.getSegmentId()));
        assertFalse(containsDocA2, "低于 0.40 的噪点切片必须被底线门禁拦截过滤");

        // 2. 验证 RRF 融合精排：kbA 的 Rank 1 (DocA1) 与 kbB 的 Rank 1 (DocB1) 的 RRF 得分均应为 1/(60+1) = 0.01639
        CrossKbScoreCalibrator.CalibratedSegment top1 = merged.getEmittedSegments().get(0);
        CrossKbScoreCalibrator.CalibratedSegment top2 = merged.getEmittedSegments().get(1);
        assertEquals(1.0 / 61.0, top1.getRrfScore(), 1e-5);
        assertEquals(1.0 / 61.0, top2.getRrfScore(), 1e-5);

        // 3. 验证排在后面的 DocA3 (因 DocA2 被门禁过滤，其有效排名为 Rank 2) 的 RRF 得分为 1/(60+2) = 0.01613，严格小于 Rank 1
        CrossKbScoreCalibrator.CalibratedSegment segA3 = merged.getEmittedSegments().stream()
                .filter(s -> "3".equals(s.getSegmentId())).findFirst().orElseThrow();
        assertEquals(1.0 / 62.0, segA3.getRrfScore(), 1e-5);
        assertTrue(segA3.getRrfScore() < top1.getRrfScore());
    }

    @Test
    @DisplayName("契约 3: 全局 20KB (20000 字节) 统一硬预算截断与来源溯源格式")
    void testGlobal20KbBudgetTruncationAndCitationFormat() {
        // 构建超大文本段落（每个 1KB，共 30 个，总计 30KB > 20KB）
        List<RetrieveResult> largeHits = new ArrayList<>();
        String largeChunk = "QKnow企业级智能知识库平台核心架构深度解析。".repeat(25); // 约 600 字节
        for (long i = 1; i <= 30; i++) {
            largeHits.add(createHit(i, 100L + i, "架构文档_" + i + ".md", largeChunk, 0.80 - (i * 0.01)));
        }

        MultiKbRetrievalCoordinator.SingleKbRecallResult kbResult = MultiKbRetrievalCoordinator.SingleKbRecallResult.builder()
                .knowledgeId(99L)
                .knowledgeName("超大知识库")
                .success(true)
                .results(largeHits)
                .build();

        CrossKbScoreCalibrator.MergedContextResult merged = calibrator.calibrateAndAssemble(List.of(kbResult));

        // 1. 验证总字节数严格不超过 20,000 字节
        int totalBytes = merged.getTotalUsedBytes();
        byte[] actualUtf8Bytes = merged.getFormattedContext().getBytes(StandardCharsets.UTF_8);
        assertEquals(totalBytes, actualUtf8Bytes.length);
        assertTrue(totalBytes <= 20000, "全局装配字节数必须严格 <= 20000，实际: " + totalBytes);

        // 2. 验证部分切片被截断
        assertTrue(merged.getEmittedSegments().size() < 30, "应当只保留预算内的切片，实际保留: " + merged.getEmittedSegments().size());

        // 3. 验证溯源打标格式
        String context = merged.getFormattedContext();
        assertTrue(context.contains("[来源 1] [KB: 超大知识库] 架构文档_1.md / segmentId=1"));
        assertTrue(context.contains("内容："));
    }

    @Test
    @DisplayName("契约 4: 强类型 Fail-Closed 租户与知识库三元交集隔离 (绝不返回 null)")
    void testFailClosedMultiTenantIntersectionAndIsolation() {
        Long workspaceA = 1001L;
        Long workspaceB = 1002L;
        Long userId = 8888L;

        // Workspace A 下包含的知识库为 [101, 102]
        KmcKnowledgeBaseDO kb101 = new KmcKnowledgeBaseDO();
        kb101.setId(101L);
        kb101.setWorkspaceId(workspaceA);

        KmcKnowledgeBaseDO kb102 = new KmcKnowledgeBaseDO();
        kb102.setId(102L);
        kb102.setWorkspaceId(workspaceA);

        when(mockKnowledgeBaseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(kb101, kb102));

        // 用户拥有普通角色（角色ID=5），授权访问 [101, 201]，其中 201 属于其他租户 Workspace B
        SysRole userRole = new SysRole();
        userRole.setRoleId(5L);
        userRole.setRoleKey("normal_user");
        when(mockSysRoleService.selectRoleByUserId(userId)).thenReturn(List.of(userRole));

        KmcKnowledgeRoleDO roleKb101 = KmcKnowledgeRoleDO.builder().roleId(5L).knowledgeId(101L).validFlag(true).build();
        KmcKnowledgeRoleDO roleKb201 = KmcKnowledgeRoleDO.builder().roleId(5L).knowledgeId(201L).validFlag(true).build();
        when(mockKnowledgeRoleService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(roleKb101, roleKb201));

        // 请求传入 [101, 102, 201]
        List<Long> requested = List.of(101L, 102L, 201L);

        // 执行三元交集过滤: requested(101, 102, 201) ∩ workspaceA(101, 102) ∩ authorized(101, 201) = [101]
        List<Long> authorized = permissionFilter.filterAccessibleKbIds(workspaceA, userId, requested);
        assertNotNull(authorized, "Fail-Closed 保证：绝不能返回 null");
        assertEquals(List.of(101L), authorized, "跨租户 201 与未授权 102 必须被强行剔除，仅保留交集 101");

        // 验证空权限或无角色用户：必须返回空列表，绝不返回 null
        when(mockSysRoleService.selectRoleByUserId(9999L)).thenReturn(Collections.emptyList());
        List<Long> unauthorized = permissionFilter.filterAccessibleKbIds(workspaceA, 9999L, requested);
        assertNotNull(unauthorized);
        assertTrue(unauthorized.isEmpty(), "无角色用户必须返回空列表");

        // 验证 buildPermissionFilter 构建不可命中表达式
        Filter.Expression expr = permissionFilter.buildPermissionFilter(workspaceA, 9999L);
        assertNotNull(expr, "无权表达式绝不能为 null");
        assertEquals(Filter.ExpressionType.IN, expr.type());
    }

    @Test
    @DisplayName("契约 5: 语义缓存带租户与权限哈希防侧信道探测")
    void testSemanticCachePermissionHashIsolation() {
        Long workspaceId = 1L;
        Long botId = 10L;
        String model = "deepseek-chat";
        String query = "公司今年年终奖发放方案是什么？";
        String kbHash = semanticCacheService.hashKnowledgeBaseIds(List.of(101L, 102L));

        // 高权限用户（可访问全量库）
        String highPermHash = semanticCacheService.calculatePermissionHash(1001L, List.of(1L), List.of(101L, 102L));
        // 低权限用户（仅访问普通库）
        String lowPermHash = semanticCacheService.calculatePermissionHash(2002L, List.of(2L), List.of(101L));

        assertNotEquals(highPermHash, lowPermHash, "不同权限指纹必须具备强抗碰撞互异性");

        String highKey = semanticCacheService.buildExactKey(workspaceId, highPermHash, botId, kbHash, model, query);
        String lowKey = semanticCacheService.buildExactKey(workspaceId, lowPermHash, botId, kbHash, model, query);

        assertNotEquals(highKey, lowKey, "相同问题在不同权限下生成的缓存 Key 必须完全隔离");
        assertTrue(highKey.contains(highPermHash));
        assertTrue(lowKey.contains(lowPermHash));
    }
}
