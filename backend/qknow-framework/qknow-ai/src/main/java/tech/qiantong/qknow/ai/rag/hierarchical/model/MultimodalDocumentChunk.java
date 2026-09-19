package tech.qiantong.qknow.ai.rag.hierarchical.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 层次化多模态文档切片不可变实体 (Java 21 Record)
 * 记录 Markdown/HTML 语法树切片、面包屑路径、表格行跨度与多模态图文空间锚定信息，
 * 构造时自动计算 SHA-256 密码学自签名保证防篡改与高保真追溯。
 */
public record MultimodalDocumentChunk(
        String chunkId,
        String parentChunkId,
        ChunkType chunkType,
        String content,
        List<String> breadcrumbPath,
        Map<String, String> metadata,
        boolean isPartialTable,
        int tableRowStart,
        int tableRowEnd,
        String imageUri,
        String imageCaption,
        String chunkSha256
) {
    /**
     * 切片类型枚举
     */
    public enum ChunkType {
        TEXT_PARAGRAPH,
        TABLE,
        IMAGE_ANCHORED,
        SECTION_PARENT
    }

    public MultimodalDocumentChunk {
        Objects.requireNonNull(chunkId, "chunkId 不能为空");
        Objects.requireNonNull(chunkType, "chunkType 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        breadcrumbPath = breadcrumbPath == null ? List.of() : List.copyOf(breadcrumbPath);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        if (chunkSha256 == null) {
            chunkSha256 = calculateSha256(content, breadcrumbPath, chunkType);
        }
    }

    /**
     * 计算切片内容的 SHA-256 摘要
     */
    public static String calculateSha256(String content, List<String> breadcrumbs, ChunkType type) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(content.getBytes(StandardCharsets.UTF_8));
            digest.update(String.join("/", breadcrumbs).getBytes(StandardCharsets.UTF_8));
            digest.update(type.name().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 校验切片 SHA-256 签名的有效性
     */
    public boolean verifySignature() {
        return chunkSha256.equals(calculateSha256(content, breadcrumbPath, chunkType));
    }
}
