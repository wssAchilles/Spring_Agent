package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.hermes.grpc.HermesCredentialResolver;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.mybatis.core.type.EncryptTypeHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-04 回归防护：控制面只下发 keyId，Hermes 本地解密还原 API Key；
 * 凭据解析失败必须 fail-close，禁止空密钥静默调用模型。
 */
class GrpcCredentialResolutionTest {

    private static final String PASSWORD = "unit-test-secret-32bytes-2026!!!";

    @Test
    @DisplayName("keyId 凭据解析：加密存储的 Key 在 Hermes 侧正确解密并回填请求")
    void enrichMustResolveEncryptedApiKey() {
        String encrypted = EncryptTypeHandler.encrypt("sk-unit-test-key");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(42L))).thenReturn(encrypted);

        HermesCredentialResolver resolver = new HermesCredentialResolver();
        ReflectionTestUtils.setField(resolver, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(resolver, "encryptorPassword", PASSWORD);
        resolver.init();

        ChatRequest request = ChatRequest.newBuilder()
                .setRequestId("req-1")
                .setQuestion("hello")
                .setModelConfig(ModelConfig.newBuilder().setKeyId(42L).setApiKey("").build())
                .build();

        ChatRequest enriched = resolver.enrich(request);
        assertEquals("sk-unit-test-key", enriched.getModelConfig().getApiKey(),
                "Hermes 必须本地解密并回填 API Key，控制面不再下发明文密钥");
    }

    @Test
    @DisplayName("凭据缺失 fail-close：DB 无记录时禁止以空密钥继续调用")
    void enrichMustFailClosedWhenCredentialMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(99L)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        HermesCredentialResolver resolver = new HermesCredentialResolver();
        ReflectionTestUtils.setField(resolver, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(resolver, "encryptorPassword", PASSWORD);
        resolver.init();

        ChatRequest request = ChatRequest.newBuilder()
                .setRequestId("req-2")
                .setQuestion("hello")
                .setModelConfig(ModelConfig.newBuilder().setKeyId(99L).setApiKey("").build())
                .build();

        assertThrows(IllegalStateException.class, () -> resolver.enrich(request),
                "凭据解析失败必须 fail-close，不能以空 API Key 静默调用外部模型");
    }

    @Test
    @DisplayName("兼容路径：请求已带 apiKey 时不再访问数据库")
    void enrichMustKeepExplicitApiKey() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        HermesCredentialResolver resolver = new HermesCredentialResolver();
        ReflectionTestUtils.setField(resolver, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(resolver, "encryptorPassword", PASSWORD);
        resolver.init();

        ChatRequest request = ChatRequest.newBuilder()
                .setRequestId("req-3")
                .setModelConfig(ModelConfig.newBuilder().setKeyId(42L).setApiKey("legacy-key").build())
                .build();

        assertSame(request, resolver.enrich(request));
        assertEquals("legacy-key", resolver.enrich(request).getModelConfig().getApiKey());
    }
}
