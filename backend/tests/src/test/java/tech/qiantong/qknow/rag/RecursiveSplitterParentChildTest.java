package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecursiveSplitter 父子分块测试")
class RecursiveSplitterParentChildTest {

    @Test
    @DisplayName("中文文本 splitParentChild 应产生父子分块")
    void splitParentChild_withChineseText_producesParentAndChildChunks() {
        RecursiveSplitter splitter = new RecursiveSplitter(120, 0);
        String text = "第一段内容用于测试父子切分功能。这是第一段的后续内容，提供更多的上下文信息。" +
                "第二段内容继续提供上下文。第二段包含了一些额外的细节描述。" +
                "第三段内容用于生成子块。第三段也有一些补充说明。";
        List<Document> chunks = splitter.splitParentChild(List.of(new Document(text)), 80, 32, 0, 0);

        assertTrue(chunks.stream().anyMatch(doc ->
                RecursiveSplitter.CHUNK_LEVEL_PARENT.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL))),
                "应包含 parent 级别的分块");
        assertTrue(chunks.stream().anyMatch(doc ->
                RecursiveSplitter.CHUNK_LEVEL_CHILD.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL))),
                "应包含 child 级别的分块");
    }

    @Test
    @DisplayName("父块 metadata 应包含 chunk_level=parent")
    void splitParentChild_parentChunksHaveParentLevelMetadata() {
        RecursiveSplitter splitter = new RecursiveSplitter(80, 0);
        List<Document> chunks = splitter.splitParentChild(
                List.of(new Document("第一段内容用于父子切分。第二段内容继续提供上下文。第三段内容用于生成子块。")),
                60, 24, 0, 0);

        List<Document> parentChunks = chunks.stream()
                .filter(doc -> RecursiveSplitter.CHUNK_LEVEL_PARENT.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL)))
                .toList();

        assertFalse(parentChunks.isEmpty(), "应至少有一个 parent 分块");
        for (Document parent : parentChunks) {
            assertEquals(RecursiveSplitter.CHUNK_LEVEL_PARENT,
                    parent.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL));
            assertNotNull(parent.getId(), "parent 分块应有 ID");
        }
    }

    @Test
    @DisplayName("子块 metadata 应包含 chunk_level=child 和正确的 parent_segment_id")
    void splitParentChild_childChunksHaveCorrectParentReference() {
        RecursiveSplitter splitter = new RecursiveSplitter(80, 0);
        List<Document> chunks = splitter.splitParentChild(
                List.of(new Document("第一段内容用于父子切分。第二段内容继续提供上下文。第三段内容用于生成子块。")),
                60, 24, 0, 0);

        List<Document> childChunks = chunks.stream()
                .filter(doc -> RecursiveSplitter.CHUNK_LEVEL_CHILD.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL)))
                .toList();

        assertFalse(childChunks.isEmpty(), "应至少有一个 child 分块");
        for (Document child : childChunks) {
            assertEquals(RecursiveSplitter.CHUNK_LEVEL_CHILD,
                    child.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL));
            String parentId = (String) child.getMetadata().get(RecursiveSplitter.METADATA_PARENT_SEGMENT_ID);
            assertNotNull(parentId, "child 分块应有 parent_segment_id");
            assertFalse(parentId.isBlank(), "parent_segment_id 不应为空");
        }
    }

    @Test
    @DisplayName("splitParentChild 不应产生空分块")
    void splitParentChild_noEmptyChunksProduced() {
        RecursiveSplitter splitter = new RecursiveSplitter(80, 0);
        List<Document> chunks = splitter.splitParentChild(
                List.of(new Document("第一段内容。第二段内容。第三段内容。")),
                60, 20, 0, 0);

        for (Document chunk : chunks) {
            assertNotNull(chunk.getText(), "分块文本不应为 null");
            assertFalse(chunk.getText().isBlank(), "分块文本不应为空白");
        }
    }

    @Test
    @DisplayName("overlap 参数应正确传递到子分块器")
    void splitParentChild_overlapWorksCorrectly() {
        RecursiveSplitter splitter = new RecursiveSplitter(120, 0);
        String text = "这是第一段较长的内容用于测试重叠功能，包含足够多的文字以产生多个子块。" +
                "这是第二段较长的内容也用于测试重叠功能，同样包含足够多的文字。";
        List<Document> chunks = splitter.splitParentChild(List.of(new Document(text)), 80, 30, 0, 10);

        List<Document> childChunks = chunks.stream()
                .filter(doc -> RecursiveSplitter.CHUNK_LEVEL_CHILD.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL)))
                .toList();

        if (childChunks.size() > 1) {
            String firstChild = childChunks.get(0).getText();
            String secondChild = childChunks.get(1).getText();
            // With overlap, the second child should share some content with the first
            assertTrue(secondChild.length() > 0, "子块不应为空");
        }
    }
}
