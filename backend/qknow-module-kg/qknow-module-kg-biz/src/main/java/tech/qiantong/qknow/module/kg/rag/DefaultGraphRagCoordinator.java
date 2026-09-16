package tech.qiantong.qknow.module.kg.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 神经符号知识图谱 3.0 默认检索门面实现 (GraphRAG 3.0 Default Coordinator)
 */
@Component
@ConditionalOnMissingBean(name = "customGraphRagCoordinator")
public class DefaultGraphRagCoordinator implements GraphRagCoordinator {

    @Override
    public List<GraphCausalEvidence> retrieveCausalPaths(Long workspaceId, List<String> seedEntities, int topK) {
        return Collections.emptyList();
    }

    @Override
    public List<CommunitySummaryEvidence> retrieveCommunitySummaries(Long workspaceId, String query, int topK) {
        return Collections.emptyList();
    }
}
