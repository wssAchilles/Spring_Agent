package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GraphRagSearchReqDTO {
    private Long knowledgeBaseId;
    private List<String> entities = new ArrayList<>();
    private Integer maxHops;
    private Integer topK;
}
