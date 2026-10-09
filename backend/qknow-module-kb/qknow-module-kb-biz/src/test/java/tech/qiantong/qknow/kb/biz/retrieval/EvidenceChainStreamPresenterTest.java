package tech.qiantong.qknow.kb.biz.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.EvidenceChainStreamPresenter;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeek 思考链深度融合与多模态证据链流式呈现器契约测试
 */
public class EvidenceChainStreamPresenterTest {

    private final EvidenceChainStreamPresenter presenter = new EvidenceChainStreamPresenter();

    private static RetrieveResult createTestChunk(String id, String docName, String content, Double score) {
        RetrieveResult r = new RetrieveResult();
        r.setId(id);
        r.setDocumentName(docName);
        r.setContent(content);
        r.setScore(score);
        return r;
    }

    @Test
    @DisplayName("测试双轨流式事件帧类型与载荷封装")
    void testStreamFrames() {
        EvidenceChainStreamPresenter.StreamEventFrame thinking =
                EvidenceChainStreamPresenter.StreamEventFrame.thinking("正在分析用户提问并规划检索路径...");
        assertEquals(EvidenceChainStreamPresenter.FrameType.THINKING, thinking.type());
        assertTrue(thinking.payload().contains("正在分析用户提问"));

        EvidenceChainStreamPresenter.StreamEventFrame answer =
                EvidenceChainStreamPresenter.StreamEventFrame.answer("根据相关规章制度，");
        assertEquals(EvidenceChainStreamPresenter.FrameType.ANSWER, answer.type());
        assertEquals("根据相关规章制度，", answer.payload());
    }

    @Test
    @DisplayName("测试引文角标规范化与越界幻觉角标过滤")
    void testCitationNormalizationAndFiltering() {
        RetrieveResult c1 = createTestChunk("c1", "安全条例.pdf", "机房严禁吸烟与明火", 0.95);
        RetrieveResult c2 = createTestChunk("c2", "网络准入.docx", "外部访客须申请临时通行码", 0.88);
        List<RetrieveResult> availableChunks = List.of(c1, c2); // 仅有 2 个有效切片

        // 原始文本包含各种格式的角标：[^1]、[2]、以及越界的【3】和 [^99]
        String rawAnswer = "进入机房必须严格遵守消防规范[^1]，且外部人员须持通行码[2]。此外还有未核实条款【3】以及虚假条款[^99]。";

        String normalized = presenter.normalizeAndValidateCitations(rawAnswer, availableChunks);

        assertNotNull(normalized);
        assertTrue(normalized.contains("[^1]"), "合法角标1应保留并规范为[^1]");
        assertTrue(normalized.contains("[^2]"), "合法角标2应保留并规范为[^2]");
        assertFalse(normalized.contains("【3】") || normalized.contains("[^3]"), "越界角标3必须被剔除");
        assertFalse(normalized.contains("[^99]"), "捏造角标99必须被剔除");
    }

    @Test
    @DisplayName("测试没有切片时正文捏造角标全部被过滤")
    void testZeroChunks_AllCitationsFiltered() {
        String raw = "这是没有召回切片的纯回答[^1]，模型擅自生成了[2]。";
        String normalized = presenter.normalizeAndValidateCitations(raw, List.of());
        assertFalse(normalized.contains("[^1]"));
        assertFalse(normalized.contains("[2]"));
        assertEquals("这是没有召回切片的纯回答，模型擅自生成了。", normalized.trim());
    }

    @Test
    @DisplayName("测试渲染结构化 Markdown 证据链卡片")
    void testRenderEvidenceChainCard() {
        RetrieveResult c1 = createTestChunk("c1", "2024财报.pdf", "研发支出4.5亿元，同比增长12.8%", 0.952);
        RetrieveResult c2 = createTestChunk("c2", "技术白皮书.docx", "系统支持超低延迟流式打字机交互", 0.887);

        String card = presenter.renderEvidenceChainCard(List.of(c1, c2));

        assertNotNull(card);
        assertTrue(card.contains("知识依据与核验证据链"));
        assertTrue(card.contains("**[^1]** 《2024财报.pdf》"));
        assertTrue(card.contains("95.20%"));
        assertTrue(card.contains("研发支出4.5亿元"));
        assertTrue(card.contains("**[^2]** 《技术白皮书.docx》"));
    }

    @Test
    @DisplayName("测试生成不可变审计凭单与 SHA-256 自验真")
    void testReceiptAndIntegrity() {
        RetrieveResult c1 = createTestChunk("c1", "手册.pdf", "内容片段", 0.9);
        String ans = "这是最终答案文本。";
        String think = "深度思考链推理步骤1...步骤2...";

        EvidenceChainStreamPresenter.EvidenceChainReceipt receipt =
                presenter.generateReceipt(ans, think, List.of(c1), 150L);

        assertNotNull(receipt);
        assertEquals(1, receipt.totalCitedCount());
        assertEquals(ans, receipt.fullAnswer());
        assertEquals(think, receipt.fullThinking());
        assertNotNull(receipt.receiptHash());

        // 验证 SHA-256 常量时间自验真通过
        assertTrue(receipt.verifyIntegrity(), "自验真必须通过");
    }
}
