package tech.qiantong.qknow.module.kb.service.streaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 高可靠 SSE 滑动重发环形缓冲区 (L1 内存 RingBuffer + 断点续传)
 * 基于 W3C Last-Event-ID 规范与单调递增 sequence_id 实现断线无感补发
 */
@Slf4j
@Component
public class SseReplayWindowBuffer {

    private static final int DEFAULT_CAPACITY = 128;
    private static final long DEFAULT_TTL_MS = 60_000L; // 60秒无活动自动过期

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SseFrame {
        private long sequenceId;
        private String data;
        private long timestamp;
    }

    /**
     * 单个会话的环形滑动重发窗口
     */
    public static class SessionRingBuffer {
        private final int capacity;
        private final SseFrame[] buffer;
        private int head = 0;
        private int size = 0;
        private long lastAccessTime;
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

        public SessionRingBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new SseFrame[capacity];
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void append(long sequenceId, String data) {
            rwLock.writeLock().lock();
            try {
                SseFrame frame = SseFrame.builder()
                        .sequenceId(sequenceId)
                        .data(data)
                        .timestamp(System.currentTimeMillis())
                        .build();

                int insertIndex = (head + size) % capacity;
                if (size < capacity) {
                    buffer[insertIndex] = frame;
                    size++;
                } else {
                    // 缓冲区已满，覆盖最旧帧并将 head 前移
                    buffer[head] = frame;
                    head = (head + 1) % capacity;
                }
                this.lastAccessTime = System.currentTimeMillis();
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public List<SseFrame> getFramesAfter(long lastSequenceId) {
            rwLock.readLock().lock();
            try {
                this.lastAccessTime = System.currentTimeMillis();
                List<SseFrame> result = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    int idx = (head + i) % capacity;
                    SseFrame frame = buffer[idx];
                    if (frame != null && frame.getSequenceId() > lastSequenceId) {
                        result.add(frame);
                    }
                }
                return result;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public int getSize() {
            rwLock.readLock().lock();
            try {
                return size;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }

    private final ConcurrentHashMap<String, SessionRingBuffer> sessionBuffers = new ConcurrentHashMap<>();

    /**
     * 写入一帧数据
     */
    public void recordFrame(String sessionId, long sequenceId, String data) {
        if (sessionId == null || data == null) {
            return;
        }
        sessionBuffers.computeIfAbsent(sessionId, k -> new SessionRingBuffer(DEFAULT_CAPACITY))
                .append(sequenceId, data);
    }

    /**
     * 获取指定序号之后的所有重放帧（按 sequenceId 升序排列）
     */
    public List<SseFrame> getFramesAfter(String sessionId, long lastSequenceId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        SessionRingBuffer ringBuffer = sessionBuffers.get(sessionId);
        if (ringBuffer == null) {
            return Collections.emptyList();
        }
        return ringBuffer.getFramesAfter(lastSequenceId);
    }

    /**
     * 清理会话缓冲区
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionBuffers.remove(sessionId);
        }
    }

    /**
     * 获取当前活跃会话数量
     */
    public int getActiveSessionCount() {
        cleanExpiredSessions();
        return sessionBuffers.size();
    }

    /**
     * 获取指定会话当前缓存帧数
     */
    public int getBufferSize(String sessionId) {
        SessionRingBuffer ringBuffer = sessionBuffers.get(sessionId);
        return ringBuffer != null ? ringBuffer.getSize() : 0;
    }

    /**
     * 定期或按需清理超过 60s 未访问的过期会话，杜绝内存泄漏
     */
    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        sessionBuffers.entrySet().removeIf(entry -> (now - entry.getValue().getLastAccessTime()) > DEFAULT_TTL_MS);
    }
}
