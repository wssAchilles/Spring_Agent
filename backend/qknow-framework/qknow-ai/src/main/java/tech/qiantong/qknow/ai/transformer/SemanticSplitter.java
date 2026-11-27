package tech.qiantong.qknow.ai.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Embedding 语义相似度的文本分块器
 * <p>
 * 将文本按句子拆分，计算相邻句子的向量相似度，
 * 在相似度低于阈值处作为断点，将语义相近的句子聚合为一个分块。
 * </p>
 *
 * @author qknow
 */
public class SemanticSplitter extends TextSplitter {

    private final EmbeddingModel embeddingModel;
    private final int maxChunkSize;
    private final int minChunkSize;
    private final double similarityThreshold;

    public SemanticSplitter(EmbeddingModel embeddingModel, int maxChunkSize,
                            int minChunkSize, double similarityThreshold) {
        this.embeddingModel = embeddingModel;
        this.maxChunkSize = maxChunkSize;
        this.minChunkSize = minChunkSize;
        this.similarityThreshold = similarityThreshold;
    }

    public SemanticSplitter(EmbeddingModel embeddingModel, int maxChunkSize) {
        this(embeddingModel, maxChunkSize, 100, 0.5);
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> sentences = splitIntoSentences(text);
        if (sentences.size() <= 1) {
            return sentences;
        }

        List<float[]> embeddings = computeEmbeddings(sentences);
        if (embeddings.isEmpty()) {
            return sentences;
        }

        List<Integer> breakPoints = findBreakPoints(embeddings);
        return groupIntoChunks(sentences, breakPoints);
    }

    /**
     * 按中文/英文句子结束符拆分文本（保护数字小数点与版本号如 v2.2.1 / 3.11 不被误切碎）
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            current.append(c);
            boolean isSentenceEnd = false;
            if (c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '!' || c == '?' || c == '\n') {
                isSentenceEnd = true;
            } else if (c == '.') {
                boolean prevDigit = (i > 0 && Character.isDigit(text.charAt(i - 1)));
                boolean nextDigit = (i + 1 < len && Character.isDigit(text.charAt(i + 1)));
                // 小数点或版本号中间的点号不视为句末，仅在非数字相邻点号处切分
                if (!(prevDigit && nextDigit)) {
                    isSentenceEnd = true;
                }
            }
            if (isSentenceEnd) {
                String s = current.toString().trim();
                if (!s.isEmpty()) {
                    sentences.add(s);
                }
                current.setLength(0);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            sentences.add(remaining);
        }

        return sentences;
    }

    /**
     * 批量计算句子向量
     */
    private List<float[]> computeEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        int batchSize = 10;

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                EmbeddingRequest request = new EmbeddingRequest(batch, null);
                EmbeddingResponse response = embeddingModel.call(request);

                for (var output : response.getResults()) {
                    float[] floats = output.getOutput();
                    embeddings.add(floats);
                }
            } catch (Exception e) {
                // 向量计算失败时返回空列表，调用方回退到原始分句
                return new ArrayList<>();
            }
        }

        return embeddings;
    }

    /**
     * 基于相邻句子余弦相似度寻找断点
     */
    private List<Integer> findBreakPoints(List<float[]> embeddings) {
        List<Integer> breakPoints = new ArrayList<>();

        for (int i = 0; i < embeddings.size() - 1; i++) {
            double similarity = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
            if (similarity < similarityThreshold) {
                breakPoints.add(i + 1);
            }
        }

        return breakPoints;
    }

    /**
     * 将句子按语义断点分组，并在单组累积超过 maxChunkSize 时执行硬边界强制切分
     */
    private List<String> groupIntoChunks(List<String> sentences, List<Integer> breakPoints) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        java.util.Set<Integer> breakSet = new java.util.HashSet<>(breakPoints);

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            boolean hitSemanticBreak = breakSet.contains(i);
            boolean exceedsMax = currentChunk.length() > 0
                    && (currentChunk.length() + 1 + sentence.length() > maxChunkSize);

            if ((hitSemanticBreak && currentChunk.length() >= minChunkSize) || exceedsMax) {
                String chunk = currentChunk.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                currentChunk.setLength(0);
            }

            // 单句自身超长时按 maxChunkSize 步进安全切分
            if (sentence.length() > maxChunkSize && currentChunk.length() == 0) {
                int pos = 0;
                while (pos < sentence.length()) {
                    int end = Math.min(pos + maxChunkSize, sentence.length());
                    String slice = sentence.substring(pos, end).trim();
                    if (!slice.isEmpty()) {
                        chunks.add(slice);
                    }
                    pos = end;
                }
                continue;
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(' ');
            }
            currentChunk.append(sentence);
        }

        if (currentChunk.length() > 0) {
            String chunk = currentChunk.toString().trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
