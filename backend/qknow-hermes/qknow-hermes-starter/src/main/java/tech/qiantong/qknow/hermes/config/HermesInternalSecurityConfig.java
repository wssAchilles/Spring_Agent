package tech.qiantong.qknow.hermes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/**
 * Hermes 独立微服务启动器专用内网安全策略。
 * <p>
 * 仅作用于独立运行的 qknow-hermes-starter（8081 端口），绝不影响主控制面 qknow-server 的 JWT 鉴权链路：
 * 仅允许本机回环来源访问联邦遥测、时间旅行与健康检查端点，其余外部路径保持认证拦截。
 * </p>
 */
@Configuration
public class HermesInternalSecurityConfig {

    private static final String LOOPBACK_EXPR =
            "hasIpAddress('127.0.0.1') or hasIpAddress('::1') or hasIpAddress('0:0:0:0:0:0:0:1')";

    @Bean
    public SecurityFilterChain hermesInternalSecurityFilterChain(HttpSecurity http) throws Exception {
        WebExpressionAuthorizationManager loopbackOnly = new WebExpressionAuthorizationManager(LOOPBACK_EXPR);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/hermes/phd-studio/**", "/actuator/health", "/actuator/health/**")
                        .access(loopbackOnly)
                        .anyRequest().authenticated()
                )
                .build();
    }
}
