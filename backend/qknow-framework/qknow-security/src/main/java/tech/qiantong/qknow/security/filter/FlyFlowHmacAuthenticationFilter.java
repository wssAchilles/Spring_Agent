package tech.qiantong.qknow.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;

/**
 * 针对 /flyflow/** 内部接口的 HMAC 签名防篡改与防重放鉴权过滤器
 * 消除接口匿名访问安全漏洞
 */
@Slf4j
public class FlyFlowHmacAuthenticationFilter extends OncePerRequestFilter {

    private final String expectedAppId;
    private final String secretKey;
    private final StringRedisTemplate redisTemplate;

    public FlyFlowHmacAuthenticationFilter(String expectedAppId, String secretKey, StringRedisTemplate redisTemplate) {
        this.expectedAppId = (expectedAppId != null && !expectedAppId.isBlank()) ? expectedAppId : "flyflow-internal";
        this.secretKey = (secretKey != null && !secretKey.isBlank()) ? secretKey : "qknow-flyflow-secret-key-2026";
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/flyflow/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 使用 ContentCachingRequestWrapper 支持重复读取 Body
        ContentCachingRequestWrapper wrappedRequest = (request instanceof ContentCachingRequestWrapper)
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);

        String appId = wrappedRequest.getHeader("X-Flyflow-App-Id");
        String timestampStr = wrappedRequest.getHeader("X-Flyflow-Timestamp");
        String nonce = wrappedRequest.getHeader("X-Flyflow-Nonce");
        String signature = wrappedRequest.getHeader("X-Flyflow-Signature");

        // 1. 签名标头完备性校验
        if (appId == null || timestampStr == null || nonce == null || signature == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：缺少必需签名标头");
            return;
        }

        if (!expectedAppId.equals(appId)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：无效的 AppId");
            return;
        }

        // 2. 时间戳防时钟漂移与防过期校验 (5 分钟窗口)
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：时间戳格式非法");
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - timestamp) > 300) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：请求时间戳已过期");
            return;
        }

        // 3. 基于 Redis 的 Nonce 防重放校验
        if (redisTemplate != null) {
            String nonceKey = "flyflow:nonce:" + nonce;
            Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1", Duration.ofMinutes(5));
            if (Boolean.FALSE.equals(isFirst)) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：检测到重放请求");
                return;
            }
        }

        // 4. 计算 HMAC-SHA256 并进行恒定时间比对
        String body = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
        String payloadToSign = request.getMethod() + "\n" + path + "\n" + timestampStr + "\n" + nonce + "\n" + body;
        String calculatedSignature = calculateHmacSha256(payloadToSign, secretKey);

        if (!MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "HMAC 鉴权失败：签名校验未通过");
            return;
        }

        // 5. 鉴权通过：注入系统内部服务身份
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "FLYFLOW_INTERNAL_SERVICE", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SYSTEM")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(wrappedRequest, response);
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名计算异常", e);
        }
    }

    private void sendError(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\"}");
    }
}
