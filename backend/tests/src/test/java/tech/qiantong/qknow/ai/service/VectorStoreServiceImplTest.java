package tech.qiantong.qknow.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import tech.qiantong.qknow.ai.service.impl.VectorStoreServiceImpl;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreServiceImplTest {

    private VectorStoreServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new VectorStoreServiceImpl();
        DataSource dataSource = Mockito.mock(DataSource.class);
        Field field = VectorStoreServiceImpl.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(service, dataSource);
    }

    @Test
    void sameEmbeddingModelReturnsCachedInstance() {
        EmbeddingModel model = new EmbeddingModelA();

        VectorStore first = service.getVectorStore(model);
        VectorStore second = service.getVectorStore(model);

        assertSame(first, second);
    }

    @Test
    void concurrentCallsReturnSameInstance() throws Exception {
        EmbeddingModel model = new EmbeddingModelA();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<VectorStore>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                VectorStore result = null;
                for (int j = 0; j < 10; j++) {
                    result = service.getVectorStore(model);
                }
                return result;
            }));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        VectorStore first = futures.get(0).get();
        for (Future<VectorStore> future : futures) {
            assertSame(first, future.get());
        }
    }

    @Test
    void differentModelsReturnDifferentInstances() {
        EmbeddingModel modelA = new EmbeddingModelA();
        EmbeddingModel modelB = new EmbeddingModelB();

        VectorStore storeA = service.getVectorStore(modelA);
        VectorStore storeB = service.getVectorStore(modelB);

        assertNotSame(storeA, storeB);
    }

    private static class EmbeddingModelA implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return null;
        }

        @Override
        public float[] embed(Document document) {
            return new float[0];
        }
    }

    private static class EmbeddingModelB implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return null;
        }

        @Override
        public float[] embed(Document document) {
            return new float[0];
        }
    }
}
