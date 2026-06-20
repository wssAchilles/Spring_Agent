package tech.qiantong.qknow.thirdparty.domain.dify.studio;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行
 * @program: qknow
 * @author wang
 * @date 2025/02/19 16:11
 **/
@Data
public class WorkflowExecution {

    /** workflow 执行 ID */
    private String id;

    /** 关联的 workflow ID */
    private String workflowId;

    /** workflow 执行状态 running / succeeded / failed / stopped */
    private String status;

    /** workflow 执行输入参数 */
    private String inputs;

    /** workflow 执行输出参数 */
    private String outputs;

    /** workflow 执行错误信息 */
    private String error;

    /** workflow 执行进度 */
    private Integer totalSteps;

    /** workflow 执行 tokens */
    private Integer totalTokens;

    /** workflow 执行开始时间 */
    private LocalDateTime createdAt;

    /** workflow 执行结束时间 */
    private LocalDateTime finishedAt;

    /** workflow 执行耗时 */
    private Double elapsedTime;

}
