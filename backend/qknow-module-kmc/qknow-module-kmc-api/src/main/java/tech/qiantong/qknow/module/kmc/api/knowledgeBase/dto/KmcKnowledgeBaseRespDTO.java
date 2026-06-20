package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import tech.qiantong.qknow.common.core.annotation.Excel;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 知识库 DTO 对象 kmc_knowledge_base
 *
 * @author qknow
 * @date 2025-07-22
 */
@Data
public class KmcKnowledgeBaseRespDTO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 灵桐知识库id */
    private String qmDatasetId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 封面图片 */
    private String coverImage;

    /** 索引方式 */
    private String indexingTechnique;

    /** 权限 */
    private String permission;

    /** Embedding 模型名称 */
    private String embeddingModel;

    /** Embedding 模型供应商 */
    private String embeddingModelProvider;

    /** 检索方法 */
    private String searchMethod;

    /** 是否开启 rerank */
    private Boolean rerankingEnable;

    /** Rerank 模型的提供商 */
    private String rerankingProviderName;

    /** Rerank 模型的名称 */
    private String rerankingModelName;

    /** 召回条数 */
    private Long topK;

    /** 是否开启召回分数限制 */
    private Boolean scoreThresholdEnabled;

    /** 召回分数限制 */
    private BigDecimal scoreThreshold;

    /** 语义 */
    private BigDecimal keywordWeight;

    /** 关键字 */
    private BigDecimal vectorWeight;

    private String rerankingMode;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;

    @Excel(name = "标签")
    private String tags;

}
