package tech.qiantong.qknow.module.kmc.service.rag.guard;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * 包装并拦截 EmbeddingModel，执行写入前的实时维度与单位超球面范数防御。
 * 单次计算，就地拦截，避免写入前和写入中执行双重 Embedding 造成延迟与计算成本翻倍。
 *
 * @author qknow
 */
public class GuardedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final EmbeddingDimensionGuard guard;

    public GuardedEmbeddingModel(EmbeddingModel delegate, EmbeddingDimensionGuard guard) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate EmbeddingModel cannot be null");
        }
        if (guard == null) {
            throw new IllegalArgumentException("EmbeddingDimensionGuard cannot be null");
        }
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.call(request);
        if (response != null && response.getResults() != null) {
            for (Embedding embedding : response.getResults()) {
                if (embedding != null && embedding.getOutput() != null) {
                    guard.validateBeforeWrite(embedding.getOutput());
                }
            }
        }
        return response;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = delegate.embed(text);
        if (vector != null) {
            guard.validateBeforeWrite(vector);
        }
        return vector;
    }

    @Override
    public float[] embed(Document document) {
        float[] vector = delegate.embed(document);
        if (vector != null) {
            guard.validateBeforeWrite(vector);
        }
        return vector;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = delegate.embed(texts);
        if (vectors != null) {
            for (float[] v : vectors) {
                if (v != null) {
                    guard.validateBeforeWrite(v);
                }
            }
        }
        return vectors;
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
        List<float[]> vectors = delegate.embed(documents, options, batchingStrategy);
        if (vectors != null) {
            for (float[] v : vectors) {
                if (v != null) {
                    guard.validateBeforeWrite(v);
                }
            }
        }
        return vectors;
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        EmbeddingResponse response = delegate.embedForResponse(texts);
        if (response != null && response.getResults() != null) {
            for (Embedding embedding : response.getResults()) {
                if (embedding != null && embedding.getOutput() != null) {
                    guard.validateBeforeWrite(embedding.getOutput());
                }
            }
        }
        return response;
    }

    @Override
    public int dimensions() {
        int dim = guard.getEffectiveDimension();
        if (dim > 0) {
            return dim;
        }
        return delegate.dimensions();
    }

    public EmbeddingModel getDelegate() {
        return delegate;
    }

    public EmbeddingDimensionGuard getGuard() {
        return guard;
    }
}
