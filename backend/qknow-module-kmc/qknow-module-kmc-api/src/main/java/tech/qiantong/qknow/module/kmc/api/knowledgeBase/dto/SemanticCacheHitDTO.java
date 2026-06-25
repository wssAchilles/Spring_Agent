package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCacheHitDTO {

    private Long id;

    private String answer;

    private Double similarity;

    private String sourcesJson;
}
