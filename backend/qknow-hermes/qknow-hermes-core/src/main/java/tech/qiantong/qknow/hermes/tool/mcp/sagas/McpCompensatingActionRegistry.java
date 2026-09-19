package tech.qiantong.qknow.hermes.tool.mcp.sagas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 生产级 MCP 补偿动作注册表与防悬挂幂等拦截器
 * 1. 提供 MCP 工具正向操作对应的逆序补偿函数注册；
 * 2. 引入全局唯一 LeaseToken 租约生命周期管理；
 * 3. 实现防悬挂墓碑标记 (Tombstone)，彻底根除网络乱序导致的“补偿先于正向到达”竞态脏写。
 */
@Component
public class McpCompensatingActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpCompensatingActionRegistry.class);

    /**
     * 补偿动作注册表：toolName -> 补偿执行函数 (入参: leaseToken, 参数 Map; 返回值: 是否成功)
     */
    private final Map<String, BiFunction<String, Map<String, Object>, Boolean>> compensationHandlers = new ConcurrentHashMap<>();

    /**
     * 防悬挂墓碑集合：记录已提前执行补偿的 leaseToken
     */
    private final Set<String> tombstones = ConcurrentHashMap.newKeySet();

    /**
     * 已成功执行正向操作的 leaseToken 集合 (幂等防重)
     */
    private final Set<String> executedLeases = ConcurrentHashMap.newKeySet();

    /**
     * 已成功执行逆序补偿的 leaseToken 集合 (幂等防重)
     */
    private final Set<String> compensatedLeases = ConcurrentHashMap.newKeySet();

    /**
     * 为指定 MCP 工具注册逆序补偿动作
     *
     * @param toolName 工具唯一标识 (如 "erp.lockQuota")
     * @param handler  补偿执行函数 (如释放配额 "erp.releaseQuota")
     */
    public void registerCompensation(String toolName, BiFunction<String, Map<String, Object>, Boolean> handler) {
        if (toolName == null || handler == null) {
            throw new IllegalArgumentException("工具名称与补偿处理器不能为空");
        }
        compensationHandlers.put(toolName, handler);
        log.info("[McpCompensatingActionRegistry] 工具 {} 成功注册逆序补偿处理器", toolName);
    }

    /**
     * 生成全局唯一的租约令牌 (LeaseToken)
     *
     * @param transactionId Sagas 事务 ID
     * @param toolName      工具名称
     * @return 全局唯一租约令牌
     */
    public String generateLeaseToken(String transactionId, String toolName) {
        return "LEASE-" + transactionId + "-" + toolName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 检查正向操作是否允许执行 (防悬挂与幂等校验)
     * 若该租约已被标记为墓碑（即补偿已先行到达），则直接拒绝执行
     *
     * @param leaseToken 租约令牌
     * @return 是否允许正向执行
     */
    public boolean canExecuteForward(String leaseToken) {
        if (leaseToken == null) {
            return false;
        }
        if (tombstones.contains(leaseToken)) {
            log.warn("[防悬挂拦截] 租约 {} 命中墓碑标记 (补偿先于正向到达)，坚决拦截正向操作以防止脏写资产悬挂", leaseToken);
            return false;
        }
        if (executedLeases.contains(leaseToken)) {
            log.warn("[幂等拦截] 租约 {} 已执行过正向操作，拒绝重复执行", leaseToken);
            return false;
        }
        return true;
    }

    /**
     * 记录正向操作执行成功
     *
     * @param leaseToken 租约令牌
     */
    public void markForwardExecuted(String leaseToken) {
        if (leaseToken != null) {
            executedLeases.add(leaseToken);
        }
    }

    /**
     * 执行逆序补偿操作
     * 若正向操作尚未到达，则立即打上墓碑标记 (Tombstone)，阻止后续正向操作执行
     *
     * @param toolName   原正向工具名称
     * @param leaseToken 租约令牌
     * @param params     原始调用参数与上下文
     * @return 补偿是否成功
     */
    public boolean executeCompensation(String toolName, String leaseToken, Map<String, Object> params) {
        if (toolName == null || leaseToken == null) {
            log.error("[Sagas 补偿失败] 工具名称或租约令牌为空");
            return false;
        }

        // 幂等防重：若该租约已经补偿过，直接返回成功
        if (compensatedLeases.contains(leaseToken)) {
            log.info("[Sagas 补偿幂等] 租约 {} 已经成功执行过补偿，直接返回成功", leaseToken);
            return true;
        }

        // 检查正向操作是否尚未执行 (乱序场景：补偿先于正向到达)
        if (!executedLeases.contains(leaseToken)) {
            log.warn("[Sagas 防悬挂] 检测到租约 {} 补偿先于正向操作到达，打上墓碑标记并跳过实体补偿", leaseToken);
            tombstones.add(leaseToken);
            compensatedLeases.add(leaseToken);
            return true;
        }

        // 正常逆序补偿逻辑
        BiFunction<String, Map<String, Object>, Boolean> handler = compensationHandlers.get(toolName);
        if (handler == null) {
            log.warn("[Sagas 补偿无处理器] 工具 {} 未注册逆序补偿动作，默认为空操作放行", toolName);
            compensatedLeases.add(leaseToken);
            return true;
        }

        try {
            boolean success = handler.apply(leaseToken, params != null ? params : Map.of());
            if (success) {
                compensatedLeases.add(leaseToken);
                log.info("[Sagas 补偿成功] 工具 {} (租约: {}) 逆序补偿执行成功", toolName, leaseToken);
            } else {
                log.error("[Sagas 补偿失败] 工具 {} (租约: {}) 逆序补偿处理器返回失败", toolName, leaseToken);
            }
            return success;
        } catch (Exception e) {
            log.error("[Sagas 补偿异常] 工具 {} (租约: {}) 执行逆序补偿发生未捕获异常: {}", toolName, leaseToken, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 判断指定租约是否已被打上墓碑标记
     */
    public boolean isTombstoned(String leaseToken) {
        return leaseToken != null && tombstones.contains(leaseToken);
    }

    /**
     * 重置所有注册表与状态 (供测试隔离使用)
     */
    public void clearAll() {
        compensationHandlers.clear();
        tombstones.clear();
        executedLeases.clear();
        compensatedLeases.clear();
    }
}
