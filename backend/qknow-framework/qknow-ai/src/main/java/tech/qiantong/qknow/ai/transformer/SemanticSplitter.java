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
     * 按中文/英文句子结束符拆分文本
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : text.toCharArray()) {
            current.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == '\n') {
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
     * 将句子按断点分组，并遵守 maxChunkSize / minChunkSize 约束
     */
    private List<String> groupIntoChunks(List<String> sentences, List<Integer> breakPoints) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int lastBreak = 0;

        for (int bp : breakPoints) {
            StringBuilder group = new StringBuilder();
            for (int i = lastBreak; i < bp; i++) {
                group.append(sentences.get(i));
            }

            if (currentChunk.length() + group.length() > maxChunkSize
                    && currentChunk.length() >= minChunkSize) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }

            for (int i = lastBreak; i < bp; i++) {
                currentChunk.append(sentences.get(i));
            }
            lastBreak = bp;
        }

        for (int i = lastBreak; i < sentences.size(); i++) {
            currentChunk.append(sentences.get(i));
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
