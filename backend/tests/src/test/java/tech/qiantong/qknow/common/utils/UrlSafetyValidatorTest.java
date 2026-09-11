package tech.qiantong.qknow.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlSafetyValidatorTest {

    @Test
    @DisplayName("拒绝 http(s) 以外 scheme")
    void rejectNonHttpScheme() {
        assertNotNull(UrlSafetyValidator.check("file:///etc/passwd"));
        assertNotNull(UrlSafetyValidator.check("gopher://x"));
        assertNotNull(UrlSafetyValidator.check(null));
    }

    @Test
    @DisplayName("拒绝 loopback / localhost / 127.0.0.1")
    void rejectLoopback() {
        assertNotNull(UrlSafetyValidator.check("http://127.0.0.1/admin"));
        assertNotNull(UrlSafetyValidator.check("http://localhost:8080/"));
        assertNotNull(UrlSafetyValidator.check("http://0.0.0.0/"));
    }

    @Test
    @DisplayName("拒绝私网与链路本地/元数据")
    void rejectPrivateAndMetadata() {
        assertNotNull(UrlSafetyValidator.check("http://10.0.0.5/x"));
        assertNotNull(UrlSafetyValidator.check("http://192.168.1.1/"));
        assertNotNull(UrlSafetyValidator.check("http://172.16.0.1/"));
        assertNotNull(UrlSafetyValidator.check("http://169.254.169.254/latest/meta-data/"));
        assertNotNull(UrlSafetyValidator.check("http://[::1]/"));
    }

    @Test
    @DisplayName("放行普通公网 URL")
    void allowPublic() {
        assertNull(UrlSafetyValidator.check("https://example.com/path"));
        assertNull(UrlSafetyValidator.check("http://api.deepseek.com/v1"));
    }

    @Test
    @DisplayName("validateOrThrow 对非法 URL 抛错")
    void validateOrThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validateOrThrow("http://127.0.0.1:8099"));
    }
}
