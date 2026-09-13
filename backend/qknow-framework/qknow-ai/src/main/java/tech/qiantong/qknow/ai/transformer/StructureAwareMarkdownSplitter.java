package tech.qiantong.qknow.ai.transformer;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知 Markdown 分块器 (Phase 14 核心组件)
 * <p>
 * 核心特性：
 * 1. Markdown 多级标题栈跟踪与面包屑注入 (Breadcrumb Header Injection，如 【章 > 节 > 小节】\n\n)
 * 2. 表格完整性原子保护与超长表格按行拆分时表头传播 (Table Header Propagation)
 * 3. 三反引号代码块 (```) 围栏原子性保护
 * 4. 英文句子切分负向断言防误切版本号 (如 3.5.8) 与小数
 * 5. 父子分块支持 (Parent-Child Splitting)
 * </p>
 *
 * @author qknow
 */
public class StructureAwareMarkdownSplitter extends TextSplitter {

    public static final String METADATA_BREADCRUMB = "breadcrumb";
    public static final String METADATA_PARENT_SEGMENT_ID = "parent_segment_id";
    public static final String METADATA_PARENT_CONTENT = "parent_content";
    public static final String METADATA_CHUNK_LEVEL = "chunk_level";
    public static final String CHUNK_LEVEL_PARENT = "parent";
    public static final String CHUNK_LEVEL_CHILD = "child";

    private final int maxChunkSize;
    private final int chunkOverlap;
    private final boolean injectBreadcrumb;

    // 匹配 Markdown 标题：^#{1,6}\s+(.+)
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    // 保护版本号和小数点：非数字前缀的点号，且后面不是数字
    private static final Pattern SAFE_SENTENCE_SPLIT = Pattern.compile("(?<!\\d)\\.(?!\\d)|[。！？!？\\n\\r]+");

    public StructureAwareMarkdownSplitter(int maxChunkSize, int chunkOverlap, boolean injectBreadcrumb) {
        this.maxChunkSize = maxChunkSize;
        this.chunkOverlap = chunkOverlap;
        this.injectBreadcrumb = injectBreadcrumb;
    }

    public StructureAwareMarkdownSplitter(int maxChunkSize, int chunkOverlap) {
        this(maxChunkSize, chunkOverlap, true);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            String text = doc.getText();
            if (StrUtil.isBlank(text)) {
                continue;
            }
            List<ChunkWithMeta> chunks = splitTextWithMeta(text);
            for (ChunkWithMeta c : chunks) {
                Map<String, Object> meta = new HashMap<>(doc.getMetadata());
                if (c.breadcrumb != null && !c.breadcrumb.isEmpty()) {
                    meta.put(METADATA_BREADCRUMB, c.breadcrumb);
                }
                result.add(Document.builder()
                        .id(UUID.randomUUID().toString())
                        .text(c.text)
                        .metadata(meta)
                        .build());
            }
        }
        return result;
    }

    @Override
    protected List<String> splitText(String text) {
        List<ChunkWithMeta> chunks = splitTextWithMeta(text);
        List<String> list = new ArrayList<>(chunks.size());
        for (ChunkWithMeta c : chunks) {
            list.add(c.text);
        }
        return list;
    }

    /**
     * 生成父子分块：父块保留宏观完备上下文，子块用于高精度向量检索
     */
    public List<Document> splitParentChild(List<Document> documents,
                                           int parentChunkSize, int childChunkSize,
                                           int parentOverlap, int childOverlap) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        StructureAwareMarkdownSplitter parentSplitter =
                new StructureAwareMarkdownSplitter(parentChunkSize, parentOverlap, injectBreadcrumb);
        StructureAwareMarkdownSplitter childSplitter =
                new StructureAwareMarkdownSplitter(childChunkSize, childOverlap, injectBreadcrumb);

        List<Document> result = new ArrayList<>();

        for (Document document : documents) {
            List<ChunkWithMeta> parentChunks = parentSplitter.splitTextWithMeta(document.getText());
            for (ChunkWithMeta parentChunk : parentChunks) {
                String parentId = UUID.randomUUID().toString();
                Map<String, Object> parentMetadata = new HashMap<>(document.getMetadata());
                parentMetadata.put(METADATA_CHUNK_LEVEL, CHUNK_LEVEL_PARENT);
                parentMetadata.put(METADATA_PARENT_SEGMENT_ID, "");
                if (parentChunk.breadcrumb != null && !parentChunk.breadcrumb.isEmpty()) {
                    parentMetadata.put(METADATA_BREADCRUMB, parentChunk.breadcrumb);
                }

                result.add(Document.builder()
                        .id(parentId)
                        .text(parentChunk.text)
                        .metadata(parentMetadata)
                        .build());

                // 对父块内容做精细 child 切分
                List<ChunkWithMeta> childChunks = childSplitter.splitTextWithMeta(parentChunk.text);
                for (ChunkWithMeta childChunk : childChunks) {
                    Map<String, Object> childMetadata = new HashMap<>(document.getMetadata());
                    childMetadata.put(METADATA_CHUNK_LEVEL, CHUNK_LEVEL_CHILD);
                    childMetadata.put(METADATA_PARENT_SEGMENT_ID, parentId);
                    childMetadata.put(METADATA_PARENT_CONTENT, parentChunk.text);
                    if (childChunk.breadcrumb != null && !childChunk.breadcrumb.isEmpty()) {
                        childMetadata.put(METADATA_BREADCRUMB, childChunk.breadcrumb);
                    } else if (parentChunk.breadcrumb != null && !parentChunk.breadcrumb.isEmpty()) {
                        childMetadata.put(METADATA_BREADCRUMB, parentChunk.breadcrumb);
                    }

                    result.add(Document.builder()
                        .id(UUID.randomUUID().toString())
                        .text(childChunk.text)
                        .metadata(childMetadata)
                        .build());
                }
            }
        }
        return result;
    }

    private static class ChunkWithMeta {
        String text;
        List<String> breadcrumb;

        ChunkWithMeta(String text, List<String> breadcrumb) {
            this.text = text;
            this.breadcrumb = breadcrumb;
        }
    }

    private enum BlockType {
        HEADING,
        CODE_BLOCK,
        TABLE,
        PARAGRAPH
    }

    private static class MarkdownBlock {
        BlockType type;
        int headingLevel;
        String content;
        List<String> breadcrumb;
        List<String> tableHeaders;
        List<String> tableRows;
    }

    private record HeadingEntry(int level, String title) {}

    /**
     * 核心切分逻辑：解析结构块后实施组装
     */
    private List<ChunkWithMeta> splitTextWithMeta(String text) {
        if (StrUtil.isBlank(text)) {
            return Collections.emptyList();
        }

        List<MarkdownBlock> blocks = parseBlocks(text);
        return assembleChunks(blocks);
    }

    /**
     * 阶段一：基于行扫描状态机解析 Markdown AST 结构块
     */
    private List<MarkdownBlock> parseBlocks(String text) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);

        Deque<HeadingEntry> headingStack = new ArrayDeque<>();
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();

        boolean inTable = false;
        List<String> tableLines = new ArrayList<>();

        StringBuilder paragraphBuffer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            // 1. 代码块围栏 (``` 或 ~~~)
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (inCodeBlock) {
                    codeBuffer.append(line).append("\n");
                    MarkdownBlock block = new MarkdownBlock();
                    block.type = BlockType.CODE_BLOCK;
                    block.content = codeBuffer.toString().trim();
                    block.breadcrumb = getCurrentBreadcrumb(headingStack);
                    blocks.add(block);
                    codeBuffer.setLength(0);
                    inCodeBlock = false;
                    continue;
                } else {
                    flushParagraph(paragraphBuffer, headingStack, blocks);
                    flushTable(tableLines, headingStack, blocks);
                    inCodeBlock = true;
                    codeBuffer.append(line).append("\n");
                    continue;
                }
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            // 2. 表格行检测
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flushParagraph(paragraphBuffer, headingStack, blocks);
                inTable = true;
                tableLines.add(line);
                continue;
            } else if (inTable) {
                flushTable(tableLines, headingStack, blocks);
                inTable = false;
            }

            // 3. 标题行检测
            Matcher headingMatcher = HEADING_PATTERN.matcher(trimmed);
            if (headingMatcher.matches()) {
                flushParagraph(paragraphBuffer, headingStack, blocks);

                int level = headingMatcher.group(1).length();
                String title = headingMatcher.group(2).trim();

                // 弹出更深或同级的标题
                while (!headingStack.isEmpty() && headingStack.peek().level >= level) {
                    headingStack.pop();
                }
                headingStack.push(new HeadingEntry(level, title));

                MarkdownBlock block = new MarkdownBlock();
                block.type = BlockType.HEADING;
                block.headingLevel = level;
                block.content = trimmed;
                block.breadcrumb = getCurrentBreadcrumb(headingStack);
                blocks.add(block);
                continue;
            }

            // 4. 普通段落
            if (StrUtil.isBlank(trimmed)) {
                flushParagraph(paragraphBuffer, headingStack, blocks);
            } else {
                paragraphBuffer.append(line).append("\n");
            }
        }

        // 清空残留缓冲区
        if (inCodeBlock && codeBuffer.length() > 0) {
            MarkdownBlock block = new MarkdownBlock();
            block.type = BlockType.CODE_BLOCK;
            block.content = codeBuffer.toString().trim();
            block.breadcrumb = getCurrentBreadcrumb(headingStack);
            blocks.add(block);
        }
        flushTable(tableLines, headingStack, blocks);
        flushParagraph(paragraphBuffer, headingStack, blocks);

        return blocks;
    }

    /**
     * 阶段二：装配分块，实施表头传播、面包屑前缀与长度控制
     */
    private List<ChunkWithMeta> assembleChunks(List<MarkdownBlock> blocks) {
        List<ChunkWithMeta> chunks = new ArrayList<>();

        for (MarkdownBlock block : blocks) {
            String prefix = "";
            if (injectBreadcrumb && block.breadcrumb != null && !block.breadcrumb.isEmpty()) {
                prefix = "【" + String.join(" > ", block.breadcrumb) + "】\n\n";
            }

            switch (block.type) {
                case HEADING:
                    // 标题块本身不单独作为切片，其语义已随面包屑注入各个正文子块
                    break;

                case CODE_BLOCK:
                    assembleCodeBlockChunks(chunks, prefix, block);
                    break;

                case TABLE:
                    assembleTableChunks(chunks, prefix, block);
                    break;

                case PARAGRAPH:
                default:
                    assembleParagraphChunks(chunks, prefix, block);
                    break;
            }
        }
        return chunks;
    }

    private void assembleTableChunks(List<ChunkWithMeta> chunks, String prefix, MarkdownBlock block) {
        String fullTable = block.content;
        if (prefix.length() + fullTable.length() <= maxChunkSize) {
            chunks.add(new ChunkWithMeta(prefix + fullTable, block.breadcrumb));
            return;
        }

        // 超长表格：提取前两行（表头行与分隔行）进行跨块复制
        if (block.tableHeaders == null || block.tableHeaders.isEmpty() || block.tableRows == null || block.tableRows.isEmpty()) {
            chunks.add(new ChunkWithMeta(prefix + fullTable, block.breadcrumb));
            return;
        }

        String headerPart = String.join("\n", block.tableHeaders) + "\n";
        StringBuilder current = new StringBuilder(prefix).append(headerPart);

        for (String row : block.tableRows) {
            if (current.length() + row.length() + 1 > maxChunkSize) {
                if (current.length() > (prefix + headerPart).length()) {
                    chunks.add(new ChunkWithMeta(current.toString().trim(), block.breadcrumb));
                    current = new StringBuilder(prefix).append(headerPart);
                }
            }
            current.append(row).append("\n");
        }

        if (current.length() > (prefix + headerPart).length()) {
            chunks.add(new ChunkWithMeta(current.toString().trim(), block.breadcrumb));
        }
    }

    private void assembleCodeBlockChunks(List<ChunkWithMeta> chunks, String prefix, MarkdownBlock block) {
        if (prefix.length() + block.content.length() <= maxChunkSize) {
            chunks.add(new ChunkWithMeta(prefix + block.content, block.breadcrumb));
            return;
        }

        // 超长代码块按行保守切分
        String[] lines = block.content.split("\n");
        StringBuilder sb = new StringBuilder(prefix);
        for (String line : lines) {
            if (sb.length() + line.length() + 1 > maxChunkSize) {
                if (sb.length() > prefix.length()) {
                    chunks.add(new ChunkWithMeta(sb.toString().trim(), block.breadcrumb));
                    sb = new StringBuilder(prefix);
                }
            }
            sb.append(line).append("\n");
        }
        if (sb.length() > prefix.length()) {
            chunks.add(new ChunkWithMeta(sb.toString().trim(), block.breadcrumb));
        }
    }

    private void assembleParagraphChunks(List<ChunkWithMeta> chunks, String prefix, MarkdownBlock block) {
        String content = block.content;
        if (prefix.length() + content.length() <= maxChunkSize) {
            chunks.add(new ChunkWithMeta(prefix + content, block.breadcrumb));
            return;
        }

        // 句级防误切递归切分
        String[] sentences = SAFE_SENTENCE_SPLIT.split(content);
        StringBuilder sb = new StringBuilder(prefix);
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() + trimmed.length() > maxChunkSize) {
                if (sb.length() > prefix.length()) {
                    chunks.add(new ChunkWithMeta(sb.toString().trim(), block.breadcrumb));
                    sb = new StringBuilder(prefix);
                }
            }
            sb.append(trimmed).append("。");
        }
        if (sb.length() > prefix.length()) {
            chunks.add(new ChunkWithMeta(sb.toString().trim(), block.breadcrumb));
        }
    }

    private void flushParagraph(StringBuilder sb, Deque<HeadingEntry> stack, List<MarkdownBlock> blocks) {
        if (sb.length() > 0) {
            MarkdownBlock block = new MarkdownBlock();
            block.type = BlockType.PARAGRAPH;
            block.content = sb.toString().trim();
            block.breadcrumb = getCurrentBreadcrumb(stack);
            blocks.add(block);
            sb.setLength(0);
        }
    }

    private void flushTable(List<String> tableLines, Deque<HeadingEntry> stack, List<MarkdownBlock> blocks) {
        if (tableLines.size() >= 2) {
            MarkdownBlock block = new MarkdownBlock();
            block.type = BlockType.TABLE;
            block.content = String.join("\n", tableLines);
            int headerRows = Math.min(2, tableLines.size());
            block.tableHeaders = new ArrayList<>(tableLines.subList(0, headerRows));
            block.tableRows = new ArrayList<>(tableLines.subList(headerRows, tableLines.size()));
            block.breadcrumb = getCurrentBreadcrumb(stack);
            blocks.add(block);
        } else if (!tableLines.isEmpty()) {
            MarkdownBlock block = new MarkdownBlock();
            block.type = BlockType.PARAGRAPH;
            block.content = String.join("\n", tableLines);
            block.breadcrumb = getCurrentBreadcrumb(stack);
            blocks.add(block);
        }
        tableLines.clear();
    }

    private List<String> getCurrentBreadcrumb(Deque<HeadingEntry> stack) {
        List<String> list = new ArrayList<>();
        Iterator<HeadingEntry> it = stack.descendingIterator();
        while (it.hasNext()) {
            list.add(it.next().title());
        }
        return list;
    }
}
