package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.Data;

import java.util.List;

@Data
public class SemanticCacheLookupReqDTO {

    private Long workspaceId;

    private Long botId;

    private List<Long> knowledgeBaseIds;

    private String query;

    private String modelName;
}
