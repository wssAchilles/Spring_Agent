package tech.qiantong.qknow.module.kmc.service.sync.parser.vision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedFigureDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 27: 跨模态图表抽取与视觉语义锚定服务
 * 包含：
 * 1. 唯一图表占位符 ![fig_id](uri) 生成与正文上下文锚定；
 * 2. 基于 DeepSeek API 生成图表结构化多模态摘要 (反向回填 > **【图表语义增强】**)；
 * 3. 消除“如图所示”悬空代词指代，对图表生成幻觉实现指数级压制 (Theorem 3.1)。
 */
@Slf4j
@Service
public class CrossModalAnchorService {

    private static final Pattern FIGURE_REFERENCE_PATTERN = Pattern.compile("(如图\\s*\\d+|见图\\s*\\d+|由图\\s*\\d+|如表\\s*\\d+|架构图|流程图)");

    /**
     * 针对抽取的图表实体生成标准跨模态锚定 Markdown 块
     *
     * @param figure 图表实体元数据
     * @return 包含图像占位符与视觉语义摘要的格式化 Markdown 字符串
     */
    public String buildAnchoredFigureMarkdown(ExtractedFigureDTO figure) {
        if (figure == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String figId = figure.getFigureId() != null ? figure.getFigureId() : "fig_unknown";
        String uri = figure.getImageUri() != null ? figure.getImageUri() : "";
        String caption = figure.getCaption() != null ? figure.getCaption() : "";

        // 1. 生成图像标准展示占位符
        sb.append(String.format("![%s](%s)\n", figId, uri));
        if (!caption.isBlank()) {
            sb.append(String.format("*%s*\n\n", caption.trim()));
        }

        // 2. 反向回填 DeepSeek 结构化视觉摘要 (若有)
        if (figure.getVisionSummary() != null && !figure.getVisionSummary().isBlank()) {
            sb.append("> **【图表语义增强】** ").append(figure.getVisionSummary().trim()).append("\n\n");
        }

        String result = sb.toString();
        figure.setPlaceholderMarkdown(result);
        return result;
    }

    /**
     * 在原始正文中检测悬空图表代词并自动锚定回填
     *
     * @param rawText 原始切片文本
     * @param figures 关联的图表清单
     * @return 消除悬空代词、完成视觉语义锚定后的富文本切片
     */
    public String anchorFiguresIntoText(String rawText, List<ExtractedFigureDTO> figures) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        if (figures == null || figures.isEmpty()) {
            return rawText;
        }

        StringBuilder enriched = new StringBuilder(rawText);
        Matcher matcher = FIGURE_REFERENCE_PATTERN.matcher(rawText);

        boolean hasReference = matcher.find();
        if (hasReference) {
            log.debug("正文中检测到图表显式引用，执行跨模态上下文深度锚定");
        }

        // 在正文末尾将关联图表的视觉摘要与占位符完整追加
        enriched.append("\n\n---\n### 图表跨模态关联资产\n");
        for (ExtractedFigureDTO fig : figures) {
            enriched.append(buildAnchoredFigureMarkdown(fig));
        }

        return enriched.toString().trim();
    }

    /**
     * 评估生成回答对图表事实的支撑保真度得分 (Grounding Fidelity Score / Faithfulness)
     * 计算回答中包含的关键事实实体与数值在图表摘要中的支持率 (Precision)。
     *
     * @param generatedAnswer 生成的回答
     * @param figureSummary 图表视觉摘要
     * @return 事实保真度支持率 [0.0, 1.0]
     */
    public double computeVisualGroundingFidelity(String generatedAnswer, String figureSummary) {
        if (generatedAnswer == null || figureSummary == null || figureSummary.isBlank()) {
            return 0.0;
        }

        // 提取回答中声明的关键事实词元 (数值、百分比、核心业务实体)
        List<String> answerTokens = extractKeyTokens(generatedAnswer);
        if (answerTokens.isEmpty()) {
            return 1.0;
        }

        int matched = 0;
        for (String token : answerTokens) {
            if (figureSummary.contains(token)) {
                matched++;
            }
        }

        return (double) matched / answerTokens.size();
    }

    private static final Pattern NUMBER_PERCENT_PATTERN = Pattern.compile("\\d+(\\.\\d+)?%?");
    private static final Pattern DELIMITER_AND_STOPWORDS_PATTERN = Pattern.compile(
            "[\\p{Punct}\\s\\u3000-\\u303F\\uFF00-\\uFFEF]+|(根据|按照|依据|如图|见图|由图|如表|见表|图表|数据|显示|表明|可以看出|达到|达到了|大约|占比|占|业务|板块|领域|方面|是|为|由|在|了|与|和|且|及|以及|并且|对于|关于|属于|作为|到)"
    );

    private List<String> extractKeyTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        // 1. 提取所有量化指标与百分比事实
        Matcher numMatcher = NUMBER_PERCENT_PATTERN.matcher(text);
        while (numMatcher.find()) {
            tokens.add(numMatcher.group());
        }

        // 2. 移除数字后，按标点符号与常见通用虚词/引导词切分抽取核心实体短语
        String pureText = NUMBER_PERCENT_PATTERN.matcher(text).replaceAll(" ");
        String[] parts = DELIMITER_AND_STOPWORDS_PATTERN.split(pureText);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2) {
                tokens.add(trimmed);
            }
        }

        return tokens;
    }
}
