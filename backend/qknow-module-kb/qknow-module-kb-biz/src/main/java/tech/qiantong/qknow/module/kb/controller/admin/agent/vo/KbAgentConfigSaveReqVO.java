package tech.qiantong.qknow.module.kb.controller.admin.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * agent配置 创建/修改 Request VO kb_agent_config
 *
 * @author qknow
 * @date 2026-03-19
 */
@Schema(description = "agent配置 Response VO")
@Data
public class KbAgentConfigSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botid", example = "")
    @NotNull(message = "bot不能为空")
    private Long botId;

    @Schema(description = "大模型配置", example = "")
    @NotBlank(message = "大模型配置不能为空")
    private String modelConfig;

    @Schema(description = "提示词", example = "")
    private String prePrompt;

    @Schema(description = "参数配置", example = "")
    private String parameters;

    @Schema(description = "知识库ids", example = "")
    @Size(max = 128, message = "知识库ids长度不能超过128个字符")
    private String knowledgeIds;

    @Schema(description = "知识图谱ids", example = "")
    @Size(max = 128, message = "知识图谱ids长度不能超过128个字符")
    private String graphIds;

    @Schema(description = "工具方法ids", example = "")
    @Size(max = 128, message = "工具方法ids长度不能超过128个字符")
    private String toolMethodIds;

    @Schema(description = "备注", example = "")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
