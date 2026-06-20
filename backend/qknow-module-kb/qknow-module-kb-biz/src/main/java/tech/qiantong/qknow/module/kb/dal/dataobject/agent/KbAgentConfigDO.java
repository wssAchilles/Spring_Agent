package tech.qiantong.qknow.module.kb.dal.dataobject.agent;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * agent配置 DO 对象 kb_agent_config
 *
 * @author qknow
 * @date 2026-03-19
 */
@Data
@TableName(value = "kb_agent_config")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kb_agent_config_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KbAgentConfigDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** bot id */
    private Long botId;

    /** 大模型配置 */
    private String modelConfig;

    /** 提示词 */
    private String prePrompt;

    /** 参数配置 */
    private String parameters;

    /** 知识库ids */
    private String knowledgeIds;

    /** 知识图谱ids */
    private String graphIds;

    /** 工具方法ids */
    private String toolMethodIds;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    @TableLogic
    private Boolean delFlag;


}
