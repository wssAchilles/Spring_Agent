package tech.qiantong.qknow.ai.transformer;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecursiveSplitterParentChildTest {

    @Test
    void createsParentAndChildChunksWithMapping() {
        RecursiveSplitter splitter = new RecursiveSplitter(80, 0);
        List<Document> chunks = splitter.splitParentChild(
                List.of(new Document("第一段内容用于父子切分。第二段内容继续提供上下文。第三段内容用于生成子块。")),
                32,
                12,
                0,
                0);

        assertTrue(chunks.stream().anyMatch(doc ->
                RecursiveSplitter.CHUNK_LEVEL_PARENT.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL))));
        assertTrue(chunks.stream().anyMatch(doc ->
                RecursiveSplitter.CHUNK_LEVEL_CHILD.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL))));
        assertTrue(chunks.stream()
                .filter(doc -> RecursiveSplitter.CHUNK_LEVEL_CHILD.equals(doc.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL)))
                .allMatch(doc -> doc.getMetadata().get(RecursiveSplitter.METADATA_PARENT_SEGMENT_ID) != null));
    }
}
