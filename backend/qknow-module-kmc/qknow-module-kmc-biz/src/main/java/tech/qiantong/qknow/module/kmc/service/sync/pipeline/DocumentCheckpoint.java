package tech.qiantong.qknow.module.kmc.service.sync.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Phase 25: 文档断点游标值对象
 * 记录超长文档分块与向量化嵌入两阶段推进进度，支持崩溃秒级恢复与零重复消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCheckpoint {

    /**
     * 关联文档主键 ID
     */
    private Long documentId;

    /**
     * 文档总字节大小
     */
    private Long totalBytes;

    /**
     * 上次安全已提交的行偏移量 / 游标偏移量
     */
    private Long lastLineOffset;

    /**
     * 已完成嵌入并入库的切片累计数量
     */
    private Integer embeddedSegments;

    /**
     * 处理状态：INIT, RUNNING, COMPLETED, SUSPENDED, ERROR
     */
    private String status;

    /**
     * 异常或崩溃时的错误快照信息
     */
    private String errorMsg;

    /**
     * 检查点创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 检查点最后更新时间
     */
    private LocalDateTime updatedAt;
}
