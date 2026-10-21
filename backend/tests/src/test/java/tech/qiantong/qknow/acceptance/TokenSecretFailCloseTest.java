package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.security.web.service.TokenService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R7/P0-01 回归防护：token.secret 不得使用公开默认值运行生产；
 * 未配置或生产使用公开默认密钥必须启动失败。
 */
class TokenSecretFailCloseTest {

    private static final String PUBLIC_DEFAULT = "qknow-jwt-secret-key-32bytes-secure-2026";

    @Test
    @DisplayName("未配置 token.secret 启动失败")
    void blankSecretFailsFast() {
        TokenService service = newService("", "prod");
        assertThrows(IllegalStateException.class, service::validateTokenSecret);
    }

    @Test
    @DisplayName("生产 profile 使用公开默认密钥启动失败")
    void publicDefaultRejectedInProd() {
        TokenService service = newService(PUBLIC_DEFAULT, "prod");
        assertThrows(IllegalStateException.class, service::validateTokenSecret);
    }

    @Test
    @DisplayName("dev profile 允许公开默认密钥并告警")
    void publicDefaultAllowedInDevWithWarning() {
        TokenService service = newService(PUBLIC_DEFAULT, "dev");
        assertDoesNotThrow(service::validateTokenSecret);
    }

    @Test
    @DisplayName("生产使用独立高熵密钥正常启动")
    void strongSecretAllowedInProd() {
        TokenService service = newService("unit-test-independent-secret-32bytes-2026", "prod");
        assertDoesNotThrow(service::validateTokenSecret);
    }

    private TokenService newService(String secret, String activeProfile) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", secret);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});
        ReflectionTestUtils.setField(service, "environment", environment);
        return service;
    }
}
