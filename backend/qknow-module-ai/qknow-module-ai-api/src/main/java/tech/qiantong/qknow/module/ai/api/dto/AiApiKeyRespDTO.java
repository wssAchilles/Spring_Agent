package tech.qiantong.qknow.module.ai.api.dto;

import lombok.*;

/**
 * API秘钥 DTO 对象 ai_api_key
 *
 * @author qknow
 * @date 2025-12-23
 */
@Data
public class AiApiKeyRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 名称 */
    private String name;

    /** 秘钥 */
    private String apiKey;

    /** 平台 */
    private String platform;

    /** API地址 */
    private String url;

    /** 状态 */
    private Integer status;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
