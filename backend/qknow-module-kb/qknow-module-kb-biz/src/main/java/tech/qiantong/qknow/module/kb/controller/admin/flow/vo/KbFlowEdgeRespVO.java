package tech.qiantong.qknow.module.kb.controller.admin.flow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.qiantong.qknow.common.core.annotation.Excel;

import java.util.Date;
import java.io.Serializable;

/**
 * bot流程关系 Response VO 对象 kb_flow_edge
 *
 * @author qknow
 * @date 2026-03-18
 */
@Schema(description = "bot流程关系 Response VO")
@Data
public class KbFlowEdgeRespVO implements Serializable {

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

    @Excel(name = "开始节点唯一标识")
    @Schema(description = "开始节点唯一标识", example = "")
    private String sourceNodeUuid;

    @Excel(name = "结束节点唯一标识")
    @Schema(description = "结束节点唯一标识", example = "")
    private String targetNodeUuid;

    @Excel(name = "开始节点锚点")
    @Schema(description = "开始节点锚点", example = "")
    private String sourceHandle;

    @Excel(name = "连线样式")
    @Schema(description = "连线样式", example = "")
    private String style;

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
