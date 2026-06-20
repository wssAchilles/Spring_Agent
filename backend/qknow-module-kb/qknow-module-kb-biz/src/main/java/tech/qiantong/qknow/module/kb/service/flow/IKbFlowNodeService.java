package tech.qiantong.qknow.module.kb.service.flow;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;

import java.util.List;

/**
 * bot流程节点Service接口
 *
 * @author qknow
 * @date 2026-03-18
 */
public interface IKbFlowNodeService extends IService<KbFlowNodeDO> {

    /**
     * 根据 botId 查询流程节点
     *
     * @param botId botId
     * @return 流程节点列表
     */
    List<JSONObject> flowVOByBotId(Long botId);

    /**
     * 批量新增流程节点
     *
     * @param flowVO 流程对象
     * @return 操作是否成功
     */
    boolean submitBatch(KbFlowVO flowVO);

    /**
     * 根据 botId 删除流程节点
     *
     * @param botId botId
     */
    void removeByBotId(Long botId);

    /**
     * 根据 botId 查询流程节点
     *
     * @param botId botId
     * @return 流程节点列表
     */
    List<KbFlowNodeDO> listByBotId(Long botId);
}
