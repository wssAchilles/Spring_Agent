package tech.qiantong.qknow.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.qiantong.qknow.mcp.client.transport.StdioEnterpriseTransport;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("企业级 MCP 客户端传输协议栈测试 (StdioEnterpriseTransportTest)")
class EnterpriseMcpClientTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试1: 双向 Stdio 通信与 JSON-RPC 响应解析及 Ping 心跳探活")
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testBidirectionalCommunicationAndPing() throws Exception {
        // 使用 python3 模拟 MCP 进程（实时 echo 处理 JSON-RPC 请求）
        List<String> command = List.of(
                "python3", "-u", "-c",
                "import sys, json\n" +
                "for line in sys.stdin:\n" +
                "    req = json.loads(line.strip())\n" +
                "    resp = {'jsonrpc': '2.0', 'id': req.get('id'), 'result': {'status': 'ok', 'echo_method': req.get('method')}}\n" +
                "    print(json.dumps(resp), flush=True)\n"
        );

        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(command);
        transport.start();
        assertTrue(transport.isConnected(), "传输通道启动后应为已连接状态");

        try {
            CompletableFuture<JsonRpcResponse> future = transport.sendRequest("tools/list", Map.of("filter", "all"));
            JsonRpcResponse response = future.get(3, TimeUnit.SECONDS);

            assertNotNull(response, "响应不应为空");
            assertNull(response.error(), "不应有错误");
            assertTrue(response.result() instanceof Map<?, ?>, "result 应为 Map");
            Map<?, ?> resMap = (Map<?, ?>) response.result();
            assertEquals("tools/list", resMap.get("echo_method"));

            // 测试 ping
            boolean pingSuccess = transport.ping().get(3, TimeUnit.SECONDS);
            assertTrue(pingSuccess, "心跳 Ping 探测应成功");
        } finally {
            transport.close();
            assertFalse(transport.isConnected(), "关闭后通道应处于非连接状态");
        }
    }

    @Test
    @DisplayName("测试2: Stdio 独立虚拟线程排空 stderr 防管道死锁")
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testStderrDrainingUnderHeavyStderrOutput() throws Exception {
        // 模拟一个向 stderr 疯狂打印日志但 stdout 正常响应的 MCP Server
        List<String> command = List.of(
                "python3", "-u", "-c",
                "import sys, json\n" +
                "for i in range(10):\n" +
                "    sys.stderr.write(f'STDERR_LOG_LINE_{i}\\n')\n" +
                "sys.stderr.flush()\n" +
                "for line in sys.stdin:\n" +
                "    req = json.loads(line.strip())\n" +
                "    sys.stderr.write('Processing request on stderr\\n')\n" +
                "    sys.stderr.flush()\n" +
                "    resp = {'jsonrpc': '2.0', 'id': req.get('id'), 'result': {'drained': True}}\n" +
                "    print(json.dumps(resp), flush=True)\n"
        );

        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(command);
        transport.start();

        try {
            // 等待一小会儿确保初始 stderr 产生
            Thread.sleep(100);

            CompletableFuture<JsonRpcResponse> future = transport.sendRequest("test/drained", Map.of());
            JsonRpcResponse resp = future.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);

            List<String> recentStderr = transport.getRecentStderr();
            assertFalse(recentStderr.isEmpty(), "stderr 缓冲池应成功捕获到日志");
            assertTrue(recentStderr.stream().anyMatch(s -> s.contains("STDERR_LOG_LINE")),
                    "缓冲池应包含 stderr 输出内容，证明虚拟线程排空有效运行");
        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("测试3: 滑动窗口信用背压溢出拦截 (W_max = 2)")
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testCreditBackpressureOverflow() throws Exception {
        // 模拟一个故意不回复的假死 MCP 进程
        List<String> command = List.of(
                "python3", "-u", "-c",
                "import sys, time\n" +
                "for line in sys.stdin:\n" +
                "    time.sleep(10) # 延迟响应模拟堵塞\n"
        );

        // 设置信用窗口上限为 2，超时时间为 100ms
        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(
                "test-backpressure",
                command,
                Map.of(),
                null,
                2,
                100,
                objectMapper
        );
        transport.start();

        try {
            // 发送第 1 个请求（消耗 1 个信用）
            CompletableFuture<JsonRpcResponse> f1 = transport.sendRequest("slow1", Map.of());
            assertEquals(1, transport.getInFlightCount());

            // 发送第 2 个请求（消耗 1 个信用，信用打满）
            CompletableFuture<JsonRpcResponse> f2 = transport.sendRequest("slow2", Map.of());
            assertEquals(2, transport.getInFlightCount());

            // 发送第 3 个请求（超出信用窗口，等待 100ms 后必须抛出背压溢出）
            CompletableFuture<JsonRpcResponse> f3 = transport.sendRequest("slow3", Map.of());

            ExecutionException ex = assertThrows(ExecutionException.class, () -> f3.get(1, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("ERR_MCP_BACKPRESSURE_OVERFLOW"),
                    "超出信用窗口上限必须抛出 ERR_MCP_BACKPRESSURE_OVERFLOW 异常");

        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("测试4: 优雅关闭与递归强杀杜绝孤儿进程 (Zero Orphan Process)")
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testGracefulCloseAndZeroProcessLeak() throws Exception {
        // 启动带子进程的命令 (sh 中 sleep)
        List<String> command = List.of("sh", "-c", "sleep 60");

        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(command);
        transport.start();
        assertTrue(transport.isConnected());

        Process process = transport.getProcess();
        assertNotNull(process);
        assertTrue(process.isAlive(), "进程刚启动应处于存活状态");

        // 执行销毁与清理
        transport.close();

        // 验证状态
        assertFalse(transport.isConnected());
        assertFalse(process.isAlive(), "调用 close 后主进程及子进程树必须被彻底杀灭");
    }
}
