package tech.qiantong.qknow.module.kmc.service.rag.evolution.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.evolution.dto.TemporalFactStatement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时态因果偏序与多源事实冲突偏序消歧门禁 (定理 1.2)
 * 基于有效时间戳、数据源权威性与半衰期指数衰减，对单值函数谓词执行半格偏序覆盖算子 ⊞，隔离冲突
 */
@Component
public class TemporalConflictDisambiguationGate {

    private static final Logger log = LoggerFactory.getLogger(TemporalConflictDisambiguationGate.class);

    // 单值函数互斥谓词注册表 (Functional Predicates)
    private final Set<String> functionalPredicates = ConcurrentHashMap.newKeySet();

    // 事实库存储: statementId -> Statement
    private final Map<String, TemporalFactStatement> factStore = new ConcurrentHashMap<>();

    // 索引: subjectId#predicate -> list of statementId
    private final Map<String, List<String>> subjectPredicateIndex = new ConcurrentHashMap<>();

    // 默认半衰期 (180 天以毫秒计)
    private static final double HALF_LIFE_MILLIS = 180.0 * 24.0 * 3600.0 * 1000.0;
    private static final double LAMBDA = Math.log(2.0) / HALF_LIFE_MILLIS;

    public TemporalConflictDisambiguationGate() {
        // 预置常见单值函数互斥谓词
        functionalPredicates.add("legalRepresentative");
        functionalPredicates.add("headquartersLocation");
        functionalPredicates.add("contractStatus");
        functionalPredicates.add("currentCeo");
        functionalPredicates.add("currentAuditStatus");
    }

    public void registerFunctionalPredicate(String predicate) {
        if (predicate != null) {
            functionalPredicates.add(predicate);
        }
    }

    /**
     * 摄入事实并执行时态偏序冲突消歧 (定理 1.2)
     */
    public synchronized TemporalFactStatement ingestAndDisambiguateFact(TemporalFactStatement incomingFact) {
        if (incomingFact == null || incomingFact.statementId() == null) {
            throw new IllegalArgumentException("事实声明不可为空");
        }

        String key = incomingFact.subjectId() + "#" + incomingFact.predicate();
        boolean isFunctional = functionalPredicates.contains(incomingFact.predicate());

        if (!isFunctional) {
            // 非单值互斥谓词，直接活跃入库
            factStore.put(incomingFact.statementId(), incomingFact);
            subjectPredicateIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(incomingFact.statementId());
            return incomingFact;
        }

        // 针对单值互斥谓词检查历史活跃事实
        List<String> existingIds = subjectPredicateIndex.computeIfAbsent(key, k -> new ArrayList<>());
        TemporalFactStatement currentActive = null;
        for (String id : existingIds) {
            TemporalFactStatement stmt = factStore.get(id);
            if (stmt != null && stmt.status() == TemporalFactStatement.FactStatus.ACTIVE) {
                currentActive = stmt;
                break;
            }
        }

        if (currentActive == null) {
            // 无历史活跃事实，直接入库为 ACTIVE
            TemporalFactStatement activeFact = incomingFact.status() == TemporalFactStatement.FactStatus.ACTIVE ?
                    incomingFact : incomingFact.withStatus(TemporalFactStatement.FactStatus.ACTIVE);
            factStore.put(activeFact.statementId(), activeFact);
            existingIds.add(activeFact.statementId());
            return activeFact;
        }

        // 若宾语相同，则属于同一事实的重复确认或刷新
        if (currentActive.objectId().equals(incomingFact.objectId())) {
            factStore.put(incomingFact.statementId(), incomingFact);
            existingIds.add(incomingFact.statementId());
            return incomingFact;
        }

        // 存在互斥冲突事实，应用半格偏序覆盖算子 ⊞ (定理 1.2)
        long now = System.currentTimeMillis();
        double omegaIncoming = calculateEffectiveWeight(incomingFact, now);
        double omegaCurrent = calculateEffectiveWeight(currentActive, now);

        if (incomingFact.validTime() > currentActive.validTime() && omegaIncoming >= 0.75 * omegaCurrent) {
            // 新事实时效更新且权重达标：将旧事实原子标记为 SUPERSEDED，新事实置为 ACTIVE
            TemporalFactStatement supersededOld = currentActive.withStatus(TemporalFactStatement.FactStatus.SUPERSEDED);
            factStore.put(supersededOld.statementId(), supersededOld);

            TemporalFactStatement activeNew = incomingFact.withStatus(TemporalFactStatement.FactStatus.ACTIVE);
            factStore.put(activeNew.statementId(), activeNew);
            existingIds.add(activeNew.statementId());

            if (log.isDebugEnabled()) {
                log.debug("事实冲突消歧: 旧事实 {} 已被新事实 {} 覆盖 SUPERSEDED", currentActive.statementId(), activeNew.statementId());
            }
            return activeNew;
        } else if (currentActive.validTime() > incomingFact.validTime() && omegaCurrent >= 0.75 * omegaIncoming) {
            // 旧事实更为新且有效，新事实标记为历史失效 SUPERSEDED
            TemporalFactStatement supersededIncoming = incomingFact.withStatus(TemporalFactStatement.FactStatus.SUPERSEDED);
            factStore.put(supersededIncoming.statementId(), supersededIncoming);
            existingIds.add(supersededIncoming.statementId());
            return supersededIncoming;
        } else {
            // 证据权重冲突或时效无法严格偏序断言，两者均标记为 DISPUTED 并待仲裁
            TemporalFactStatement disputedOld = currentActive.withStatus(TemporalFactStatement.FactStatus.DISPUTED);
            factStore.put(disputedOld.statementId(), disputedOld);

            TemporalFactStatement disputedNew = incomingFact.withStatus(TemporalFactStatement.FactStatus.DISPUTED);
            factStore.put(disputedNew.statementId(), disputedNew);
            existingIds.add(disputedNew.statementId());

            if (log.isDebugEnabled()) {
                log.debug("事实冲突无法确定偏序: 事实 {} 与 {} 均置为 DISPUTED 隔离状态", currentActive.statementId(), incomingFact.statementId());
            }
            return disputedNew;
        }
    }

    private double calculateEffectiveWeight(TemporalFactStatement fact, long now) {
        long age = Math.max(0, now - fact.validTime());
        double decay = Math.exp(-LAMBDA * age);
        return fact.confidence() * fact.authorityWeight() * decay;
    }

    public List<TemporalFactStatement> getActiveFacts(String subjectId, String predicate) {
        String key = subjectId + "#" + predicate;
        List<String> ids = subjectPredicateIndex.getOrDefault(key, Collections.emptyList());
        List<TemporalFactStatement> active = new ArrayList<>();
        for (String id : ids) {
            TemporalFactStatement s = factStore.get(id);
            if (s != null && s.status() == TemporalFactStatement.FactStatus.ACTIVE) {
                active.add(s);
            }
        }
        return active;
    }

    public List<TemporalFactStatement> getAllStatements() {
        return new ArrayList<>(factStore.values());
    }
}
