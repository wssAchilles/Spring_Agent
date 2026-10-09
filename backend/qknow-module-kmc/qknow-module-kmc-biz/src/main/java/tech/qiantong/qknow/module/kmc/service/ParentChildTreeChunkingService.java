package tech.qiantong.qknow.module.kmc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 父子层次树切片服务 (Parent-Child Hierarchical Tree Chunking Service)
 * <p>
 * 解决小切片检索敏锐但上下文缺失、大切片语义完整但检索稀释的双难悖论：
 * 1. 切片阶段：将文档切分为 Parent Chunk（800~1500 字符的大语义段）与 Child Chunk（150~300 字符的微切片）；
 * 2. 检索阶段：Child Chunk 用于高精度向量与全文索引命中；
 * 3. 召回阶段：命中 Child Chunk 自动向上解析并聚合唯一的 Parent Chunk 注入 LLM 上下文。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Service
public class ParentChildTreeChunkingService {

    public record ParentChunk(
            String parentId,
            String content,
            int orderIndex
    ) {}

    public record ChildChunk(
            String childId,
            String parentId,
            String content,
            int orderIndex
    ) {}

    public record HierarchicalChunkBundle(
            List<ParentChunk> parentChunks,
            List<ChildChunk> childChunks
    ) {}

    /**
     * 对原始文档内容执行父子层次树两级切分
     *
     * @param documentId     文档唯一标识
     * @param documentText   文档全文
     * @param parentSize     父切片大小（字符数，推荐 1000）
     * @param parentOverlap  父切片重叠大小（推荐 100）
     * @param childSize      子切片大小（字符数，推荐 250）
     * @param childOverlap   子切片重叠大小（推荐 30）
     * @return 包含父子两级切片的不可变聚合包
     */
    public HierarchicalChunkBundle createHierarchicalChunks(
            String documentId,
            String documentText,
            int parentSize,
            int parentOverlap,
            int childSize,
            int childOverlap
    ) {
        if (documentText == null || documentText.isBlank()) {
            return new HierarchicalChunkBundle(List.of(), List.of());
        }

        List<ParentChunk> parents = new ArrayList<>();
        List<ChildChunk> children = new ArrayList<>();

        int docLen = documentText.length();
        int parentStart = 0;
        int parentIndex = 0;

        while (parentStart < docLen) {
            int parentEnd = Math.min(parentStart + parentSize, docLen);
            String parentContent = documentText.substring(parentStart, parentEnd);
            String parentId = String.format("%s_p%d", documentId, parentIndex);

            ParentChunk parentChunk = new ParentChunk(parentId, parentContent, parentIndex);
            parents.add(parentChunk);

            // 在当前 Parent 内部切分子切片
            int childStartInParent = 0;
            int childIndex = 0;
            int pContentLen = parentContent.length();

            while (childStartInParent < pContentLen) {
                int childEnd = Math.min(childStartInParent + childSize, pContentLen);
                String childContent = parentContent.substring(childStartInParent, childEnd);
                String childId = String.format("%s_c%d", parentId, childIndex);

                ChildChunk childChunk = new ChildChunk(childId, parentId, childContent, childIndex);
                children.add(childChunk);

                if (childEnd == pContentLen) break;
                childStartInParent += (childSize - childOverlap);
                childIndex++;
            }

            if (parentEnd == docLen) break;
            parentStart += (parentSize - parentOverlap);
            parentIndex++;
        }

        log.info("文档层次树切分完成: docId={}, parentCount={}, childCount={}", documentId, parents.size(), children.size());
        return new HierarchicalChunkBundle(Collections.unmodifiableList(parents), Collections.unmodifiableList(children));
    }

    /**
     * 根据命中的 Child Chunk ID 列表向上聚合解析唯一父切片集合（保序去重）
     *
     * @param hitChildIds 命中的子切片 ID 列表
     * @param parentMap   父切片快速查找字典
     * @param childMap    子切片快速查找字典
     * @return 聚合去重后的 Parent Chunk 列表（保留原始文章顺序）
     */
    public List<ParentChunk> resolveParentsForMatchedChildren(
            List<String> hitChildIds,
            Map<String, ParentChunk> parentMap,
            Map<String, ChildChunk> childMap
    ) {
        if (hitChildIds == null || hitChildIds.isEmpty() || parentMap == null) {
            return List.of();
        }

        Set<String> resolvedParentIds = new LinkedHashSet<>();
        for (String childId : hitChildIds) {
            ChildChunk child = childMap != null ? childMap.get(childId) : null;
            if (child != null && child.parentId() != null) {
                resolvedParentIds.add(child.parentId());
            } else if (childId.contains("_p") && childId.contains("_c")) {
                // 基于规范 ID 前缀快速推断 parentId: doc_p0_c1 -> doc_p0
                String inferredParentId = childId.substring(0, childId.lastIndexOf("_c"));
                resolvedParentIds.add(inferredParentId);
            }
        }

        List<ParentChunk> result = new ArrayList<>();
        for (String pId : resolvedParentIds) {
            ParentChunk parent = parentMap.get(pId);
            if (parent != null) {
                result.add(parent);
            }
        }

        // 按照在文章中的原始顺位排序以呈现连贯语境
        result.sort(Comparator.comparingInt(ParentChunk::orderIndex));
        return result;
    }
}
