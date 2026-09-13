package tech.qiantong.qknow.security;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.common.security.ssrf.SafeDns;
import tech.qiantong.qknow.common.security.ssrf.SafeOkHttpClientBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSRF 纵深防御契约测试
 * 验证基于传输层原子 IP 绑定的防穿透与防 DNS Rebinding 机制
 */
public class SsrfDefenseContractTest {

    @Test
    @DisplayName("契约 1：严格阻断 127.0.0.1 回环地址与 localhost")
    void testBlockLocalhostAndLoopback() {
        SafeDns safeDns = new SafeDns(hostname -> List.of(InetAddress.getByName("127.0.0.1")));
        assertThrows(UnknownHostException.class, () -> safeDns.lookup("localhost"));

        SafeDns safeDnsOtherLoopback = new SafeDns(hostname -> List.of(InetAddress.getByName("127.1.2.3")));
        assertThrows(UnknownHostException.class, () -> safeDnsOtherLoopback.lookup("evil.com"));
    }

    @Test
    @DisplayName("契约 2：严格阻断 169.254.169.254 云厂商元数据服务端点")
    void testBlockCloudMetadataService() {
        SafeDns safeDns = new SafeDns(hostname -> List.of(InetAddress.getByName("169.254.169.254")));
        UnknownHostException ex = assertThrows(UnknownHostException.class, () -> safeDns.lookup("metadata.internal"));
        assertTrue(ex.getMessage().contains("SSRF") || ex.getMessage().contains("forbidden") || ex.getMessage().contains("blocked"));
    }

    @Test
    @DisplayName("契约 3：严格阻断 RFC1918 私网网段与运营商 CGNAT 网段")
    void testBlockPrivateSubnets() {
        // 10.0.0.0/8
        SafeDns dns10 = new SafeDns(hostname -> List.of(InetAddress.getByName("10.10.10.10")));
        assertThrows(UnknownHostException.class, () -> dns10.lookup("lan.local"));

        // 172.16.0.0/12
        SafeDns dns172 = new SafeDns(hostname -> List.of(InetAddress.getByName("172.20.1.5")));
        assertThrows(UnknownHostException.class, () -> dns172.lookup("corp.internal"));

        // 192.168.0.0/16
        SafeDns dns192 = new SafeDns(hostname -> List.of(InetAddress.getByName("192.168.1.1")));
        assertThrows(UnknownHostException.class, () -> dns192.lookup("router.home"));

        // 100.64.0.0/10 (CGNAT)
        SafeDns dnsCgnat = new SafeDns(hostname -> List.of(InetAddress.getByName("100.64.0.1")));
        assertThrows(UnknownHostException.class, () -> dnsCgnat.lookup("cgnat.test"));

        // 0.0.0.0
        SafeDns dnsZero = new SafeDns(hostname -> List.of(InetAddress.getByName("0.0.0.0")));
        assertThrows(UnknownHostException.class, () -> dnsZero.lookup("zero.test"));
    }

    @Test
    @DisplayName("契约 4：严格阻断 IPv6 回环与私网地址")
    void testBlockIpv6PrivateAndLoopback() {
        // ::1 回环
        SafeDns dnsIpv6Loopback = new SafeDns(hostname -> List.of(InetAddress.getByName("::1")));
        assertThrows(UnknownHostException.class, () -> dnsIpv6Loopback.lookup("ipv6.localhost"));

        // fc00::/7 唯一本地地址 (ULA)
        SafeDns dnsIpv6Ula = new SafeDns(hostname -> List.of(InetAddress.getByName("fc00::1")));
        assertThrows(UnknownHostException.class, () -> dnsIpv6Ula.lookup("ula.internal"));
    }

    @Test
    @DisplayName("契约 5：允许安全的公网公用 DNS / IP")
    void testAllowSafePublicDomain() {
        SafeDns safeDns = new SafeDns(hostname -> List.of(InetAddress.getByName("8.8.8.8")));
        assertDoesNotThrow(() -> {
            List<InetAddress> addresses = safeDns.lookup("dns.google");
            assertEquals(1, addresses.size());
            assertEquals("8.8.8.8", addresses.get(0).getHostAddress());
        });
    }

    @Test
    @DisplayName("契约 6：SafeOkHttpClient 发起针对内网主机的请求被物理熔断")
    void testSafeOkHttpClientBlocksIntranetRequest() {
        OkHttpClient client = SafeOkHttpClientBuilder.buildSafeClient();
        Request request = new Request.Builder()
                .url("http://127.0.0.1:59999/actuator")
                .build();

        assertThrows(IOException.class, () -> {
            client.newCall(request).execute();
        });
    }

    @Test
    @DisplayName("契约 7：SafeOkHttpClient 阻断针对元数据 IP 的请求")
    void testSafeOkHttpClientBlocksMetadataEndpoint() {
        OkHttpClient client = SafeOkHttpClientBuilder.buildSafeClient();
        Request request = new Request.Builder()
                .url("http://169.254.169.254/latest/meta-data/")
                .build();

        assertThrows(IOException.class, () -> {
            client.newCall(request).execute();
        });
    }
}
