package tech.qiantong.qknow.ai.transformer;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GeneralSplitterBugTest {

    private List<String> split(GeneralSplitter splitter, String text) {
        return splitter.apply(List.of(new Document(text))).stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    @Test
    void singleChunkText_shouldNotThrowException() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 1024, 100);
        String text = "This is a single chunk of text";
        List<String> result = split(splitter, text);
        assertEquals(1, result.size());
        assertEquals("This is a single chunk of text", result.get(0));
    }

    @Test
    void twoChunkText_overlapShouldWork() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 1024, 5);
        String text = "First chunk\nSecond chunk";
        List<String> result = split(splitter, text);
        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("First chunk"));
        assertTrue(result.get(1).contains("Second chunk"));
    }

    @Test
    void multiChunkText_overlapShouldWork() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 1024, 10);
        String text = "First\nSecond\nThird\nFourth";
        List<String> result = split(splitter, text);
        assertEquals(4, result.size());
        assertTrue(result.get(0).contains("First"));
        assertTrue(result.get(1).contains("Second"));
        assertTrue(result.get(2).contains("Third"));
        assertTrue(result.get(3).contains("Fourth"));
    }

    @Test
    void emptyStringInput_shouldReturnEmptyList() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 1024, 100);
        String text = "";
        List<String> result = split(splitter, text);
        assertTrue(result.isEmpty());
    }

    @Test
    void singleChunkWithOverlap_shouldNotThrowException() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 20, 5);
        String text = "short text";
        List<String> result = split(splitter, text);
        assertEquals(1, result.size());
    }

    @Test
    void splitByLength_withOverlap_shouldWork() {
        GeneralSplitter splitter = new GeneralSplitter("\\n", 10, 3);
        String text = "abcdefghijklmnopqrstuvwxyz";
        List<String> result = split(splitter, text);
        assertTrue(result.size() > 1);
        for (String chunk : result) {
            assertTrue(chunk.length() <= 13);
        }
    }
}
