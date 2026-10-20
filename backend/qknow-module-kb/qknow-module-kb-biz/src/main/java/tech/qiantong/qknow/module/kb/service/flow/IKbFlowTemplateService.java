package tech.qiantong.qknow.module.kb.service.flow;

import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;

import java.util.List;

/**
 * 工作流模板与原生数据库初始化服务
 *
 * @author qknow
 * @date 2026-10-17
 */
public interface IKbFlowTemplateService {

    /**
     * 根据工作流类型或指定模板 Key，为新建的 Bot 初始化标准数据库工作流节点与连线 (向下兼容)
     *
     * @param botId Bot 编号
     * @param workflowType 工作流类型 (0: 工作流, 1: Chatflow, 2: Agent)
     * @param templateKey 模板标识 (可选)
     * @return 初始化生成的 KbFlowVO
     */
    default KbFlowVO initBotWorkflowByTemplate(Long botId, Integer workflowType, String templateKey) {
        return initBotWorkflowByTemplate(botId, workflowType, templateKey, null);
    }

    /**
     * 根据工作流类型或指定模板 Key，为新建的 Bot 初始化标准数据库工作流节点与连线
     *
     * @param botId Bot 编号
     * @param workflowType 工作流类型 (0: 工作流, 1: Chatflow, 2: Agent)
     * @param templateKey 模板标识 (可选)
     * @param workspaceId 所属租户/工作空间编号
     * @return 初始化生成的 KbFlowVO
     */
    KbFlowVO initBotWorkflowByTemplate(Long botId, Integer workflowType, String templateKey, Long workspaceId);

    /**
     * 获取系统内置的标准工作流模板定义
     *
     * @param workflowType 工作流类型过滤 (可选)
     * @return 模板定义列表
     */
    List<JSONObject> getAvailableTemplates(Integer workflowType);
}
