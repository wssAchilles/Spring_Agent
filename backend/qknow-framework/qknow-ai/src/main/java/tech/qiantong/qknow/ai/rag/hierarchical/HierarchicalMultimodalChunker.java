package tech.qiantong.qknow.ai.rag.hierarchical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.rag.hierarchical.model.MultimodalDocumentChunk;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AST 语法树感知层次化多模态文档切片器 (定理 1.1)
 * 1. 识别 Markdown 语法层级 (H1~H6)，维护 Breadcrumb 面包屑路径栈；
 * 2. 实施表格结构完整性保护 (Table Integrity Preservation)，大表格跨块切分时强制逐块复制 Markdown 表头与列定义；
 * 3. 实施多模态图文强空间锚定 (Multimodal Anchoring)，将图片/图表与章节标题路径及紧邻文本段落强绑定；
 * 4. 输出纯 Java 21 Record 格式的父子分层切片集合，支持 O(1) 向上父级上下文无损展开。
 */
@Component
public class HierarchicalMultimodalChunker {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalMultimodalChunker.class);

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");
    private static final int DEFAULT_MAX_TABLE_ROWS_PER_CHUNK = 5;

    /**
     * 对 Markdown 复合文档进行层次化多模态解析与切分
     *
     * @param documentId 文档唯一标识
     * @param markdown   文档 Markdown 原始内容
     * @return 层次化切片清单 (包含父切片、文本段落、表格切片与图片锚定切片)
     */
    public List<MultimodalDocumentChunk> chunkDocument(String documentId, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<MultimodalDocumentChunk> chunks = new ArrayList<>();
        String[] lines = markdown.split("\\r?\\n");

        List<String> currentBreadcrumbs = new ArrayList<>();
        String currentParentId = "ROOT-" + documentId;

        // 初始根父节点
        chunks.add(new MultimodalDocumentChunk(
                currentParentId,
                null,
                MultimodalDocumentChunk.ChunkType.SECTION_PARENT,
                "Document Root: " + documentId,
                List.of(documentId),
                Map.of("level", "0"),
                false, 0, 0, null, null, null
        ));

        int lineIdx = 0;
        int chunkSeq = 1;
        StringBuilder paragraphBuffer = new StringBuilder();

        while (lineIdx < lines.length) {
            String line = lines[lineIdx];
            String trimmed = line.trim();

            // 1. 标题行检测 (H1 ~ H6)
            Matcher headerMatcher = HEADER_PATTERN.matcher(trimmed);
            if (headerMatcher.matches()) {
                // 刷新之前的段落
                flushParagraph(paragraphBuffer, chunks, documentId, chunkSeq++, currentParentId, currentBreadcrumbs);

                int level = headerMatcher.group(1).length();
                String title = headerMatcher.group(2).trim();

                // 更新面包屑层级栈
                while (currentBreadcrumbs.size() >= level) {
                    currentBreadcrumbs.remove(currentBreadcrumbs.size() - 1);
                }
                currentBreadcrumbs.add(title);

                currentParentId = "SEC-" + documentId + "-" + chunkSeq++;
                chunks.add(new MultimodalDocumentChunk(
                        currentParentId,
                        "ROOT-" + documentId,
                        MultimodalDocumentChunk.ChunkType.SECTION_PARENT,
                        line,
                        List.copyOf(currentBreadcrumbs),
                        Map.of("level", String.valueOf(level), "title", title),
                        false, 0, 0, null, null, null
                ));

                lineIdx++;
                continue;
            }

            // 2. 表格检测 (以 | 开头且后续包含 | --- | 分隔符)
            if (isTableStart(lines, lineIdx)) {
                // 刷新之前的段落
                flushParagraph(paragraphBuffer, chunks, documentId, chunkSeq++, currentParentId, currentBreadcrumbs);

                // 解析完整表格行
                List<String> tableLines = new ArrayList<>();
                while (lineIdx < lines.length && lines[lineIdx].trim().startsWith("|")) {
                    tableLines.add(lines[lineIdx].trim());
                    lineIdx++;
                }

                // 表格处理：完整性保护与跨块表头复制
                chunkSeq = processTable(tableLines, chunks, documentId, chunkSeq, currentParentId, currentBreadcrumbs);
                continue;
            }

            // 3. 多模态图片检测 (![caption](url))
            Matcher imgMatcher = IMAGE_PATTERN.matcher(trimmed);
            if (imgMatcher.find()) {
                // 刷新之前的段落
                String precedingText = paragraphBuffer.toString().trim();
                flushParagraph(paragraphBuffer, chunks, documentId, chunkSeq++, currentParentId, currentBreadcrumbs);

                String caption = imgMatcher.group(1);
                String uri = imgMatcher.group(2);

                String imgChunkId = "IMG-" + documentId + "-" + chunkSeq++;
                chunks.add(new MultimodalDocumentChunk(
                        imgChunkId,
                        currentParentId,
                        MultimodalDocumentChunk.ChunkType.IMAGE_ANCHORED,
                        line,
                        List.copyOf(currentBreadcrumbs),
                        Map.of(
                                "caption", caption != null ? caption : "",
                                "uri", uri != null ? uri : "",
                                "precedingSnippet", precedingText
                        ),
                        false, 0, 0,
                        uri, caption, null
                ));

                lineIdx++;
                continue;
            }

            // 4. 普通正文处理
            if (trimmed.isEmpty()) {
                flushParagraph(paragraphBuffer, chunks, documentId, chunkSeq++, currentParentId, currentBreadcrumbs);
            } else {
                if (!paragraphBuffer.isEmpty()) {
                    paragraphBuffer.append("\n");
                }
                paragraphBuffer.append(line);
            }
            lineIdx++;
        }

        // 刷新最后未完成的段落
        flushParagraph(paragraphBuffer, chunks, documentId, chunkSeq, currentParentId, currentBreadcrumbs);

        log.info("[层次化切片完成] 文档 ID: {}, 原始行数: {}, 生成分层切片数: {}", documentId, lines.length, chunks.size());
        return chunks;
    }

    private boolean isTableStart(String[] lines, int idx) {
        if (idx >= lines.length || !lines[idx].trim().startsWith("|")) {
            return false;
        }
        // 检查下一行是否是表头分隔线 (如 |---|---|)
        return idx + 1 < lines.length && lines[idx + 1].trim().matches("^\\|([\\s-:]+\\|)+$");
    }

    private int processTable(
            List<String> tableLines,
            List<MultimodalDocumentChunk> chunks,
            String documentId,
            int startSeq,
            String parentId,
            List<String> breadcrumbs
    ) {
        if (tableLines.size() < 2) {
            return startSeq;
        }

        String headerLine1 = tableLines.get(0);
        String headerLine2 = tableLines.get(1);
        List<String> dataRows = tableLines.subList(2, tableLines.size());

        // 若数据行较少，作为单一原子完整表格保存
        if (dataRows.size() <= DEFAULT_MAX_TABLE_ROWS_PER_CHUNK) {
            String tableContent = String.join("\n", tableLines);
            chunks.add(new MultimodalDocumentChunk(
                    "TBL-" + documentId + "-" + startSeq++,
                    parentId,
                    MultimodalDocumentChunk.ChunkType.TABLE,
                    tableContent,
                    List.copyOf(breadcrumbs),
                    Map.of("totalRows", String.valueOf(dataRows.size())),
                    false,
                    1,
                    dataRows.size(),
                    null, null, null
            ));
            return startSeq;
        }

        // 跨块切分：强制逐块复制 Markdown 表头 (Header Replication)
        int rowIdx = 0;
        while (rowIdx < dataRows.size()) {
            int endIdx = Math.min(rowIdx + DEFAULT_MAX_TABLE_ROWS_PER_CHUNK, dataRows.size());
            List<String> chunkRows = dataRows.subList(rowIdx, endIdx);

            StringBuilder tableChunkBuilder = new StringBuilder();
            tableChunkBuilder.append(headerLine1).append("\n");
            tableChunkBuilder.append(headerLine2).append("\n");
            for (String r : chunkRows) {
                tableChunkBuilder.append(r).append("\n");
            }

            chunks.add(new MultimodalDocumentChunk(
                    "TBL-" + documentId + "-" + startSeq++,
                    parentId,
                    MultimodalDocumentChunk.ChunkType.TABLE,
                    tableChunkBuilder.toString().trim(),
                    List.copyOf(breadcrumbs),
                    Map.of(
                            "isPartial", "true",
                            "rowStart", String.valueOf(rowIdx + 1),
                            "rowEnd", String.valueOf(endIdx)
                    ),
                    true,
                    rowIdx + 1,
                    endIdx,
                    null, null, null
            ));

            rowIdx = endIdx;
        }

        return startSeq;
    }

    private void flushParagraph(
            StringBuilder buffer,
            List<MultimodalDocumentChunk> chunks,
            String documentId,
            int seq,
            String parentId,
            List<String> breadcrumbs
    ) {
        if (buffer.isEmpty()) {
            return;
        }
        String content = buffer.toString().trim();
        if (!content.isEmpty()) {
            chunks.add(new MultimodalDocumentChunk(
                    "TXT-" + documentId + "-" + seq,
                    parentId,
                    MultimodalDocumentChunk.ChunkType.TEXT_PARAGRAPH,
                    content,
                    List.copyOf(breadcrumbs),
                    Map.of(),
                    false, 0, 0, null, null, null
            ));
        }
        buffer.setLength(0);
    }

    /**
     * 向上无损展开父级上下文 (消除代词悬空与结构截断)
     */
    public String expandParentContext(MultimodalDocumentChunk childChunk, Map<String, MultimodalDocumentChunk> chunkMap) {
        if (childChunk == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!childChunk.breadcrumbPath().isEmpty()) {
            sb.append("【所属章节路径】: ").append(String.join(" > ", childChunk.breadcrumbPath())).append("\n\n");
        }

        if (childChunk.parentChunkId() != null && chunkMap != null) {
            MultimodalDocumentChunk parent = chunkMap.get(childChunk.parentChunkId());
            if (parent != null && parent.chunkType() == MultimodalDocumentChunk.ChunkType.SECTION_PARENT) {
                sb.append("【章节总起说明】: ").append(parent.content()).append("\n\n");
            }
        }

        sb.append(childChunk.content());
        return sb.toString();
    }
}
