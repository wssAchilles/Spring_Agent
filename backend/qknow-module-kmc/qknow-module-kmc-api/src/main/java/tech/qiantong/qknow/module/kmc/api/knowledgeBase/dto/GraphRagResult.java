package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRagResult {
    private Long segmentId;
    private Long documentId;
    private String documentName;
    private String content;
    private String evidence;
    private double score;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
