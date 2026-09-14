package tech.qiantong.qknow.hermes.memory.dst;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.model.SlotValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图有向无环图 (Intent DAG)
 * 管理领域意图之间的流转拓扑、必需槽位依赖以及分支插话允许规则。
 */
@Slf4j
public class IntentDAG {

    /**
     * 意图节点定义
     */
    public record IntentNode(
            String intentName,
            Set<String> requiredSlots,
            Set<String> allowedTransitions,
            Set<String> allowedSubIntents
    ) {}

    private final Map<String, IntentNode> nodes = new ConcurrentHashMap<>();

    public IntentDAG() {
        initDefaultDAG();
    }

    /**
     * 初始化常用意图拓扑
     */
    private void initDefaultDAG() {
        // 指标诊断意图 (需要 metric_name, time_range)
        registerIntent("METRIC_DIAGNOSIS",
                Set.of("metric_name", "time_range"),
                Set.of("METRIC_DIAGNOSIS", "ROOT_CAUSE_ANALYSIS", "ACTION_EXECUTION"),
                Set.of("SYSTEM_LOAD_QUERY", "LOG_INSPECTION"));

        // 系统负载临时插话查询 (需要 cluster_id)
        registerIntent("SYSTEM_LOAD_QUERY",
                Set.of("cluster_id"),
                Set.of("SYSTEM_LOAD_QUERY", "METRIC_DIAGNOSIS"),
                Set.of());

        // 根因分析意图
        registerIntent("ROOT_CAUSE_ANALYSIS",
                Set.of("root_cause_target"),
                Set.of("ACTION_EXECUTION"),
                Set.of("SYSTEM_LOAD_QUERY"));

        // 动作执行意图
        registerIntent("ACTION_EXECUTION",
                Set.of("action_type", "target_host"),
                Set.of(),
                Set.of());
    }

    /**
     * 注册/更新意图节点
     */
    public void registerIntent(String intentName, Set<String> requiredSlots,
                               Set<String> transitions, Set<String> allowedSubIntents) {
        nodes.put(intentName, new IntentNode(
                intentName,
                new HashSet<>(requiredSlots),
                new HashSet<>(transitions),
                new HashSet<>(allowedSubIntents)
        ));
    }

    /**
     * 校验从当前意图转移至目标意图是否合法
     */
    public boolean canTransition(String fromIntent, String toIntent) {
        if (fromIntent == null || toIntent == null) {
            return true;
        }
        if (fromIntent.equals(toIntent)) {
            return true;
        }
        IntentNode node = nodes.get(fromIntent);
        if (node == null) {
            // 未注册意图默认允许流转
            return true;
        }
        return node.allowedTransitions().contains(toIntent);
    }

    /**
     * 校验目标意图是否允许作为当前意图的分支插话挂起
     */
    public boolean isSubIntentAllowed(String parentIntent, String subIntent) {
        if (parentIntent == null || subIntent == null) {
            return true;
        }
        IntentNode parentNode = nodes.get(parentIntent);
        if (parentNode == null) {
            return true;
        }
        return parentNode.allowedSubIntents().contains(subIntent);
    }

    /**
     * 检查当前已填充槽位是否满足必需槽位依赖
     */
    public boolean isSlotsComplete(String intentName, Map<String, SlotValue> slots) {
        Set<String> missing = getMissingSlots(intentName, slots);
        return missing.isEmpty();
    }

    /**
     * 获取未填充的缺失槽位列表
     */
    public Set<String> getMissingSlots(String intentName, Map<String, SlotValue> slots) {
        IntentNode node = nodes.get(intentName);
        if (node == null) {
            return Collections.emptySet();
        }
        Set<String> missing = new HashSet<>();
        for (String req : node.requiredSlots()) {
            SlotValue sv = slots != null ? slots.get(req) : null;
            if (sv == null || sv.getValue() == null || String.valueOf(sv.getValue()).isBlank()) {
                missing.add(req);
            }
        }
        return missing;
    }

    public IntentNode getNode(String intentName) {
        return nodes.get(intentName);
    }
}
