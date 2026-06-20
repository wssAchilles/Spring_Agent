package tech.qiantong.qknow.module.kb.controller.admin.runtime.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.qiantong.qknow.common.core.annotation.Excel;

import java.util.Date;
import java.io.Serializable;

/**
 * bot运行 Response VO 对象 kb_runtime
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot运行 Response VO")
@Data
public class KbRuntimeRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Excel(name = "ID")
    @Schema(description = "ID")
    private Long id;

    @Excel(name = "工作区id")
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Excel(name = "botId")
    @Schema(description = "botId", example = "")
    private Long botId;

    @Excel(name = "输入问题")
    @Schema(description = "输入问题", example = "")
    private String input;

    @Excel(name = "输出结果")
    @Schema(description = "输出结果", example = "")
    private String output;

    @Excel(name = "运行状态")
    @Schema(description = "运行状态", example = "")
    private Integer status;

    @Excel(name = "运行时间(毫秒)")
    @Schema(description = "运行时间(毫秒)", example = "")
    private Long runtime;

    @Excel(name = "创建人")
    @Schema(description = "创建人", example = "")
    private String createBy;

    @Excel(name = "创建人id")
    @Schema(description = "创建人id", example = "")
    private Long creatorId;

    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "")
    private Date createTime;

    @Excel(name = "更新人")
    @Schema(description = "更新人", example = "")
    private String updateBy;

    @Excel(name = "更新人id")
    @Schema(description = "更新人id", example = "")
    private Long updaterId;

    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "")
    private Date updateTime;

    @Excel(name = "备注")
    @Schema(description = "备注", example = "")
    private String remark;

}
