package tech.qiantong.qknow.hermes.tool.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTransformToolTest {

    private final TextTransformToolFunction tool = new TextTransformToolFunction();

    @Test
    void uppercase() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello world");
        req.setOperation("uppercase");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("HELLO WORLD", resp.getResult());
    }

    @Test
    void lowercase() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("HELLO WORLD");
        req.setOperation("lowercase");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("hello world", resp.getResult());
    }

    @Test
    void trim() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("  hello  ");
        req.setOperation("trim");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("hello", resp.getResult());
    }

    @Test
    void substring() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello world");
        req.setOperation("substring");
        req.setParam(5);

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("hello", resp.getResult());
    }

    @Test
    void substringDefaultParam() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hi");
        req.setOperation("substring");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("hi", resp.getResult());
    }

    @Test
    void length() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello");
        req.setOperation("length");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("5", resp.getResult());
    }

    @Test
    void unsupportedOperationReturnsError() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello");
        req.setOperation("reverse");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNotNull(resp.getError());
        assertTrue(resp.getError().contains("不支持"));
    }

    @Test
    void nullTextReturnsError() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText(null);
        req.setOperation("uppercase");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNotNull(resp.getError());
    }

    @Test
    void nullOperationReturnsError() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello");
        req.setOperation(null);

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNotNull(resp.getError());
    }

    @Test
    void chineseTextUppercase() {
        TextTransformToolFunction.Request req = new TextTransformToolFunction.Request();
        req.setText("hello 你好");
        req.setOperation("uppercase");

        TextTransformToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("HELLO 你好", resp.getResult());
    }
}
