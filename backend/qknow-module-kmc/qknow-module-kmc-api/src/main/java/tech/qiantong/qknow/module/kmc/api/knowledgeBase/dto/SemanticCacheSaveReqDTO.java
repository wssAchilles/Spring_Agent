package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.Data;

import java.util.List;

@Data
public class SemanticCacheSaveReqDTO {

    private Long workspaceId;

    private Long botId;

    private List<Long> knowledgeBaseIds;

    private String query;

    private String answer;

    private String modelName;

    private String sourcesJson;
}
