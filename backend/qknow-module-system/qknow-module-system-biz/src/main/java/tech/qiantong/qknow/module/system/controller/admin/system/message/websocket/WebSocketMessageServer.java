package tech.qiantong.qknow.module.system.controller.admin.system.message.websocket;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.system.controller.admin.system.message.vo.MessagePageReqVO;
import tech.qiantong.qknow.websocket.WebSocketMessage;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.List;

/**
 * websocket 消息处理
 *
 * @author qknow
 */
@Component
@ServerEndpoint(value = "/websocket/message/{userId}", configurator = tech.qiantong.qknow.security.websocket.JwtServerEndpointConfigurator.class)
public class WebSocketMessageServer {
    // 日志记录
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketMessageServer.class);

    /**
     * 连接建立成功时触发的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) throws Exception {
        // 强制要求握手阶段必须通过 JWT 认证，严禁回退信任未认证的路径参数 userId 防止身份冒充
        String authenticatedUserId = (String) session.getUserProperties().get(tech.qiantong.qknow.security.websocket.JwtServerEndpointConfigurator.AUTHENTICATED_USER_ID);
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            LOGGER.warn("拦截未认证 WebSocket 连接尝试 - 声称 userId: {}, sessionId: {}", userId, session.getId());
            if (session.isOpen()) {
                session.close(new jakarta.websocket.CloseReason(
                        jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY, "Unauthorized WebSocket connection"));
            }
            return;
        }
        String finalUserId = authenticatedUserId;
        String key = finalUserId + '_' + session.getId();
        session.getUserProperties().put("SESSION_USER_ID", finalUserId);
        LOGGER.info("连接成功 - 用户ID: {} (Token认证通过)", key);
        WebSocketMessage.put(key, session);
    }

    /**
     * 连接关闭时触发的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        String finalUserId = (String) session.getUserProperties().get("SESSION_USER_ID");
        if (finalUserId == null || finalUserId.isBlank()) {
            return;
        }
        String key = finalUserId + '_' + session.getId();
        LOGGER.info("连接关闭 - 用户ID: {}", key);
        WebSocketMessage.remove(key);
    }

    /**
     * 异常时触发的方法
     */
    @OnError
    public void onError(Session session, Throwable exception, @PathParam("userId") String userId) throws Exception {
        String finalUserId = (String) session.getUserProperties().get("SESSION_USER_ID");
        if (session.isOpen()) {
            session.close();
        }
        LOGGER.error("连接异常 - 用户ID: {} - 异常信息: {}", finalUserId != null ? finalUserId : userId, exception.getMessage());
        // 按 userId_sessionId 组合键精确清理连接，防止会话表泄漏
        if (finalUserId != null && !finalUserId.isBlank()) {
            WebSocketMessage.remove(finalUserId + '_' + session.getId());
        }
    }

    /**
     * 向客户端发送消息
     * 一个客户可能会开启多个窗口,不同窗口session不一样, 存储key格式: userId_sessionId
     * @param userId
     * @param message
     */
    public static void sendMessageToUser(String userId, String message) {
        // 获取用户的 session
        List<Session> sessionList = WebSocketMessage.getUserSessionList(userId);
        if (sessionList.size() > 0) {
            for (Session session : sessionList) {
                // 通过 WebSocketMessage 发送消息
                WebSocketMessage.sendMessageToUserByText(session, message);
            }
        }
    }

    /**
     * 向客户端发送消息
     * @param userId 用户ID
     * @param message 消息内容
     */
    public static void sendMessage(String userId, String message) {
        // 获取用户的 session
        Session session = WebSocketMessage.get(userId);
        if (session != null) {
            // 通过 WebSocketMessage 发送消息
            WebSocketMessage.sendMessageToUserByText(session, message);
        }
    }

    /**
     * 向所有连接的客户端广播消息
     * @param message 消息内容
     */
    public static void broadcastMessage(MessagePageReqVO message) {
        String string = JSONObject.toJSONString(message);
        WebSocketMessage.broadcast(string);
    }

}
