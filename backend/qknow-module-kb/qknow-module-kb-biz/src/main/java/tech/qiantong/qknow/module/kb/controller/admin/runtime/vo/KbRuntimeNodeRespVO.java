package tech.qiantong.qknow.module.kb.controller.admin.runtime.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.qiantong.qknow.common.core.annotation.Excel;

import java.util.Date;
import java.io.Serializable;

/**
 * bot节点运行 Response VO 对象 kb_runtime_node
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot节点运行 Response VO")
@Data
public class KbRuntimeNodeRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Excel(name = "ID")
    @Schema(description = "ID")
    private Long id;

    @Excel(name = "工作区id")
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Excel(name = "运行时id")
    @Schema(description = "运行时id", example = "")
    private Long runtimeId;

    @Excel(name = "节点id")
    @Schema(description = "节点id", example = "")
    private Long nodeId;

    @Excel(name = "步骤号")
    @Schema(description = "步骤号", example = "")
    private Long step;

    @Excel(name = "输入数据")
    @Schema(description = "输入数据", example = "")
    private String input;

    @Excel(name = "输出数据")
    @Schema(description = "输出数据", example = "")
    private String output;

    @Excel(name = "是否有效")
    @Schema(description = "是否有效", example = "")
    private Boolean validFlag;

    @Excel(name = "删除标志")
    @Schema(description = "删除标志", example = "")
    private Boolean delFlag;

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
