package tech.qiantong.qknow.hermes.tool.mcp.sagas.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagaLeaseRecord;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 第二道防线：基于 Fencing Token 与 Lease TTL 的分布式租约防脑裂安全接管协调器
 * 对齐 ESWA Lemma 3.1（获胜者崩溃后的租约超时活性自愈机制）与 Martin Kleppmann Fencing 屏障铁律
 *
 * 1. 租约三元组: leaseOwnerId, expireAt (TTL 绝对时间戳), fencingToken (64位单调递增原子世代计数器)；
 * 2. 节点心跳保活机制；后继备用节点检测到 now > expireAt 时原子 CAS 接管，Token 严格递增；
 * 3. 任何外部资源物理写操作必须前置校验 fencingToken，彻底拒绝过期幽灵写 (Zombie Write)。
 */
@Component
public class DistributedLeaseCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DistributedLeaseCoordinator.class);

    // 租约注册表: transactionId -> McpSagaLeaseRecord
    private final ConcurrentHashMap<String, McpSagaLeaseRecord> leaseRegistry = new ConcurrentHashMap<>();
    // 全局世代序列号发生器 (严格单调递增)
    private final AtomicLong globalFencingGenerator = new AtomicLong(1000L);

    /**
     * 获取或尝试接管事务租约 (原子 CAS 自愈，对齐 ESWA Lemma 3.1)
     *
     * @param transactionId 全局事务唯一标识
     * @param requesterId   请求节点唯一标识 (如 worker-01, worker-02)
     * @param ttlMillis     租约有效时长 (毫秒)
     * @return 最新的租约元组实体
     * @throws IllegalStateException 若租约正被他人持有且尚未超期
     */
    public synchronized McpSagaLeaseRecord acquireOrTakeoverLease(String transactionId, String requesterId, long ttlMillis) {
        long now = System.currentTimeMillis();
        long effectiveTtl = ttlMillis > 0 ? ttlMillis : 3000L;
        McpSagaLeaseRecord current = leaseRegistry.get(transactionId);

        if (current == null || "RELEASED".equals(current.status())) {
            // 首次获取或已释放重用：分配初始 Fencing Token
            long initialToken = globalFencingGenerator.incrementAndGet();
            McpSagaLeaseRecord newLease = new McpSagaLeaseRecord(
                    transactionId, requesterId, initialToken, now + effectiveTtl, "ACTIVE"
            );
            leaseRegistry.put(transactionId, newLease);
            log.info("[DistributedLeaseCoordinator] 节点 {} 首次成功获得事务 {} 租约, FencingToken: {}",
                    requesterId, transactionId, initialToken);
            return newLease;
        }

        // 既有租约存在：检查是否持有者自身续期
        if (current.leaseOwnerId().equals(requesterId)) {
            McpSagaLeaseRecord renewed = new McpSagaLeaseRecord(
                    transactionId, requesterId, current.fencingToken(), now + effectiveTtl, "ACTIVE"
            );
            leaseRegistry.put(transactionId, renewed);
            return renewed;
        }

        // 非持有者：检查租约是否已超时失效 (Crash-Stop 判定)
        if (current.isExpired(now)) {
            // 超时判定原主已宕机或假死，备用节点安全接管，Token 单调严格递增！
            long nextToken = globalFencingGenerator.incrementAndGet();
            McpSagaLeaseRecord takenOver = new McpSagaLeaseRecord(
                    transactionId, requesterId, nextToken, now + effectiveTtl, "ACTIVE"
            );
            leaseRegistry.put(transactionId, takenOver);
            log.warn("[DistributedLeaseCoordinator] 事务 {} 原租约持有者 {} 超时失效，备用节点 {} 原子 CAS 成功接管！Token: {} -> {}",
                    transactionId, current.leaseOwnerId(), requesterId, current.fencingToken(), nextToken);
            return takenOver;
        }

        // 仍在有效期且为他人持有，拒绝侵入
        throw new IllegalStateException("事务 " + transactionId + " 租约正被 " + current.leaseOwnerId()
                + " 持有且尚未超时 (剩余 " + (current.expireAtMillis() - now) + "ms)");
    }

    /**
     * 外部写屏障校验 (Write Barrier)：彻底阻断过期幽灵写 (Zombie Write)
     *
     * @param transactionId 全局事务唯一标识
     * @param incomingToken 请求携带的 Fencing Token
     * @throws SecurityException 若请求 Token 小于当前最新租约世代
     */
    public void validateFencingToken(String transactionId, long incomingToken) {
        McpSagaLeaseRecord current = leaseRegistry.get(transactionId);
        if (current == null) {
            throw new IllegalStateException("事务 " + transactionId + " 尚未建立有效租约！");
        }
        if (incomingToken < current.fencingToken()) {
            log.error("[DistributedLeaseCoordinator] 拦截幽灵写攻击！请求 Token {} < 当前租约世代 {}",
                    incomingToken, current.fencingToken());
            throw new SecurityException("检测到幽灵写攻击：传入 Token " + incomingToken
                    + " 已被集群备用节点接管（最新世代: " + current.fencingToken() + "）！");
        }
    }

    /**
     * 正常释放事务租约
     */
    public synchronized void releaseLease(String transactionId, String requesterId, long incomingToken) {
        McpSagaLeaseRecord current = leaseRegistry.get(transactionId);
        if (current != null && current.leaseOwnerId().equals(requesterId) && current.fencingToken() == incomingToken) {
            leaseRegistry.put(transactionId, new McpSagaLeaseRecord(
                    transactionId, requesterId, current.fencingToken(), 0L, "RELEASED"
            ));
            log.info("[DistributedLeaseCoordinator] 事务 {} 租约由 {} 正常释放", transactionId, requesterId);
        }
    }

    /**
     * 查询指定事务的当前租约元组
     */
    public McpSagaLeaseRecord getLease(String transactionId) {
        return leaseRegistry.get(transactionId);
    }
}
