package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Neo4j 用户实体偏好图与 2-Hop 激活扩散服务。
 * 遵循 Phase 21 架构设计 (Theorem 2.1 ~ 2.3)：
 * 1. 多租户硬隔离: (userId, scope, name) 复合约束
 * 2. 度数截断与分支因子限制: hop1Limit <= 10, hop2Limit <= 5, 防超级节点 Supernode 爆炸
 * 3. 3s 事务级超时与软降级熔断保护
 */
@Slf4j
@Service
public class UserMemoryGraphService {

    private final Driver driver;

    private static final String UPSERT_PREFERENCE_CYPHER = """
            MERGE (u:UserMemoryEntity {userId: $userId, scope: $scope, name: $userName})
            ON CREATE SET 
                u.id = randomUUID(),
                u.type = 'User',
                u.importance = 1.0,
                u.createdAt = timestamp(),
                u.lastAccessedAt = timestamp()

            MERGE (target:UserMemoryEntity {userId: $userId, scope: $scope, name: $targetName})
            ON CREATE SET 
                target.id = randomUUID(),
                target.type = $targetType,
                target.importance = $importance,
                target.createdAt = timestamp(),
                target.lastAccessedAt = timestamp()
            ON MATCH SET 
                target.lastAccessedAt = timestamp(),
                target.importance = CASE WHEN $importance > target.importance THEN $importance ELSE target.importance END

            MERGE (u)-[r:PREFERS {sentiment: $sentiment}]->(target)
            ON CREATE SET 
                r.weight = $initialWeight,
                r.count = 1,
                r.updatedAt = timestamp()
            ON MATCH SET 
                r.weight = r.weight + $deltaWeight,
                r.count = r.count + 1,
                r.updatedAt = timestamp();
            """;

    private static final String SPREAD_ACTIVATION_CYPHER = """
            MATCH (u:UserMemoryEntity {userId: $userId, scope: $scope})-[r1:PREFERS]->(p:UserMemoryEntity)
            WHERE r1.weight > 0.2 AND p.name IN $seedNames
            SET p.lastAccessedAt = timestamp()
            WITH u, p, r1
            ORDER BY r1.weight DESC
            LIMIT $hop1Limit

            OPTIONAL MATCH (p)-[r2:RELATED_TO]-(neighbor:UserMemoryEntity)
            WHERE neighbor.userId = $userId AND neighbor.scope = $scope AND coalesce(r2.weight, 1.0) >= 0.4
            WITH p, r1, collect(DISTINCT {
                neighborName: neighbor.name, 
                relWeight: coalesce(r2.weight, 0.5)
            })[0..$hop2LimitPerNode] AS relatedList

            UNWIND (CASE WHEN size(relatedList) > 0 THEN relatedList ELSE [{neighborName: p.name, relWeight: 1.0}] END) AS item
            WITH coalesce(item.neighborName, p.name) AS entityName,
                 sum((1.0 - $lambda) * (1.0 / size($seedNames)) + $lambda * (r1.weight * item.relWeight)) AS rawAct
            WHERE rawAct >= $threshold
            RETURN entityName, min(rawAct, 1.0) AS activation
            ORDER BY activation DESC
            LIMIT 50;
            """;

    public UserMemoryGraphService(@Autowired(required = false) Driver driver) {
        this.driver = driver;
    }

    public boolean isAvailable() {
        return driver != null;
    }

    /**
     * 原子 Upsert 用户偏好实体与因果关系
     */
    public void upsertPreference(String userId, String scope, String entityName, String entityType,
                                 double importance, String sentiment) {
        if (!isAvailable() || userId == null || scope == null || entityName == null || entityName.isBlank()) {
            return;
        }

        try (Session session = driver.session()) {
            session.run(UPSERT_PREFERENCE_CYPHER, Values.parameters(
                    "userId", userId,
                    "scope", scope,
                    "userName", "User_" + userId,
                    "targetName", entityName.trim(),
                    "targetType", entityType != null ? entityType : "Preference",
                    "importance", Math.min(1.0, Math.max(0.0, importance)),
                    "sentiment", sentiment != null ? sentiment : "POSITIVE",
                    "initialWeight", 1.0,
                    "deltaWeight", 0.1
            ));
            log.debug("Upserted user preference graph: userId={}, scope={}, entity={}", userId, scope, entityName);
        } catch (Exception e) {
            log.warn("Failed to upsert user preference in Neo4j (soft fail): entity={}, error={}", entityName, e.getMessage());
        }
    }

    /**
     * 执行 2-Hop 激活扩散检索 (带度数截断与 3s 超时熔断)
     *
     * @param userId            用户标识
     * @param scope             作用域
     * @param seedNames         种子实体名列表
     * @param lambda            阻尼扩散系数 (如 0.4)
     * @param hop1Limit         一跳展开度上限 (如 10)
     * @param hop2LimitPerNode  二跳每个节点展开度上限 (如 5)
     * @param threshold         正激活阈值 (如 0.02)
     * @return 实体名到激活度权重的映射
     */
    public Map<String, Double> spreadActivation(String userId, String scope, List<String> seedNames,
                                                double lambda, int hop1Limit, int hop2LimitPerNode, double threshold) {
        if (!isAvailable() || seedNames == null || seedNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> activationMap = new HashMap<>();
        SessionConfig sessionConfig = SessionConfig.builder()
                .withDefaultAccessMode(AccessMode.READ)
                .build();

        TransactionConfig txConfig = TransactionConfig.builder()
                .withTimeout(Duration.ofMillis(3000))
                .build();

        try (Session session = driver.session(sessionConfig)) {
            Result result = session.run(SPREAD_ACTIVATION_CYPHER, Values.parameters(
                    "userId", userId,
                    "scope", scope,
                    "seedNames", seedNames,
                    "lambda", lambda,
                    "hop1Limit", hop1Limit,
                    "hop2LimitPerNode", hop2LimitPerNode,
                    "threshold", threshold
            ).asMap(), txConfig);

            while (result.hasNext()) {
                Record record = result.next();
                String name = record.get("entityName").asString();
                double act = record.get("activation").asDouble(0.0);
                activationMap.put(name, act);
            }
        } catch (Exception e) {
            log.warn("Neo4j 2-Hop activation diffusion fallback (timeout or error): userId={}, error={}", userId, e.getMessage());
            return Collections.emptyMap(); // 软降级返回空 Map
        }

        return activationMap;
    }
}
