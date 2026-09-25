package tech.qiantong.qknow.hermes.rag.causal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AST 语法树感知父子分块与跨页表格完整性保护分块器 (第一道工业防线)
 * <p>
 * 1. 构建 Document -> Section -> Paragraph / Table 四级树状拓扑流形，维护双向指针；
 * 2. 跨页表格原子性保护与表头 Schema 复制继承，消除代词悬挂与字段撕裂；
 * 3. 命中叶子节点时通过父指针向上无损恢复上下文大纲与面包屑。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class HierarchicalAstChunker {

    public enum ChunkLevel {
        DOCUMENT,
        SECTION,
        PARAGRAPH,
        TABLE
    }

    public record AstChunkNode(
            String id,
            ChunkLevel level,
            String title,
            String content,
            String parentId,
            List<String> childrenIds,
            Map<String, Object> metadata
    ) {}

    public record ExpandedChunkContext(
            String chunkId,
            String primaryContent,
            String parentSectionTitle,
            String fullBreadcrumb,
            String enrichedContext,
            List<String> tableHeaders
    ) {}

    private final Map<String, AstChunkNode> nodeStore = new ConcurrentHashMap<>();
    private final Map<String, String> docRoots = new ConcurrentHashMap<>();

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|(.+)\\|$");
    private static final Pattern TABLE_DIVIDER_PATTERN = Pattern.compile("^\\|([\\s-:]+\\|)+$");

    /**
     * 解析 Markdown/复合文本为层次化 AST 分块流形
     *
     * @param docId           文档唯一 ID
     * @param markdownContent 原始 Markdown 文本
     * @param maxTableRows    每个表格切片容纳的最大数据行数 (用于表格分块测试，默认可设为 3)
     * @return 根节点
     */
    public AstChunkNode parseAndBuildTree(String docId, String markdownContent, int maxTableRows) {
        Objects.requireNonNull(docId, "docId 不能为空");
        if (markdownContent == null || markdownContent.isBlank()) {
            String rootId = "doc-" + docId;
            AstChunkNode emptyRoot = new AstChunkNode(rootId, ChunkLevel.DOCUMENT, "Empty Document", "", null, List.of(), Map.of());
            nodeStore.put(rootId, emptyRoot);
            docRoots.put(docId, rootId);
            return emptyRoot;
        }

        int safeMaxRows = maxTableRows > 0 ? maxTableRows : 5;
        String rootId = "doc-" + docId;
        List<String> rootChildren = new ArrayList<>();
        AstChunkNode rootNode = new AstChunkNode(rootId, ChunkLevel.DOCUMENT, "Document: " + docId, "", null, rootChildren, new HashMap<>());
        nodeStore.put(rootId, rootNode);
        docRoots.put(docId, rootId);

        String[] rawLines = markdownContent.split("\r?\n");
        Deque<SectionFrame> sectionStack = new ArrayDeque<>();
        sectionStack.push(new SectionFrame(rootId, 0, "Document: " + docId, rootChildren));

        List<String> tableBuffer = new ArrayList<>();
        List<String> textBuffer = new ArrayList<>();
        int chunkSeq = 1;

        for (String line : rawLines) {
            String trimmed = line.trim();

            // 检测标题行
            Matcher headingMatcher = HEADING_PATTERN.matcher(trimmed);
            if (headingMatcher.matches()) {
                // 先刷空已有的文本或表格缓存
                chunkSeq = flushBuffers(sectionStack.peek(), textBuffer, tableBuffer, chunkSeq, safeMaxRows);

                int depth = headingMatcher.group(1).length();
                String headingTitle = headingMatcher.group(2).trim();

                // 弹出栈中深度大于等于当前标题的章节
                while (sectionStack.size() > 1 && sectionStack.peek().depth >= depth) {
                    sectionStack.pop();
                }

                SectionFrame parentSection = sectionStack.peek();
                String sectionId = parentSection.id + "-sec-" + chunkSeq++;
                parentSection.childrenIds.add(sectionId);

                List<String> secChildren = new ArrayList<>();
                AstChunkNode secNode = new AstChunkNode(
                        sectionId,
                        ChunkLevel.SECTION,
                        headingTitle,
                        headingTitle,
                        parentSection.id,
                        secChildren,
                        Map.of("depth", depth)
                );
                nodeStore.put(sectionId, secNode);
                sectionStack.push(new SectionFrame(sectionId, depth, headingTitle, secChildren));
                continue;
            }

            // 检测表格行
            if (TABLE_ROW_PATTERN.matcher(trimmed).matches()) {
                if (!textBuffer.isEmpty()) {
                    chunkSeq = flushTextBuffer(sectionStack.peek(), textBuffer, chunkSeq);
                }
                tableBuffer.add(trimmed);
                continue;
            } else {
                if (!tableBuffer.isEmpty()) {
                    chunkSeq = flushTableBuffer(sectionStack.peek(), tableBuffer, chunkSeq, safeMaxRows);
                }
            }

            // 普通段落行
            if (!trimmed.isEmpty()) {
                textBuffer.add(line);
            } else {
                if (!textBuffer.isEmpty()) {
                    chunkSeq = flushTextBuffer(sectionStack.peek(), textBuffer, chunkSeq);
                }
            }
        }

        // 最终刷空剩余缓存
        flushBuffers(sectionStack.peek(), textBuffer, tableBuffer, chunkSeq, safeMaxRows);

        return nodeStore.get(rootId);
    }

    private int flushBuffers(SectionFrame currentSection, List<String> textBuffer, List<String> tableBuffer, int seq, int maxRows) {
        if (!textBuffer.isEmpty()) {
            seq = flushTextBuffer(currentSection, textBuffer, seq);
        }
        if (!tableBuffer.isEmpty()) {
            seq = flushTableBuffer(currentSection, tableBuffer, seq, maxRows);
        }
        return seq;
    }

    private int flushTextBuffer(SectionFrame currentSection, List<String> textBuffer, int seq) {
        String paragraphContent = String.join("\n", textBuffer).trim();
        textBuffer.clear();
        if (paragraphContent.isEmpty()) {
            return seq;
        }

        String paraId = currentSection.id + "-p-" + seq++;
        currentSection.childrenIds.add(paraId);
        AstChunkNode paraNode = new AstChunkNode(
                paraId,
                ChunkLevel.PARAGRAPH,
                currentSection.title + " (段落)",
                paragraphContent,
                currentSection.id,
                List.of(),
                Map.of("charCount", paragraphContent.length())
        );
        nodeStore.put(paraId, paraNode);
        return seq;
    }

    private int flushTableBuffer(SectionFrame currentSection, List<String> tableBuffer, int seq, int maxRows) {
        if (tableBuffer.isEmpty()) {
            return seq;
        }

        List<String> lines = new ArrayList<>(tableBuffer);
        tableBuffer.clear();

        // 提取表头
        List<String> headers = new ArrayList<>();
        int dataStartIndex = 0;

        if (!lines.isEmpty()) {
            headers = parseTableRow(lines.get(0));
            dataStartIndex = 1;
            if (lines.size() > 1 && TABLE_DIVIDER_PATTERN.matcher(lines.get(1).trim()).matches()) {
                dataStartIndex = 2;
            }
        }

        List<String> dataRows = lines.subList(dataStartIndex, lines.size());
        if (dataRows.isEmpty()) {
            // 纯空表格或仅表头
            String tableId = currentSection.id + "-tbl-" + seq++;
            currentSection.childrenIds.add(tableId);
            AstChunkNode tblNode = new AstChunkNode(
                    tableId,
                    ChunkLevel.TABLE,
                    currentSection.title + " [表格]",
                    String.join("\n", lines),
                    currentSection.id,
                    List.of(),
                    Map.of("headers", headers, "rowCount", 0)
            );
            nodeStore.put(tableId, tblNode);
            return seq;
        }

        // 分块处理，每块最多 maxRows 行数据，强制复制表头并序列化为行级键值对
        int totalRows = dataRows.size();
        int chunkCount = (int) Math.ceil((double) totalRows / maxRows);

        for (int c = 0; c < chunkCount; c++) {
            int start = c * maxRows;
            int end = Math.min(start + maxRows, totalRows);
            List<String> subRows = dataRows.subList(start, end);

            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("【表格上下文所属章节】：").append(currentSection.title).append("\n");
            contentBuilder.append("【表格表头元数据 (Schema)】：[").append(String.join(", ", headers)).append("]\n");
            contentBuilder.append("【结构化数据行 (切片 ").append(c + 1).append("/").append(chunkCount).append(")】：\n");

            for (int r = 0; r < subRows.size(); r++) {
                List<String> cells = parseTableRow(subRows.get(r));
                contentBuilder.append("  - [行记录 ").append(start + r + 1).append("]: ");
                List<String> kvList = new ArrayList<>();
                for (int col = 0; col < headers.size(); col++) {
                    String h = headers.get(col);
                    String v = col < cells.size() ? cells.get(col) : "";
                    kvList.add(h + "=" + v);
                }
                contentBuilder.append(String.join(", ", kvList)).append("\n");
            }

            String chunkContent = contentBuilder.toString().trim();
            String tableChunkId = currentSection.id + "-tbl-" + seq + "-part" + (c + 1);
            currentSection.childrenIds.add(tableChunkId);

            Map<String, Object> meta = new HashMap<>();
            meta.put("headers", headers);
            meta.put("partIndex", c + 1);
            meta.put("totalParts", chunkCount);
            meta.put("startRow", start + 1);
            meta.put("endRow", end);
            meta.put("inheritedSchema", true);

            AstChunkNode tblChunkNode = new AstChunkNode(
                    tableChunkId,
                    ChunkLevel.TABLE,
                    currentSection.title + " [表格分块 " + (c + 1) + "/" + chunkCount + "]",
                    chunkContent,
                    currentSection.id,
                    List.of(),
                    meta
            );
            nodeStore.put(tableChunkId, tblChunkNode);
        }

        seq++;
        return seq;
    }

    private List<String> parseTableRow(String rowLine) {
        String clean = rowLine.trim();
        if (clean.startsWith("|")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("|")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        String[] parts = clean.split("\\|");
        List<String> res = new ArrayList<>();
        for (String p : parts) {
            res.add(p.trim());
        }
        return res;
    }

    /**
     * 命中叶子切片时向上无损恢复父级章节大纲与背景上下文
     *
     * @param chunkId 目标切片 ID
     * @return 展开后的富上下文
     */
    public ExpandedChunkContext expandContext(String chunkId) {
        AstChunkNode target = nodeStore.get(chunkId);
        if (target == null) {
            throw new IllegalArgumentException("未找到切片节点: " + chunkId);
        }

        List<String> breadcrumbs = new ArrayList<>();
        String currId = target.parentId();
        String parentSectionTitle = "根文档";

        while (currId != null && nodeStore.containsKey(currId)) {
            AstChunkNode parent = nodeStore.get(currId);
            breadcrumbs.add(parent.title());
            if (parent.level() == ChunkLevel.SECTION && "根文档".equals(parentSectionTitle)) {
                parentSectionTitle = parent.title();
            }
            currId = parent.parentId();
        }
        Collections.reverse(breadcrumbs);
        breadcrumbs.add(target.title());
        String fullBreadcrumb = String.join(" > ", breadcrumbs);

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) target.metadata().getOrDefault("headers", List.of());

        StringBuilder enriched = new StringBuilder();
        enriched.append("【层级导航】: ").append(fullBreadcrumb).append("\n");
        if (target.level() == ChunkLevel.TABLE) {
            enriched.append("【表格类型】: 跨页原子自解释表格切片\n");
            if (!headers.isEmpty()) {
                enriched.append("【继承表头】: ").append(headers).append("\n");
            }
        }
        enriched.append("【切片正文】:\n").append(target.content());

        return new ExpandedChunkContext(
                chunkId,
                target.content(),
                parentSectionTitle,
                fullBreadcrumb,
                enriched.toString(),
                headers
        );
    }

    public AstChunkNode getNode(String id) {
        return nodeStore.get(id);
    }

    public void clear() {
        nodeStore.clear();
        docRoots.clear();
    }

    private record SectionFrame(String id, int depth, String title, List<String> childrenIds) {}
}
