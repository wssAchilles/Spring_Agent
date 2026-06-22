package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.qiantong.qknow.common.core.annotation.Excel;
import java.util.Date;
import java.io.Serializable;

/**
 * 应用图谱关联 Response VO 对象 kac_apply_graph
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "应用图谱关联 Response VO")
@Data
public class KacApplyGraphRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Excel(name = "ID")
    @Schema(description = "ID")
    private Long id;

    @Excel(name = "应用id")
    @Schema(description = "应用id", example = "")
    private Long applyId;

    @Excel(name = "图谱id")
    @Schema(description = "图谱id", example = "")
    private Long graphId;

    @Excel(name = "工作区id")
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Excel(name = "是否有效")
    @Schema(description = "是否有效", example = "")
    private Boolean validFlag;

    @Excel(name = "创建人")
    @Schema(description = "创建人", example = "")
    private String createBy;

    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "")
    private Date createTime;
}
