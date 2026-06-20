package tech.qiantong.qknow.module.ai.api.dto;

import lombok.*;

/**
 * AI 模型 DTO 对象 ai_model
 *
 * @author qknow
 * @date 2025-12-23
 */
@Data
public class AiModelRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 秘钥id */
    private Long keyId;

    /** 模型名称 */
    private String name;

    /** 模型标志 */
    private String model;

    /** 平台 */
    private String platform;

    /** 类型 */
    private Integer type;

    /** 排序值 */
    private Long sort;

    /** 状态 */
    private Integer status;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
