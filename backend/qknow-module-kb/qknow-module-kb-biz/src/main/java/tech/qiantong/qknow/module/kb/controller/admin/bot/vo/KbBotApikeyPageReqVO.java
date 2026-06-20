package tech.qiantong.qknow.module.kb.controller.admin.bot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * bot访问密钥 Request VO 对象 kb_bot_apikey
 *
 * @author qknow
 * @date 2026-04-24
 */
@Schema(description = "bot访问密钥 Request VO")
@Data
public class KbBotApikeyPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "apikey", example = "")
    private String apiKey;

    @Schema(description = "botid", example = "")
    private Long botId;




}
