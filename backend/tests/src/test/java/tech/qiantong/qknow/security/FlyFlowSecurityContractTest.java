package tech.qiantong.qknow.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tech.qiantong.qknow.security.filter.FlyFlowHmacAuthenticationFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlyFlow 内部接口 HMAC 签名防篡改与防重放契约测试
 */
public class FlyFlowSecurityContractTest {

    private final String testAppId = "flyflow-internal-test";
    private final String testSecretKey = "secret-key-1234567890abcdef";

    private String calculateHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("契约 1：匿名未携带签名标头的请求直接返回 401")
    void testAnonymousAccessBlocked() throws Exception {
        FlyFlowHmacAuthenticationFilter filter = new FlyFlowHmacAuthenticationFilter(testAppId, testSecretKey, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/flyflow/userById");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("契约 2：携带合规签名、时间戳和 Nonce 的请求成功放行")
    void testValidHmacAccessGranted() throws Exception {
        FlyFlowHmacAuthenticationFilter filter = new FlyFlowHmacAuthenticationFilter(testAppId, testSecretKey, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/flyflow/userById");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        long now = System.currentTimeMillis() / 1000;
        String nonce = "nonce-valid-001";
        String payload = "GET\n/flyflow/userById\n" + now + "\n" + nonce + "\n";
        String signature = calculateHmac(payload, testSecretKey);

        request.addHeader("X-Flyflow-App-Id", testAppId);
        request.addHeader("X-Flyflow-Timestamp", String.valueOf(now));
        request.addHeader("X-Flyflow-Nonce", nonce);
        request.addHeader("X-Flyflow-Signature", signature);

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("FLYFLOW_INTERNAL_SERVICE", SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        // 清理安全上下文
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("契约 3：时间戳过期（如超过 5 分钟）被阻断")
    void testExpiredTimestampBlocked() throws Exception {
        FlyFlowHmacAuthenticationFilter filter = new FlyFlowHmacAuthenticationFilter(testAppId, testSecretKey, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/flyflow/userById");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        long expiredTime = (System.currentTimeMillis() / 1000) - 400; // 400s 前，超过 300s
        String nonce = "nonce-expired-002";
        String payload = "GET\n/flyflow/userById\n" + expiredTime + "\n" + nonce + "\n";
        String signature = calculateHmac(payload, testSecretKey);

        request.addHeader("X-Flyflow-App-Id", testAppId);
        request.addHeader("X-Flyflow-Timestamp", String.valueOf(expiredTime));
        request.addHeader("X-Flyflow-Nonce", nonce);
        request.addHeader("X-Flyflow-Signature", signature);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("过期") || response.getContentAsString().contains("401"));
    }

    @Test
    @DisplayName("契约 4：篡改请求参数导致签名不匹配被阻断")
    void testTamperedSignatureBlocked() throws Exception {
        FlyFlowHmacAuthenticationFilter filter = new FlyFlowHmacAuthenticationFilter(testAppId, testSecretKey, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/flyflow/userById");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        long now = System.currentTimeMillis() / 1000;
        String nonce = "nonce-tampered-003";

        request.addHeader("X-Flyflow-App-Id", testAppId);
        request.addHeader("X-Flyflow-Timestamp", String.valueOf(now));
        request.addHeader("X-Flyflow-Nonce", nonce);
        request.addHeader("X-Flyflow-Signature", "tampered_fake_signature_hex");

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("签名校验未通过") || response.getContentAsString().contains("401"));
    }
}
