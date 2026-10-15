package tech.qiantong.qknow.hermes.infra.offheap;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Java 21 Project Panama (FFM API) 的堆外连续 Slab 内存池
 * 专门用于大规模高并发向量打分（如 ColBERT MaxSim）与模型张量交互
 * 
 * 核心特性：
 * 1. 0-GC 停顿：所有密集浮点数向量直接在堆外连续内存分配，消除年轻代 GC 回收压力；
 * 2. 连续性与缓存亲和性：内存物理连续，极大提升 CPU L1/L2 Cache 命中率；
 * 3. 毫秒级复用：通过 Bump-pointer 分配器与 reset() 快速复用，无需反复申请系统虚拟内存；
 * 4. 线程安全与资源自动回收治理（AutoCloseable）。
 */
public class ArenaOffHeapSlabPool implements AutoCloseable {

    private static final int DEFAULT_CAPACITY_BYTES = 32 * 1024 * 1024; // 默认 32MB 连续 Slab
    private static final int FLOAT_BYTES = Float.BYTES; // 4 字节

    private final Arena arena;
    private final MemorySegment slabSegment;
    private final long totalCapacityBytes;
    private final AtomicLong allocatedBytes;
    private volatile boolean closed = false;

    public ArenaOffHeapSlabPool() {
        this(DEFAULT_CAPACITY_BYTES);
    }

    public ArenaOffHeapSlabPool(long capacityBytes) {
        if (capacityBytes <= 0 || capacityBytes % 64 != 0) {
            throw new IllegalArgumentException("Slab 内存池容量必须大于 0 且满足 64 字节缓存行对齐");
        }
        this.totalCapacityBytes = capacityBytes;
        this.arena = Arena.ofShared();
        this.slabSegment = arena.allocate(capacityBytes, 64); // 64 字节物理对齐
        this.allocatedBytes = new AtomicLong(0);
    }

    /**
     * 堆外连续内存切片结构
     */
    public record OffHeapSlice(
            MemorySegment parentSegment,
            MemorySegment sliceSegment,
            long byteOffset,
            long byteLength,
            int floatCount
    ) {
        /**
         * 写入 float 数组到该堆外切片
         */
        public void writeFloats(float[] source, int srcOffset, int length) {
            if (length > floatCount) {
                throw new IndexOutOfBoundsException(
                        String.format("写入长度 %d 超过切片容量 %d", length, floatCount));
            }
            MemorySegment.copy(source, srcOffset, sliceSegment, ValueLayout.JAVA_FLOAT, 0, length);
        }

        /**
         * 从堆外切片读取回 float 数组
         */
        public void readFloats(float[] destination, int destOffset, int length) {
            if (length > floatCount) {
                throw new IndexOutOfBoundsException(
                        String.format("读取长度 %d 超过切片容量 %d", length, floatCount));
            }
            MemorySegment.copy(sliceSegment, ValueLayout.JAVA_FLOAT, 0, destination, destOffset, length);
        }
    }

    /**
     * 在堆外连续 Slab 中分配指定数量的 float 连续空间
     *
     * @param floatCount 浮点数数量
     * @return 堆外切片对象
     */
    public OffHeapSlice allocateFloats(int floatCount) {
        checkNotClosed();
        long reqBytes = (long) floatCount * FLOAT_BYTES;
        // 确保分配以 64 字节（缓存行对齐）步进
        long alignedReqBytes = (reqBytes + 63) & ~63;

        long prevOffset = allocatedBytes.getAndAdd(alignedReqBytes);
        if (prevOffset + alignedReqBytes > totalCapacityBytes) {
            // 回滚并抛出空间溢出
            allocatedBytes.addAndGet(-alignedReqBytes);
            throw new OutOfMemoryError(String.format(
                    "堆外 Slab 内存池耗尽: 当前已分配 %d 字节, 请求 %d 字节, 总容量 %d 字节",
                    prevOffset, alignedReqBytes, totalCapacityBytes));
        }

        MemorySegment subSlice = slabSegment.asSlice(prevOffset, reqBytes);
        return new OffHeapSlice(slabSegment, subSlice, prevOffset, reqBytes, floatCount);
    }

    /**
     * 批量导入 float 数组并直接返回堆外切片
     */
    public OffHeapSlice loadFloats(float[] data) {
        if (data == null) {
            throw new IllegalArgumentException("导入向量数组不可为 null");
        }
        OffHeapSlice slice = allocateFloats(data.length);
        slice.writeFloats(data, 0, data.length);
        return slice;
    }

    /**
     * 快速重置分配指针（0 耗时复用，不回收操作系统内存）
     */
    public void reset() {
        checkNotClosed();
        allocatedBytes.set(0);
    }

    public long getTotalCapacityBytes() {
        return totalCapacityBytes;
    }

    public long getAllocatedBytes() {
        return allocatedBytes.get();
    }

    public double getUtilization() {
        return (double) allocatedBytes.get() / (double) totalCapacityBytes;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("ArenaOffHeapSlabPool 已关闭，不可继续操作");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            arena.close(); // 彻底释放堆外连续内存
        }
    }
}
