package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.qiantong.qknow.common.core.annotation.Excel;
import java.util.Date;
import java.io.Serializable;

/**
 * 解决方案应用关联 Response VO 对象 kac_solution_apply
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "解决方案应用关联 Response VO")
@Data
public class KacSolutionApplyRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Excel(name = "ID")
    @Schema(description = "ID")
    private Long id;

    @Excel(name = "解决方案id")
    @Schema(description = "解决方案id", example = "")
    private Long solutionId;

    @Excel(name = "应用id")
    @Schema(description = "应用id", example = "")
    private Long applyId;

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
