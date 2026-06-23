package tech.qiantong.qknow.kmc.rag;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;
import tech.qiantong.qknow.module.kmc.service.sync.impl.LuceneServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LuceneWriterTest {

    private LuceneServiceImpl service;

    @TempDir
    Path tempDir;

    @Mock
    private KmcDocumentSegmentDO segmentDO;

    @Mock
    private KmcDocumentDO documentDO;

    @BeforeEach
    void setUp() throws Exception {
        service = new LuceneServiceImpl();
        java.lang.reflect.Field indexPathField = LuceneServiceImpl.class.getDeclaredField("indexPath");
        indexPathField.setAccessible(true);
        indexPathField.set(service, tempDir.toString());
    }

    @Test
    void concurrentWritesDoNotThrow() throws Exception {
        when(segmentDO.getId()).thenReturn(1L);
        when(segmentDO.getContent()).thenReturn("test content");
        when(segmentDO.getAnswer()).thenReturn(null);
        when(documentDO.getKnowledgeBaseId()).thenReturn(100L);
        when(documentDO.getId()).thenReturn(200L);
        when(documentDO.getName()).thenReturn("test-doc");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    service.saveSegment(segmentDO, documentDO);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void afterWriteDocumentIsSearchable() throws Exception {
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig();
        IndexWriter writer = new IndexWriter(directory, config);

        Document doc = new Document();
        doc.add(new StringField(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, "42", Field.Store.YES));
        doc.add(new TextField("content", "searchable content", Field.Store.YES));
        writer.addDocument(doc);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TermQuery query = new TermQuery(new Term(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, "42"));
        TopDocs results = searcher.search(query, 10);

        assertEquals(1, results.totalHits.value);

        reader.close();
        writer.close();
    }

    @Test
    void destroyClosesWriter() throws Exception {
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig();
        IndexWriter writer = new IndexWriter(directory, config);

        java.lang.reflect.Field writerField = LuceneServiceImpl.class.getDeclaredField("indexWriter");
        writerField.setAccessible(true);
        writerField.set(service, writer);

        assertTrue(writer.isOpen());

        service.destroy();

        assertFalse(writer.isOpen());
    }
}
