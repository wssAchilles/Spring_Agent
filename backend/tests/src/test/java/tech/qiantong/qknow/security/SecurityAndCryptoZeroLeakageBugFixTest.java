package tech.qiantong.qknow.security;

import jakarta.websocket.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.constant.Constants;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.search.ColbertScoreReceipt;
import tech.qiantong.qknow.module.kb.controller.admin.KbInternalApiController;
import tech.qiantong.qknow.module.system.controller.admin.system.message.websocket.WebSocketMessageServer;
import tech.qiantong.qknow.neo4j.domain.DynamicEntity;
import tech.qiantong.qknow.neo4j.wrapper.Neo4jBuildWrapper;
import tech.qiantong.qknow.neo4j.wrapper.Neo4jQueryWrapper;
import tech.qiantong.qknow.security.web.service.TokenService;
import tech.qiantong.qknow.websocket.WebSocketMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 战役五（5.1 ~ 5.2）：高危安全漏洞清除与全库 MessageDigest.isEqual 常量时间验签合规测试
 */
class SecurityAndCryptoZeroLeakageBugFixTest {

    @Test
    @DisplayName("5.1a WebSocketMessageServer: 未携带有效 JWT 认证身份时拒绝连接并关闭 Session，onError 精确移除组合键")
    void webSocketMessageServer_rejectsUnauthenticatedSessionAndCleansCompositeKeyOnError() throws Exception {
        WebSocketMessageServer wsServer = new WebSocketMessageServer();
        Session unauthSession = mock(Session.class);
        Map<String, Object> props = new HashMap<>();
        when(unauthSession.getUserProperties()).thenReturn(props);
        when(unauthSession.getId()).thenReturn("sess-unauth");
        when(unauthSession.isOpen()).thenReturn(true);

        wsServer.onOpen(unauthSession, "admin_victim");
        assertNull(WebSocketMessage.get("admin_victim_sess-unauth"),
                "未通过 JWT 认证的连接严禁回退信任路径参数 userId 写入会话表");
        verify(unauthSession, atLeastOnce()).close(any(jakarta.websocket.CloseReason.class));

        // 已认证会话在 onError 时必须按 userId_sessionId 组合键清理
        Session authSession = mock(Session.class);
        Map<String, Object> authProps = new HashMap<>();
        authProps.put(tech.qiantong.qknow.security.websocket.JwtServerEndpointConfigurator.AUTHENTICATED_USER_ID, "u100");
        when(authSession.getUserProperties()).thenReturn(authProps);
        when(authSession.getId()).thenReturn("s99");
        when(authSession.isOpen()).thenReturn(true);

        wsServer.onOpen(authSession, "spoofed_id");
        assertNotNull(WebSocketMessage.get("u100_s99"));
        wsServer.onError(authSession, new RuntimeException("io_err"), "spoofed_id");
        assertNull(WebSocketMessage.get("u100_s99"), "onError 必须按组合键清理会话防止泄漏");
    }

    @Test
    @DisplayName("5.1b KbInternalApiController: 强制校验 x-hermes-auth-token 内部共享令牌（未配置时仅放行回环地址）")
    void kbInternalApiController_enforcesSharedTokenOrLoopbackIsolation() {
        KbInternalApiController controller = new KbInternalApiController();
        // 配置共享密钥后，外部无头或错误令牌请求必须拒绝
        Arrays.stream(KbInternalApiController.class.getDeclaredFields())
                .filter(f -> "hermesAuthToken".equals(f.getName()))
                .findFirst()
                .ifPresent(f -> ReflectionTestUtils.setField(controller, "hermesAuthToken", "top-secret-hermes-token"));

        MockHttpServletRequest unauthorizedReq = new MockHttpServletRequest();
        unauthorizedReq.setRemoteAddr("10.20.30.40");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(unauthorizedReq));
        try {
            CommonResult<String> res = controller.recall(Map.of("knowledgeId", 1L, "query", "test"));
            assertEquals(401, res.getCode(), "未携带有效 x-hermes-auth-token 的非回环请求必须返回 401 拒绝");
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("5.1c-d Neo4j 包装器转义/语法修复、定时任务真实黑名单包名与 TokenService 默认开发密钥拦截")
    void neo4jWrapperJobBlacklistAndTokenSecret_preventInjectionAndDefaultSecretBypass() {
        // 1. Neo4jBuildWrapper 字符串单引号转义 & 标签注入拦截
        Neo4jBuildWrapper<DynamicEntity> buildWrapper = new Neo4jBuildWrapper<>(DynamicEntity.class);
        String cypher = buildWrapper.mergeCreateNode("DynamicEntity", Map.of("name", "O'Reilly"), Map.of("note", "a'b\\c"));
        assertTrue(cypher.contains("O\\'Reilly") && cypher.contains("a\\'b\\\\c"),
                "Neo4jBuildWrapper 必须转义字符串中的单引号与反斜杠防止 Cypher 注入，实际=" + cypher);
        assertThrows(IllegalArgumentException.class,
                () -> buildWrapper.mergeCreateNode("Node) DETACH DELETE n //", Map.of(), Map.of()),
                "Neo4jBuildWrapper 必须拦截非法动态标签注入");

        // 2. Neo4jQueryWrapper between 右边界不得漏写属性名
        Neo4jQueryWrapper<DynamicEntity> queryWrapper = new Neo4jQueryWrapper<>(DynamicEntity.class);
        queryWrapper.between("age", 18, 60);
        String betweenCypher = queryWrapper.getCypherQuery();
        assertTrue(betweenCypher.contains("age <= $ageEnd"),
                "Neo4jQueryWrapper.between 右边界必须包含属性名 age，实际=" + betweenCypher);

        // 3. Constants.JOB_ERROR_STR 必须覆盖项目真实包前缀 tech.qiantong.qknow.*
        List<String> jobBlacklist = Arrays.asList(Constants.JOB_ERROR_STR);
        assertTrue(jobBlacklist.contains("tech.qiantong.qknow.common.utils.file"),
                "定时任务黑名单必须包含真实包路径 tech.qiantong.qknow.common.utils.file");
        assertTrue(jobBlacklist.contains("tech.qiantong.qknow.generator"),
                "定时任务黑名单必须包含真实包路径 tech.qiantong.qknow.generator");

        // 4. TokenService 在生产 profile 下必须拦截 qknow-local-dev-jwt-secret-32bytes-2026
        TokenService tokenService = new TokenService();
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        ReflectionTestUtils.setField(tokenService, "environment", prodEnv);
        ReflectionTestUtils.setField(tokenService, "secret", "qknow-local-dev-jwt-secret-32bytes-2026");
        assertThrows(IllegalStateException.class, tokenService::validateTokenSecret,
                "生产环境必须拒绝公开开发默认密钥 qknow-local-dev-jwt-secret-32bytes-2026");
    }

    @Test
    @DisplayName("5.2 ColbertScoreReceipt 载荷防篡改、StateCheckpointRecord 状态防篡改与全库常量时间比对清零")
    void receiptsAndAuth_coverFullPayloadAndUseConstantTimeMessageDigestIsEqual() throws Exception {
        // 1. ColbertScoreReceipt 签名必须覆盖每个分数的数值而非仅数组长度
        ColbertScoreReceipt r1 = ColbertScoreReceipt.create(4, 2, 128, "JNI", 1000L, new float[]{0.15f, 0.85f});
        assertTrue(r1.verifySignature());
        ColbertScoreReceipt tamperedColbert = new ColbertScoreReceipt(
                r1.queryLength(), r1.numDocs(), r1.dimension(), r1.executionMode(),
                r1.executionDurationNanos(), List.of(0.99f, 0.99f), r1.signature()
        );
        assertFalse(tamperedColbert.verifySignature(),
                "篡改 ColbertScoreReceipt 分数内容后 verifySignature() 必须返回 false");

        // 2. StateCheckpointRecord 签名必须覆盖 fullStateSnapshot 与 pendingNodes
        StateCheckpointRecord ckpt = StateCheckpointRecord.create(
                "ckpt-1", "exec-1", "main", 3, Map.of("balance", 100), List.of("nodeA"), 1L
        );
        assertTrue(ckpt.verifyChecksum());
        StateCheckpointRecord tamperedCkpt = new StateCheckpointRecord(
                ckpt.checkpointId(), ckpt.executionId(), ckpt.branchId(), ckpt.superstep(),
                Map.of("balance", 999999), List.of("nodeHacked"), ckpt.fencingToken(),
                ckpt.createdAtEpochMs(), ckpt.checksumSha256()
        );
        assertFalse(tamperedCkpt.verifyChecksum(),
                "篡改 StateCheckpointRecord 快照状态或待执行节点后 verifyChecksum() 必须返回 false");

        // 3. 静态审计指定 12 个密码学凭单/鉴权类中不得残留 equalsIgnoreCase 短路签名比对
        Path repoBackend = Path.of("").toAbsolutePath();
        if (repoBackend.endsWith("tests")) {
            repoBackend = repoBackend.getParent();
        }
        List<String> targetRelativeFiles = List.of(
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcAuthInterceptor.java",
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/debate/dto/MultiAgentArbitrationReceipt.java",
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/consensus/dto/ZeroTrustDecisionVoucher.java",
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/receipt/MemoryConsolidationReceipt.java",
                "qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/model/McpExecutionReceipt.java",
                "qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core/gateway/dto/McpExecutionReceipt.java",
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/hitl/engine/WorkflowHitlReactiveGovernor.java",
                "qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/audit/merkle/MerkleTreeEngine.java",
                "qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/feedback/FeedbackCollectorController.java",
                "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/advanced/dto/GraphRagExecutionReceipt.java",
                "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/evolution/dto/KnowledgeEvolutionReceipt.java",
                "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/storage/tiered/ColdStorageArchiver.java"
        );
        for (String rel : targetRelativeFiles) {
            String src = Files.readString(repoBackend.resolve(rel));
            assertTrue(src.contains("MessageDigest.isEqual"),
                    "文件必须使用 MessageDigest.isEqual 常量时间比对: " + rel);
            assertFalse(src.contains("equalsIgnoreCase("),
                    "文件严禁残留 equalsIgnoreCase 短路比对暴露时序侧信道: " + rel);
        }
    }
}
