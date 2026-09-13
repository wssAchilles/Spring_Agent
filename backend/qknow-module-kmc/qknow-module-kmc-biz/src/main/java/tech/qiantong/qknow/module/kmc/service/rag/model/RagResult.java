package tech.qiantong.qknow.module.kmc.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResult {

    private String context;

    @Builder.Default
    private List<RetrievalResult> sources = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debugInfo = new HashMap<>();

    // CRAG AMBIGUOUS: 标记检索结果是否属于歧义状态
    @Builder.Default
    private Boolean ambiguous = false;

    // CRAG AMBIGUOUS: 提供的细分澄清选项列表
    @Builder.Default
    private List<String> clarificationOptions = new ArrayList<>();
}
