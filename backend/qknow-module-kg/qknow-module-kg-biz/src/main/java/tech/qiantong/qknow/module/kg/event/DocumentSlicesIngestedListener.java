package tech.qiantong.qknow.module.kg.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kg.rag.GraphRagCoordinator;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 异步切片入图事件驱动监听器 (消除跨存储长事务死锁)
 */
@Component
public class DocumentSlicesIngestedListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentSlicesIngestedListener.class);

    private final GraphRagCoordinator graphRagCoordinator;
    private Consumer<DocumentSlicesIngestedEvent> asyncHook;

    private final ExecutorService kgVirtualExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            Thread.ofVirtual().name("kg-async-ingest-", 0).factory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public DocumentSlicesIngestedListener(GraphRagCoordinator graphRagCoordinator) {
        this.graphRagCoordinator = graphRagCoordinator;
    }

    public DocumentSlicesIngestedListener() {
        this(null);
    }

    public void setAsyncHook(Consumer<DocumentSlicesIngestedEvent> hook) {
        this.asyncHook = hook;
    }

    /**
     * 接收切片入库提交后事件，异步构建图谱
     */
    @EventListener
    public void onDocumentSlicesIngested(DocumentSlicesIngestedEvent event) {
        if (event == null) return;
        kgVirtualExecutor.submit(() -> {
            try {
                log.info("[KG Event] 异步消费切片入图事件: docId={}, slices={}", event.documentId(),
                        event.segmentIds() != null ? event.segmentIds().size() : 0);
                if (graphRagCoordinator != null) {
                    graphRagCoordinator.onSlicesIngestedAsync(
                            event.workspaceId(), event.documentId(), event.segmentIds(), event.segmentTexts());
                }
                if (asyncHook != null) {
                    asyncHook.accept(event);
                }
            } catch (Exception e) {
                log.error("[KG Event] 异步图谱增量更新异常: {}", e.getMessage(), e);
            }
        });
    }
}
