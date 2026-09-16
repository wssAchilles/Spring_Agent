package tech.qiantong.qknow.module.app.dal.dataobject.kac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * 应用执行日志与凭单审计 DO 对象 kac_apply_execution_log
 *
 * @author qknow
 */
@Data
@TableName(value = "kac_apply_execution_log")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KacApplyExecutionLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用ID */
    private Long applyId;

    /** 版本ID */
    private Long versionId;

    /** 工作区ID */
    private Long workspaceId;

    /** 链路 Trace ID */
    private String traceId;

    /** 凭单 ID (唯一不可篡改) */
    private String receiptId;

    /** 执行模式 */
    private String executionMode;

    /** 输入参数快照 (JSON) */
    private String inputParams;

    /** 渲染后完整 Prompt */
    private String renderedPrompt;

    /** 输出结果文本 */
    private String outputContent;

    /** 知识库检索溯源 (JSON) */
    private String ragSources;

    /** 工具调用记录 (JSON) */
    private String toolCalls;

    /** Prompt Tokens */
    private Integer promptTokens;

    /** Completion Tokens */
    private Integer completionTokens;

    /** Total Tokens */
    private Integer totalTokens;

    /** 耗时 (ms) */
    private Long latencyMs;

    /** 状态: SUCCESS, FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 创建人ID */
    private Long creatorId;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;
}
