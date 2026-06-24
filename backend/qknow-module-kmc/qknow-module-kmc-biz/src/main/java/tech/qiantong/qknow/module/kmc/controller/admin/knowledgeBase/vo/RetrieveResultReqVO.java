package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 知识库 Request VO 对象 kmc_knowledge_base
 *
 * @author qknow
 * @date 2025-07-22
 */
@Schema(description = "知识库 Request VO")
@Data
public class RetrieveResultReqVO {

    @Schema(description = "ID", example = "")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "检索内容", example = "")
    private String query;

    @Schema(description = "索引方式", example = "")
    private String indexingTechnique;

    @Schema(description = "权限", example = "")
    private String permission;

    @Schema(description = "检索方法", example = "")
    private String searchMethod;

    @Schema(description = "是否开启 rerank", example = "")
    private Boolean rerankingEnable;

    @Schema(description = "Rerank 模型的提供商", example = "")
    private String rerankingProviderName;

    @Schema(description = "Rerank 模型的名称", example = "")
    private String rerankingModelName;

    @Schema(description = "召回条数", example = "")
    private BigDecimal topK;

    @Schema(description = "是否开启召回分数限制", example = "")
    private Boolean scoreThresholdEnabled;

    @Schema(description = "召回分数限制", example = "")
    private BigDecimal scoreThreshold;

    @Schema(description = "检索规则", example = "")
    private String rerankingMode;

    @Schema(description = "全文检索权重", example = "")
    private BigDecimal keywordWeight;

    @Schema(description = "向量检索权重", example = "")
    private BigDecimal vectorWeight;

    @Schema(description = "嵌入式模型", example = "")
    private String embeddingModel;

    @Schema(description = "嵌入式模型提供", example = "")
    private String embeddingModelProvider;

    @Schema(description = "多轮对话历史", example = "")
    private List<ChatMessage> history;

    @Data
    public static class ChatMessage {
        @Schema(description = "角色", example = "user")
        private String role;
        @Schema(description = "内容", example = "")
        private String content;
    }
}
