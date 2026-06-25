package tech.qiantong.qknow.module.kmc.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryIntent {

    private Integer dayNo;

    private String docName;

    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    @Builder.Default
    private List<String> entities = new ArrayList<>();

    private String category;
}
