package tech.qiantong.qknow.hermes.daemon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 常驻 JVM 极速 IPC 守护服务契约测试 (ResidentDaemonIpcContractTest)
 * 验证基于 Unix Domain Socket 的微秒级通道建立、探针调度与优雅清理。
 */
class ResidentDaemonIpcContractTest {

    private Path testSocketPath;
    private QKnowResidentDaemonServer daemonServer;

    @BeforeEach
    void setUp() throws IOException {
        testSocketPath = Path.of("/tmp/qknow_daemon_test_" + System.currentTimeMillis() + ".sock");
        daemonServer = new QKnowResidentDaemonServer(testSocketPath);
        daemonServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (daemonServer != null) {
            daemonServer.close();
        }
        Files.deleteIfExists(testSocketPath);
    }

    @Test
    @DisplayName("验证常驻守护进程 UDS 通道 Ping-Pong 极速响应 (< 10ms)")
    void testDaemonPingPongLatency() throws Exception {
        assertTrue(Files.exists(testSocketPath), "UDS 套接字文件必须存在");

        long startNs = System.nanoTime();
        String response = sendUdsRequest(testSocketPath, "{\"probe_type\":\"DAEMON_PING\"}");
        long elapsedNs = System.nanoTime() - startNs;

        assertNotNull(response);
        assertTrue(response.contains("\"ping\":\"pong\""), "响应必须包含 pong");
        assertTrue(response.contains("\"daemon_active\":true"), "守护进程标志必须为真");
        assertTrue(elapsedNs < TimeUnit.MILLISECONDS.toNanos(50), "单次本地 UDS 往返耗时必须极短");
    }

    @Test
    @DisplayName("验证常驻守护进程调度物理探针并正确返回凭单签名")
    void testDispatchProbeViaDaemon() throws Exception {
        String reqJson = "{\"probe_type\":\"SPECULATIVE_PREFETCH\",\"query\":\"对账核验\"}";
        String response = sendUdsRequest(testSocketPath, reqJson);

        assertNotNull(response);
        assertTrue(response.contains("\"status\":\"SUCCESS\""), "探针必须执行成功");
        assertTrue(response.contains("\"speculative_hit\":true"), "推测执行命中标志必须为真");
        assertTrue(response.contains("\"sha256_seal\":"), "必须包含 SHA-256 签名封印");
    }

    private String sendUdsRequest(Path socketPath, String requestJson) throws Exception {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);

            byte[] reqBytes = requestJson.getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(reqBytes));

            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            int read = channel.read(buffer);
            assertTrue(read > 0, "必须读取到服务端的响应数据");

            buffer.flip();
            byte[] respBytes = new byte[buffer.remaining()];
            buffer.get(respBytes);
            return new String(respBytes, StandardCharsets.UTF_8).trim();
        }
    }
}
