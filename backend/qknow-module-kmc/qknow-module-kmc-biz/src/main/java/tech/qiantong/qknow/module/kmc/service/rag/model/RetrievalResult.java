package tech.qiantong.qknow.module.kmc.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {

    private Long segmentId;

    private Long documentId;

    private String documentName;

    private String content;

    private String answer;

    private double score;

    private String source;
}
