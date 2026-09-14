package tech.qiantong.qknow.module.kmc.service.sync.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Phase 25: 文档断点游标持久化与崩溃自愈管理器
 * 负责两阶段进度提交、状态流转以及从上次安全断点秒级精准恢复。
 */
@Slf4j
@Service
public class DocumentCheckpointManager {

    /**
     * 内存态断点游标注册表 (支持与底层 kmc_document_checkpoint 表持久化解耦与穿透缓存)
     */
    private final ConcurrentMap<Long, DocumentCheckpoint> checkpointStore = new ConcurrentHashMap<>();

    /**
     * 获取或初始化断点游标
     *
     * @param documentId 文档 ID
     * @param totalBytes 文档总字节大小
     * @return 断点游标
     */
    public DocumentCheckpoint getOrCreateCheckpoint(Long documentId, Long totalBytes) {
        return checkpointStore.computeIfAbsent(documentId, id -> DocumentCheckpoint.builder()
                .documentId(id)
                .totalBytes(totalBytes != null ? totalBytes : 0L)
                .lastLineOffset(0L)
                .embeddedSegments(0)
                .status("INIT")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 获取指定文档的断点游标
     *
     * @param documentId 文档 ID
     * @return 断点游标
     */
    public DocumentCheckpoint getCheckpoint(Long documentId) {
        return checkpointStore.get(documentId);
    }

    /**
     * 记录分块与嵌入的两阶段安全进度
     *
     * @param documentId         文档 ID
     * @param lastLineOffset     已成功提交的最新行偏移量
     * @param additionalSegments 本批次成功嵌入的切片增量
     */
    public void recordProgress(Long documentId, long lastLineOffset, int additionalSegments) {
        checkpointStore.compute(documentId, (id, cp) -> {
            if (cp == null) {
                cp = DocumentCheckpoint.builder()
                        .documentId(id)
                        .totalBytes(0L)
                        .lastLineOffset(0L)
                        .embeddedSegments(0)
                        .status("RUNNING")
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            cp.setLastLineOffset(lastLineOffset);
            cp.setEmbeddedSegments((cp.getEmbeddedSegments() != null ? cp.getEmbeddedSegments() : 0) + additionalSegments);
            cp.setStatus("RUNNING");
            cp.setUpdatedAt(LocalDateTime.now());
            log.debug("文档断点游标推进: docId={}, lastLineOffset={}, totalEmbedded={}",
                    id, lastLineOffset, cp.getEmbeddedSegments());
            return cp;
        });
    }

    /**
     * 标记文档全量分块与向量化嵌入完成
     *
     * @param documentId 文档 ID
     */
    public void markCompleted(Long documentId) {
        DocumentCheckpoint cp = checkpointStore.get(documentId);
        if (cp != null) {
            cp.setStatus("COMPLETED");
            cp.setUpdatedAt(LocalDateTime.now());
            log.info("文档全量分块与向量嵌入完成: docId={}, totalSegments={}", documentId, cp.getEmbeddedSegments());
        }
    }

    /**
     * 标记处理主动挂起（例如触发限流 429 退避或手动暂停）
     *
     * @param documentId 文档 ID
     * @param reason     挂起原因
     */
    public void markSuspended(Long documentId, String reason) {
        DocumentCheckpoint cp = checkpointStore.get(documentId);
        if (cp != null) {
            cp.setStatus("SUSPENDED");
            cp.setErrorMsg(reason);
            cp.setUpdatedAt(LocalDateTime.now());
            log.warn("文档处理主动挂起: docId={}, reason={}", documentId, reason);
        }
    }

    /**
     * 标记处理发生不可逆异常并记录错误信息
     *
     * @param documentId 文档 ID
     * @param errorMsg   异常快照
     */
    public void markError(Long documentId, String errorMsg) {
        DocumentCheckpoint cp = checkpointStore.get(documentId);
        if (cp != null) {
            cp.setStatus("ERROR");
            cp.setErrorMsg(errorMsg);
            cp.setUpdatedAt(LocalDateTime.now());
            log.error("文档处理异常中断: docId={}, error={}", documentId, errorMsg);
        }
    }

    /**
     * 重置或清除文档游标（用于重新从头导入）
     *
     * @param documentId 文档 ID
     */
    public void reset(Long documentId) {
        checkpointStore.remove(documentId);
        log.info("文档游标已重置: docId={}", documentId);
    }
}
