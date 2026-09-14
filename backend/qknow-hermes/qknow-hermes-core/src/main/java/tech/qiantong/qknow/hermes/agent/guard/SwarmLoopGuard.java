package tech.qiantong.qknow.hermes.agent.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蜂群环路防死锁与振荡熔断器
 */
@Slf4j
@Component
public class SwarmLoopGuard {

    private static final int MAX_SWARM_DEPTH = 5;
    private static final int MAX_SEMANTIC_REPETITIONS = 3;

    // 记录会话调用链 (SessionId -> 调用路径列表)
    private final Map<String, List<String>> sessionCallChains = new ConcurrentHashMap<>();
    // 记录语义指纹滑动窗口 (SessionId -> 语义指纹列表)
    private final Map<String, LinkedList<String>> semanticFingerprints = new ConcurrentHashMap<>();

    /**
     * 前置检查：深度校验与调用拓扑环路校验
     */
    public void preCheck(String sessionId, String currentTaskId) {
        if (sessionId == null || currentTaskId == null) {
            return;
        }

        List<String> chain = sessionCallChains.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (chain) {
            // 1. 最大协同跳转深度检查 (Max Swarm Depth <= 5)
            if (chain.size() >= MAX_SWARM_DEPTH) {
                log.error("[SwarmLoopGuard] 会话 {} 协同深度达上限 {}，触发强制熔断！调用链: {}",
                        sessionId, MAX_SWARM_DEPTH, chain);
                throw new SwarmLoopException("SWARM_MAX_DEPTH_EXCEEDED",
                        "协同跳转深度已达阈值 " + MAX_SWARM_DEPTH + "，防止死锁与 Token 耗尽强制收敛。");
            }

            // 2. 拓扑环路检测 (检测同一个任务是否在调用链中重复出现)
            if (chain.contains(currentTaskId)) {
                log.error("[SwarmLoopGuard] 会话 {} 检测到调用拓扑成环！目标任务 [{}] 已存在于调用链: {}",
                        sessionId, currentTaskId, chain);
                throw new SwarmLoopException("SWARM_TOPOLOGY_CYCLE_DETECTED",
                        "检测到多 Agent 循环委托拓扑成环: " + currentTaskId);
            }

            chain.add(currentTaskId);
        }
    }

    /**
     * 后置检查：输出内容语义指纹检测与滑动窗口振荡熔断
     */
    public void inspectOutput(String sessionId, String output) {
        if (sessionId == null || output == null || output.isBlank()) {
            return;
        }

        // 提取核心文本特征生成 SHA-256 哈希指纹
        String normalized = output.replaceAll("\\s+", "").toLowerCase();
        String fingerprint = sha256Hex(normalized);

        LinkedList<String> window = semanticFingerprints.computeIfAbsent(sessionId, k -> new LinkedList<>());
        synchronized (window) {
            window.addLast(fingerprint);
            long duplicates = window.stream().filter(fp -> fp.equals(fingerprint)).count();
            if (duplicates >= MAX_SEMANTIC_REPETITIONS) {
                log.warn("[SwarmLoopGuard] 会话 {} 触发语义振荡熔断！相同语义输出已出现 {} 次", sessionId, duplicates);
                throw new SwarmLoopException("SWARM_SEMANTIC_OSCILLATION_BREAKER",
                        "智能体群进入语义无意义循环震荡，已强制熔断并直接收敛当前上下文。");
            }

            if (window.size() > 6) {
                window.removeFirst();
            }
        }
    }

    public void cleanSession(String sessionId) {
        if (sessionId != null) {
            sessionCallChains.remove(sessionId);
            semanticFingerprints.remove(sessionId);
        }
    }

    private String sha256Hex(String input) {
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
            return String.valueOf(input.hashCode());
        }
    }
}
