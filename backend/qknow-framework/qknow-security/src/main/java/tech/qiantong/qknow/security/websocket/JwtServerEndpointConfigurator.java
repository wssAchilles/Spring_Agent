package tech.qiantong.qknow.security.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;
import tech.qiantong.qknow.security.web.service.TokenService;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手配置器：基于 JWT Token 校验并提取可信用户身份
 * 彻底消除通过 URL 路径参数假冒用户身份的严重越权漏洞
 */
@Slf4j
@Component
public class JwtServerEndpointConfigurator extends ServerEndpointConfig.Configurator implements ApplicationContextAware {

    private static ApplicationContext context;

    public static final String AUTHENTICATED_USER_ID = "AUTHENTICATED_USER_ID";
    public static final String AUTHENTICATED_USER = "AUTHENTICATED_USER";

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        if (context == null) {
            return;
        }
        try {
            TokenService tokenService = context.getBean(TokenService.class);
            if (tokenService == null) {
                return;
            }

            // 1. 从 Query String 提取 Token (?token=xxx)
            String query = request.getRequestURI() != null ? request.getRequestURI().getQuery() : null;
            String token = extractTokenFromQuery(query);

            // 2. 若 Query 无 Token，尝试从 Header Authorization 提取
            if (!StringUtils.hasText(token)) {
                Map<String, List<String>> headers = request.getHeaders();
                if (headers != null && headers.containsKey("authorization")) {
                    List<String> authHeaders = headers.get("authorization");
                    if (authHeaders != null && !authHeaders.isEmpty()) {
                        String authHeader = authHeaders.get(0);
                        if (authHeader.startsWith("Bearer ")) {
                            token = authHeader.substring(7);
                        }
                    }
                }
            }

            if (StringUtils.hasText(token)) {
                LoginUser loginUser = tokenService.getLoginUser(token);
                if (loginUser != null && loginUser.getUserId() != null) {
                    sec.getUserProperties().put(AUTHENTICATED_USER_ID, String.valueOf(loginUser.getUserId()));
                    sec.getUserProperties().put(AUTHENTICATED_USER, loginUser);
                    log.info("WebSocket 握手鉴权成功: userId={}", loginUser.getUserId());
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 握手鉴权异常: {}", e.getMessage());
        }
    }

    private String extractTokenFromQuery(String query) {
        if (!StringUtils.hasText(query)) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
