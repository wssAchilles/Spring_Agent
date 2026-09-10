package tech.qiantong.qknow.module.kb.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H4a: Agent prefers budgeted ragContext over raw StringBuilder concat.
 * Logic mirrored from KbAgentConfigServiceImpl injection branch.
 */
class AgentRagContextPreferenceTest {

    private static String chooseRecalled(List<RetrieveResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        String budgeted = results.get(0).getRagContext();
        if (budgeted != null && !budgeted.isBlank()) {
            return budgeted;
        }
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RetrieveResult r = results.get(i);
            contentBuilder.append("[来源 ").append(i + 1).append("] ")
                    .append(r.getDocumentName()).append("\n")
                    .append("内容：").append(r.getContent()).append("\n\n");
        }
        return contentBuilder.toString();
    }

    @Test
    @DisplayName("有 ragContext 时优先使用预算化上下文")
    void prefersBudgetedContext() {
        RetrieveResult first = new RetrieveResult();
        first.setDocumentName("人工智能.pdf");
        first.setContent("raw1");
        first.setRagContext("BUDGETED_CONTEXT_FROM_BUILDER");
        RetrieveResult second = new RetrieveResult();
        second.setContent("raw2");

        String recalled = chooseRecalled(List.of(first, second));
        assertEquals("BUDGETED_CONTEXT_FROM_BUILDER", recalled);
    }

    @Test
    @DisplayName("无 ragContext 时回退裸拼（兼容旧行为）")
    void fallsBackToRawConcat() {
        RetrieveResult first = new RetrieveResult();
        first.setDocumentName("doc.pdf");
        first.setContent("body");
        String recalled = chooseRecalled(List.of(first));
        assertTrue(recalled.contains("doc.pdf"));
        assertTrue(recalled.contains("body"));
    }
}
