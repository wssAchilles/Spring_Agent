package tech.qiantong.qknow.module.kb.controller.admin.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * agent配置 Request VO 对象 kb_agent_config
 *
 * @author qknow
 * @date 2026-03-19
 */
@Schema(description = "agent配置 Request VO")
@Data
public class KbAgentConfigPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "botid", example = "")
    private Long botId;

    @Schema(description = "大模型配置", example = "")
    private String modelConfig;

    @Schema(description = "提示词", example = "")
    private String prePrompt;

    @Schema(description = "参数配置", example = "")
    private String parameters;

    @Schema(description = "知识库ids", example = "")
    private String knowledgeIds;

    @Schema(description = "知识图谱ids", example = "")
    private String graphIds;

    @Schema(description = "工具方法ids", example = "")
    private String toolMethodIds;




}
