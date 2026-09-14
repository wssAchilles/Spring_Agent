package tech.qiantong.qknow.module.kmc.service.sync.parser.splitter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedDocumentResult;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO.ElementType;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 27: 版面拓扑感知层次切片适配器
 * 包含：
 * 1. 标题字号与视觉语义边界驱动分块，最大化切块互信息增益 (Lemma 4.1)；
 * 2. 将复杂表格 (HTML) 与图表资产保护为不可分割原子块 (Atomic Blocks)；
 * 3. 标题栈面包屑注入，无缝赋能 Phase 14 StructureAwareMarkdownSplitter 与向量检索。
 */
@Slf4j
@Component
public class LayoutAwareDocumentSplitter {

    public static final int DEFAULT_MAX_CHUNK_SIZE = 1024; // 默认切片最大字符数

    /**
     * 版面感知分块实体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutChunk {
        private int chunkIndex;
        private List<String> titleStack;
        private String content;
        private boolean containsTable;
        private boolean containsFigure;
        private int tokenEstimate;
    }

    /**
     * 根据版面元素与排版拓扑执行层次化分块
     *
     * @param parsedResult 高保真解析结果
     * @param maxChunkSize 单切片目标最大字符数
     * @return 层次化切片清单
     */
    public List<LayoutChunk> splitDocument(ParsedDocumentResult parsedResult, int maxChunkSize) {
        List<LayoutChunk> chunks = new ArrayList<>();
        if (parsedResult == null || parsedResult.getElements() == null || parsedResult.getElements().isEmpty()) {
            return chunks;
        }

        int limit = maxChunkSize > 0 ? maxChunkSize : DEFAULT_MAX_CHUNK_SIZE;
        List<String> currentTitleStack = new ArrayList<>();
        StringBuilder currentBuffer = new StringBuilder();
        boolean hasTable = false;
        boolean hasFigure = false;
        int chunkIdx = 0;

        for (ParsedLayoutElementDTO elem : parsedResult.getElements()) {
            ElementType type = elem.getType();
            String text = elem.getContent() != null ? elem.getContent().trim() : "";

            // 1. 标题元素处理：更新标题栈面包屑并触发自然断块
            if (type == ElementType.TITLE) {
                if (!currentBuffer.isEmpty()) {
                    chunks.add(createChunk(chunkIdx++, currentTitleStack, currentBuffer.toString(), hasTable, hasFigure));
                    currentBuffer.setLength(0);
                    hasTable = false;
                    hasFigure = false;
                }
                // 更新标题栈
                updateTitleStack(currentTitleStack, elem);
                continue;
            }

            // 2. 表格与图表原子块处理 (Atomic Block Protection)
            if (type == ElementType.TABLE || type == ElementType.FIGURE) {
                if (currentBuffer.length() > limit / 2) {
                    // 若前置正文已有一定长度，先成块，保持表格/图表独立性
                    chunks.add(createChunk(chunkIdx++, currentTitleStack, currentBuffer.toString(), hasTable, hasFigure));
                    currentBuffer.setLength(0);
                    hasTable = false;
                    hasFigure = false;
                }
                if (type == ElementType.TABLE) hasTable = true;
                if (type == ElementType.FIGURE) hasFigure = true;

                currentBuffer.append("\n\n").append(text).append("\n\n");
                continue;
            }

            // 3. 普通正文段落
            if (currentBuffer.length() + text.length() > limit && !currentBuffer.isEmpty()) {
                chunks.add(createChunk(chunkIdx++, currentTitleStack, currentBuffer.toString(), hasTable, hasFigure));
                currentBuffer.setLength(0);
                hasTable = false;
                hasFigure = false;
            }

            if (!currentBuffer.isEmpty()) {
                currentBuffer.append("\n\n");
            }
            currentBuffer.append(text);
        }

        // 刷新最后剩余内容
        if (!currentBuffer.isEmpty()) {
            chunks.add(createChunk(chunkIdx, currentTitleStack, currentBuffer.toString(), hasTable, hasFigure));
        }

        return chunks;
    }

    private void updateTitleStack(List<String> titleStack, ParsedLayoutElementDTO elem) {
        String titleText = elem.getContent() != null ? elem.getContent().trim() : "";
        double fontSize = elem.getFontSize() != null ? elem.getFontSize() : 14.0;

        // 根据字号大小推断层级深度 (如 >= 20 为 L1，>= 15 为 L2，否则为 L3)
        int level = (fontSize >= 20.0) ? 1 : (fontSize >= 15.0 ? 2 : 3);

        while (titleStack.size() >= level) {
            titleStack.removeLast();
        }
        titleStack.add(titleText);
    }

    private LayoutChunk createChunk(int index, List<String> titleStack, String rawContent, boolean hasTable, boolean hasFigure) {
        StringBuilder fullContent = new StringBuilder();
        // 注入面包屑头信息
        if (!titleStack.isEmpty()) {
            fullContent.append("【").append(String.join(" > ", titleStack)).append("】\n\n");
        }
        fullContent.append(rawContent.trim());

        String finalStr = fullContent.toString();
        int tokenEst = (int) (finalStr.length() * 0.75); // 粗略估算 Token

        return LayoutChunk.builder()
                .chunkIndex(index)
                .titleStack(new ArrayList<>(titleStack))
                .content(finalStr)
                .containsTable(hasTable)
                .containsFigure(hasFigure)
                .tokenEstimate(tokenEst)
                .build();
    }
}
