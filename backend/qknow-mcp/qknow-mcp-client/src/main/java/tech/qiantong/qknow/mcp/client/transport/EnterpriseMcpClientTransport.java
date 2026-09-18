package tech.qiantong.qknow.mcp.client.transport;

import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.core.protocol.McpMessage;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 企业级 MCP 客户端传输通道契约 (Enterprise MCP Client Transport)
 * <p>
 * 满足 Phase 103 标准，支持滑动窗口信用背压 (Credit Backpressure)、双虚拟线程排空与心跳健康监控。
 */
public interface EnterpriseMcpClientTransport extends Closeable {

    /**
     * 获取传输通道唯一标识
     */
    String getTransportId();

    /**
     * 发送 JSON-RPC 2.0 请求并异步等待响应（受滑动信用窗口背压限制）
     *
     * @param method 方法名 (如 "tools/call", "initialize", "ping")
     * @param params 请求参数
     * @return 响应 Future
     */
    CompletableFuture<JsonRpcResponse> sendRequest(String method, Map<String, Object> params);

    /**
     * 发送单向通知 (Notification)
     *
     * @param method 通知方法名 (如 "notifications/initialized")
     * @param params 通知参数
     * @return 发送完成 Future
     */
    CompletableFuture<Void> sendNotification(String method, Map<String, Object> params);

    /**
     * 设置全双工下行消息监听器
     *
     * @param listener 消息处理器
     */
    void setMessageListener(Consumer<McpMessage> listener);

    /**
     * 传输通道是否处于健康连通状态
     */
    boolean isConnected();

    /**
     * 获取当前飞行中 (In-flight) 的请求数量
     */
    int getInFlightCount();

    /**
     * 获取滑动信用窗口最大容量 W_max
     */
    int getMaxConcurrency();

    /**
     * 主动发送 Ping 心跳探活
     *
     * @return 探活结果 Future (true 为存活)
     */
    CompletableFuture<Boolean> ping();

    /**
     * 关闭传输通道并彻底回收所有进程树与管道句柄
     */
    @Override
    void close();
}
