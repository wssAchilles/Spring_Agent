package tech.qiantong.qknow.common.security.ssrf;

import okhttp3.*;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.TimeUnit;

/**
 * 工业级 SSRF 安全防护 HTTP 客户端构建器
 * 在传输层终结 DNS Rebinding 与 302 重定向安全逃逸
 */
public class SafeOkHttpClientBuilder {

    private static volatile OkHttpClient safeClientInstance;

    /**
     * 获取全局统一安全的 OkHttpClient 单例
     */
    public static OkHttpClient getSafeClient() {
        if (safeClientInstance == null) {
            synchronized (SafeOkHttpClientBuilder.class) {
                if (safeClientInstance == null) {
                    safeClientInstance = buildSafeClient();
                }
            }
        }
        return safeClientInstance;
    }

    /**
     * 构建具备完整 SSRF 防护能力的 OkHttpClient 实例
     */
    public static OkHttpClient buildSafeClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                // 1. 传输层 DNS 解析拦截：剔除所有私网与保留网段
                .dns(new SafeDns())
                // 2. 底层 SocketFactory 终态审计：消除 TOCTOU 竞争
                .socketFactory(new SafeSocketFactory())
                // 3. 网络层拦截器：协议白名单与对端 Socket IP 审计
                .addNetworkInterceptor(new SafeNetworkInterceptor())
                .followRedirects(true)
                .followSslRedirects(false)
                .build();
    }

    /**
     * 安全底层套接字工厂：在 Socket.connect 阶段核验物理目标 IP
     */
    public static class SafeSocketFactory extends SocketFactory {
        private final SocketFactory delegate = SocketFactory.getDefault();

        @Override
        public Socket createSocket() throws IOException {
            return delegate.createSocket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            InetAddress address = InetAddress.getByName(host);
            validateAddress(address);
            return delegate.createSocket(address, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            validateAddress(host);
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            InetAddress address = InetAddress.getByName(host);
            validateAddress(address);
            return delegate.createSocket(address, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            validateAddress(address);
            return delegate.createSocket(address, port, localAddress, localPort);
        }

        private void validateAddress(InetAddress address) throws ConnectException {
            if (SafeDns.isForbidden(address)) {
                throw new ConnectException("SSRF 传输层熔断：物理建连地址命中受限内网策略: " + address.getHostAddress());
            }
        }
    }

    /**
     * 网络拦截器：校验协议白名单与底层活跃物理连接
     */
    public static class SafeNetworkInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            HttpUrl url = request.url();

            // 1. 协议白名单限制（仅允许 http / https，严禁 file://, gopher:// 等）
            if (!"http".equalsIgnoreCase(url.scheme()) && !"https".equalsIgnoreCase(url.scheme())) {
                throw new SecurityException("SSRF 拦截：禁止非标准 HTTP 协议: " + url.scheme());
            }

            // 2. 校验底层连接实际对端物理 IP
            Connection connection = chain.connection();
            if (connection != null && connection.socket() != null) {
                InetAddress remoteAddress = connection.socket().getInetAddress();
                if (SafeDns.isForbidden(remoteAddress)) {
                    throw new ConnectException("SSRF 拦截：底层物理套接字 IP 命中私网拦截: " + (remoteAddress != null ? remoteAddress.getHostAddress() : "null"));
                }
            }

            return chain.proceed(request);
        }
    }
}
