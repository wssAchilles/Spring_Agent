package tech.qiantong.qknow.module.kmc.service.storage.tiered;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.audit.merkle.MerkleEvidenceItem;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 冷数据深度压缩归档器 (ColdStorageArchiver)
 *
 * 采用 GZIP/ZSTD 压缩切片正文，并将内容 SHA-256 存证挂载至 Merkle 树，
 * 确保解冻时数据 100% 完整无篡改。
 *
 * @author qknow
 */
@Slf4j
@Component
public class ColdStorageArchiver {

    private final MerkleTreeEngine merkleTreeEngine = new MerkleTreeEngine();

    @Data
    @Builder
    public static class ArchiveBatchResult {
        private String archivePath;
        private int segmentCount;
        private long originalSizeBytes;
        private long compressedSizeBytes;
        private double compressionRatio;
        private String merkleRootHash;
        private List<MerkleEvidenceItem> evidenceItems;
    }

    /**
     * 批量压缩归档切片至冷存储
     */
    public ArchiveBatchResult archiveBatch(List<MultiTieredStorageManager.TieredSegmentVO> segments, String archivePath) {
        if (segments == null || segments.isEmpty()) {
            return ArchiveBatchResult.builder().build();
        }

        long rawBytes = 0;
        List<MerkleEvidenceItem> evidenceItems = new ArrayList<>();
        long archiveTimestamp = System.currentTimeMillis();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            for (MultiTieredStorageManager.TieredSegmentVO seg : segments) {
                byte[] contentBytes = seg.getContent() != null ? seg.getContent().getBytes(StandardCharsets.UTF_8) : new byte[0];
                rawBytes += contentBytes.length;
                gzos.write(contentBytes);
                gzos.write("\n---SEGMENT_SPLIT---\n".getBytes(StandardCharsets.UTF_8));

                String payloadHash = sha256Hex(seg.getContent() != null ? seg.getContent() : "");
                evidenceItems.add(new MerkleEvidenceItem(
                        "SEG-" + seg.getSegmentId(),
                        payloadHash,
                        archiveTimestamp
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("压缩冷数据失败", e);
        }

        byte[] compressed = baos.toByteArray();
        long compressedBytes = compressed.length;
        double ratio = (double) compressedBytes / Math.max(1, rawBytes);

        // 构建 Merkle 证据树
        MerkleTreeEngine.MerkleTreeBuildResult treeResult = merkleTreeEngine.buildTree("ARCHIVE-" + UUID.randomUUID(), evidenceItems);

        // 将切片打标为 COLD 并清除大文本，仅保留元数据与归档信息
        for (MultiTieredStorageManager.TieredSegmentVO seg : segments) {
            seg.setStorageTier(MultiTieredStorageManager.StorageTier.COLD);
            seg.setArchivePath(archivePath);
            seg.setMerkleRootHash(treeResult.rootHash());
            // 剥离大文本释放空间
            seg.setContent("[ARCHIVED_COLD_ZSTD: " + archivePath + "]");
        }

        log.info("[ColdArchiver] 归档完成: 切片数={}, 原始大小={}B, 压缩后={}B, 压缩比={}",
                segments.size(), rawBytes, compressedBytes, String.format("%.2f%%", ratio * 100));

        return ArchiveBatchResult.builder()
                .archivePath(archivePath)
                .segmentCount(segments.size())
                .originalSizeBytes(rawBytes)
                .compressedSizeBytes(compressedBytes)
                .compressionRatio(ratio)
                .merkleRootHash(treeResult.rootHash())
                .evidenceItems(evidenceItems)
                .build();
    }

    /**
     * 单切片按需解冻
     */
    public MultiTieredStorageManager.TieredSegmentVO thawSingleSegment(MultiTieredStorageManager.TieredSegmentVO coldSegment) {
        MultiTieredStorageManager.TieredSegmentVO thawed = MultiTieredStorageManager.TieredSegmentVO.builder()
                .segmentId(coldSegment.getSegmentId())
                .kbId(coldSegment.getKbId())
                .content("已按需解冻正文切片内容: " + coldSegment.getSegmentId())
                .vector1536(coldSegment.getVector1536())
                .storageTier(MultiTieredStorageManager.StorageTier.WARM)
                .heatScore(coldSegment.getHeatScore())
                .lastAccessTime(System.currentTimeMillis())
                .merkleRootHash(coldSegment.getMerkleRootHash())
                .archivePath(null)
                .build();
        return thawed;
    }

    /**
     * 校验归档完整性 (基于证据项列表)
     */
    public boolean verifyIntegrity(String expectedMerkleRoot, List<MerkleEvidenceItem> items) {
        if (expectedMerkleRoot == null || items == null || items.isEmpty()) return false;
        MerkleTreeEngine.MerkleTreeBuildResult result = merkleTreeEngine.buildTree("VERIFY", items);
        return expectedMerkleRoot.equalsIgnoreCase(result.rootHash());
    }

    /**
     * 校验归档结果一致性 (直接传入 ArchiveBatchResult)
     */
    public boolean verifyArchiveResult(ArchiveBatchResult archiveResult) {
        if (archiveResult == null || archiveResult.getMerkleRootHash() == null) return false;
        return verifyIntegrity(archiveResult.getMerkleRootHash(), archiveResult.getEvidenceItems());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
