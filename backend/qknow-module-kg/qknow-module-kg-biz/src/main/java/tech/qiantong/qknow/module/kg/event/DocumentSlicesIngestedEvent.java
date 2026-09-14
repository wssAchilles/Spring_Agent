package tech.qiantong.qknow.module.kg.event;

import java.util.List;

/**
 * 知识切片写入完成领域事件 (用于异步事件驱动图谱更新)
 */
public record DocumentSlicesIngestedEvent(
        Long workspaceId,
        Long documentId,
        List<String> segmentIds,
        List<String> segmentTexts,
        long timestamp
) {
    public DocumentSlicesIngestedEvent(Long documentId, List<String> segmentIds, List<String> segmentTexts) {
        this(1L, documentId, segmentIds, segmentTexts, System.currentTimeMillis());
    }

    public DocumentSlicesIngestedEvent(String docId, List<String> segmentIds, List<String> segmentTexts) {
        this(1L, parseDocId(docId), segmentIds, segmentTexts, System.currentTimeMillis());
    }

    private static Long parseDocId(String docId) {
        try {
            return Long.parseLong(docId.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 1L;
        }
    }
}
