package tech.qiantong.qknow.ai.agent.guard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class DefensiveToolExecutor {
    private final int maxRepeats;
    private final long timeoutMillis;
    private final int maxLength;
    private final int spillThreshold;
    private final Path spillDirectory;
    private final Map<String, Integer> paramHashCount = new HashMap<>();
    private final ExecutorService executorService;

    public DefensiveToolExecutor(int maxRepeats, long timeoutMillis, int maxLength, int spillThreshold, Path spillDirectory) {
        this.maxRepeats = maxRepeats;
        this.timeoutMillis = timeoutMillis;
        this.maxLength = maxLength;
        this.spillThreshold = spillThreshold;
        this.spillDirectory = spillDirectory;
        this.executorService = Executors.newCachedThreadPool();
        if (spillDirectory != null && !Files.exists(spillDirectory)) {
            try {
                Files.createDirectories(spillDirectory);
            } catch (IOException e) {
                throw new RuntimeException("无法创建溢出临时目录 (Spill Directory)", e);
            }
        }
    }

    public String executeTool(String toolName, String params, Callable<String> toolLogic) {
        // 1. Hash 记录状态机防死循环 (Semantic Loop)
        String paramHash = hash(toolName + ":" + params);
        int count = paramHashCount.getOrDefault(paramHash, 0) + 1;
        if (count > maxRepeats) {
            throw new CircuitBreakerException("检测到语义死循环 (Semantic loop detected): 相同参数的工具被调用了太多次。");
        }
        paramHashCount.put(paramHash, count);

        // 2. 超时预算控制 (Timeout budget control)
        String result;
        try {
            Future<String> future = executorService.submit(toolLogic);
            result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new CircuitBreakerException("工具执行超时，超出预算。", e);
        } catch (Exception e) {
            throw new RuntimeException("工具执行失败", e);
        }

        if (result == null) {
            return null;
        }

        // 3. 结果超限处理：Spill 落盘 或 头尾截断 (Head-Tail Truncation)
        int len = result.length();
        if (len > spillThreshold && spillDirectory != null) {
            return spillToDisk(result);
        } else if (len > maxLength) {
            return truncate(result);
        }

        return result;
    }

    private String spillToDisk(String result) {
        String fileName = UUID.randomUUID().toString() + ".txt";
        Path filePath = spillDirectory.resolve(fileName);
        try {
            Files.writeString(filePath, result, StandardCharsets.UTF_8);
            return "FileURI: file://" + filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("未能将结果落盘 (Spill)", e);
        }
    }

    private String truncate(String result) {
        int half = maxLength / 2;
        String head = result.substring(0, half);
        String tail = result.substring(result.length() - half);
        return head + "\n<...[truncated]...>\n" + tail;
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("找不到 Hash 算法", e);
        }
    }
    
    public void shutdown() {
        executorService.shutdownNow();
    }
}
