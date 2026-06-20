package tech.qiantong.qknow.thirdparty.domain.dify.knowledge;

import lombok.Data;

/**
 * 知识库创建后返回的实体结果（document对象）
 * @program: qknow
 * @author wang
 * @date 2025/02/19 16:11
 **/
@Data
public class CreateFileResult {
    /** 文件id */
    private String id;

    private Integer position;

    private String dataSourceType;

    private String datasetProcessRuleId;

    /** 文件名称 */
    private String name;

    private String createdFrom;

    private String createdBy;

    private Integer createdAt;

    private Integer tokens;

    private String indexingStatus;

    private String error;

    private Boolean enabled;

    private String disabledAt;

    private String disabledBy;

    private Boolean archived;

    private String displayStatus;

    private Integer wordCount;

    private Integer hitCount;

    private String docForm;

    /** 上传文档的批次号 */
    private String batch;
}
