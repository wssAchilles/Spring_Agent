package tech.qiantong.qknow.hermes.grpc;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.net.SocketAddress;

/**
 * Hermes gRPC 核心服务鉴权拦截器 (P0-04)
 * 具备双重防线：
 * 1. 内部共享通信 Token 校验 (x-hermes-auth-token)
 * 2. 容器内部网络/回环安全白名单校验
 */
@Slf4j
@GrpcGlobalServerInterceptor
public class HermesGrpcAuthInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> AUTH_HEADER_KEY =
            Metadata.Key.of("x-hermes-auth-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${hermes.grpc.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${hermes.grpc.auth.token:${HERMES_GRPC_AUTH_TOKEN:}}")
    private String expectedToken;

    @Value("${hermes.grpc.auth.allow-loopback:true}")
    private boolean allowLoopback;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if (!authEnabled) {
            return next.startCall(call, headers);
        }

        // 1. 优先校验鉴权 Token（未配置 token 时视为 fail-close，仅回环可用）
        String clientToken = headers.get(AUTH_HEADER_KEY);
        if (expectedToken != null && !expectedToken.isBlank() && expectedToken.equals(clientToken)) {
            return next.startCall(call, headers);
        }
        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("Hermes gRPC 鉴权密钥未配置 (HERMES_GRPC_AUTH_TOKEN)，拒绝非回环访问");
        }

        // 2. 校验来源地址是否为本地回环 (127.0.0.1 / ::1 / localhost)
        SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (allowLoopback && remoteAddr != null) {
            String remoteStr = remoteAddr.toString();
            if (remoteStr.contains("127.0.0.1") || remoteStr.contains("/0:0:0:0:0:0:0:1") || remoteStr.contains("localhost")) {
                return next.startCall(call, headers);
            }
        }

        log.warn("Hermes gRPC 拦截未授权访问: remoteAddress={}, method={}",
                remoteAddr, call.getMethodDescriptor().getFullMethodName());

        call.close(Status.UNAUTHENTICATED.withDescription("Hermes gRPC: Unauthorized access - missing or invalid auth token"), new Metadata());
        return new ServerCall.Listener<ReqT>() {};
    }
}
