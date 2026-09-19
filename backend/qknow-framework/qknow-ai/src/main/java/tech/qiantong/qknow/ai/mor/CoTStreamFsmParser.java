package tech.qiantong.qknow.ai.mor;

import java.util.function.Consumer;

/**
 * 零内存拷贝非阻塞字符级有限状态机 (FSM) 流式思考链解析器
 * 严格分离 <think>...</think> 内部推演与正文输出，消除正则回溯与卡顿
 */
public class CoTStreamFsmParser {

    public enum StreamChunkType {
        THINKING,   // 思考推演流片段 (推向前端思考抽屉)
        CONTENT     // 最终正文内容片段 (推向前端打字机)
    }

    public record ParsedChunk(StreamChunkType type, String text) {}

    private enum State {
        IN_CONTENT,         // 处于正文流 (默认起始状态)
        PARSING_START_TAG,  // 正在逐字符匹配 "<think>"
        IN_THINKING,        // 处于思考流中
        PARSING_END_TAG     // 正在逐字符匹配 "</think>"
    }

    private State state = State.IN_CONTENT;
    private final StringBuilder tagBuffer = new StringBuilder(16);
    private final StringBuilder thinkingAccumulator = new StringBuilder(1024);
    private final StringBuilder contentAccumulator = new StringBuilder(1024);

    private static final String START_TAG = "<think>";
    private static final String END_TAG = "</think>";

    /**
     * 逐 Chunk 输入并触发非阻塞回调
     */
    public synchronized void feed(String chunk, Consumer<ParsedChunk> consumer) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        StringBuilder outputBuffer = new StringBuilder();

        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);

            switch (state) {
                case IN_CONTENT:
                    if (c == '<') {
                        tagBuffer.setLength(0);
                        tagBuffer.append(c);
                        state = State.PARSING_START_TAG;
                    } else {
                        outputBuffer.append(c);
                        contentAccumulator.append(c);
                    }
                    break;

                case PARSING_START_TAG:
                    tagBuffer.append(c);
                    if (START_TAG.startsWith(tagBuffer.toString())) {
                        if (START_TAG.equals(tagBuffer.toString())) {
                            // 匹配到完整的 <think>，若前序积压了正文，先输出
                            if (outputBuffer.length() > 0) {
                                consumer.accept(new ParsedChunk(StreamChunkType.CONTENT, outputBuffer.toString()));
                                outputBuffer.setLength(0);
                            }
                            tagBuffer.setLength(0);
                            state = State.IN_THINKING;
                        }
                    } else {
                        // 匹配失败，非 <think>，还原为正文
                        outputBuffer.append(tagBuffer);
                        contentAccumulator.append(tagBuffer);
                        tagBuffer.setLength(0);
                        state = State.IN_CONTENT;
                    }
                    break;

                case IN_THINKING:
                    if (c == '<') {
                        tagBuffer.setLength(0);
                        tagBuffer.append(c);
                        state = State.PARSING_END_TAG;
                    } else {
                        outputBuffer.append(c);
                        thinkingAccumulator.append(c);
                    }
                    break;

                case PARSING_END_TAG:
                    tagBuffer.append(c);
                    if (END_TAG.startsWith(tagBuffer.toString())) {
                        if (END_TAG.equals(tagBuffer.toString())) {
                            // 匹配到完整的 </think>，思考结束，刷出思考流
                            if (outputBuffer.length() > 0) {
                                consumer.accept(new ParsedChunk(StreamChunkType.THINKING, outputBuffer.toString()));
                                outputBuffer.setLength(0);
                            }
                            tagBuffer.setLength(0);
                            state = State.IN_CONTENT;
                        }
                    } else {
                        // 匹配失败，非 </think>，还原为思考内容
                        outputBuffer.append(tagBuffer);
                        thinkingAccumulator.append(tagBuffer);
                        tagBuffer.setLength(0);
                        state = State.IN_THINKING;
                    }
                    break;
            }
        }

        // 刷新当前 chunk 的输出回调
        if (outputBuffer.length() > 0) {
            StreamChunkType type = (state == State.IN_THINKING || state == State.PARSING_END_TAG)
                    ? StreamChunkType.THINKING : StreamChunkType.CONTENT;
            consumer.accept(new ParsedChunk(type, outputBuffer.toString()));
        }
    }

    /**
     * 流结束时的收尾，将残留的 tagBuffer 吐出
     */
    public synchronized void finish(Consumer<ParsedChunk> consumer) {
        if (tagBuffer.length() > 0) {
            if (state == State.PARSING_START_TAG || state == State.IN_CONTENT) {
                contentAccumulator.append(tagBuffer);
                if (consumer != null) {
                    consumer.accept(new ParsedChunk(StreamChunkType.CONTENT, tagBuffer.toString()));
                }
            } else if (state == State.PARSING_END_TAG || state == State.IN_THINKING) {
                thinkingAccumulator.append(tagBuffer);
                if (consumer != null) {
                    consumer.accept(new ParsedChunk(StreamChunkType.THINKING, tagBuffer.toString()));
                }
            }
            tagBuffer.setLength(0);
        }
    }

    public String getFullThinkingProcess() {
        return thinkingAccumulator.toString();
    }

    public String getFullContent() {
        return contentAccumulator.toString();
    }

    public String getContentAccumulatorText() {
        return getFullContent();
    }

    public String getThinkingAccumulatorText() {
        return getFullThinkingProcess();
    }
}
