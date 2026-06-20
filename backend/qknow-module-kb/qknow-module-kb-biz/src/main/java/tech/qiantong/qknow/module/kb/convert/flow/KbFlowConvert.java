package tech.qiantong.qknow.module.kb.convert.flow;

import cn.hutool.core.collection.CollUtil;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.service.flow.bo.KbFlowBO;

/**
 * bot流程节点 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
public class KbFlowConvert {

    /**
     * 转换为运行时对象
     *
     * @param flowVO 流程 vo 对象
     * @return 流程运行对象
     */
    public static KbFlowBO toFlowBO(KbFlowVO flowVO) {
        KbFlowBO result = new KbFlowBO();
        result.setBotId(flowVO.getBotId());
        result.setWorkspaceId(flowVO.getWorkspaceId());
        if (CollUtil.isNotEmpty(flowVO.getNodes())) {
            result.setNodeList(KbFlowNodeConvert.toDOList(flowVO));
        }
        if (CollUtil.isNotEmpty(flowVO.getEdges())) {
            result.setEdgeList(KbFlowEdgeConvert.toDOList(flowVO));
        }
        return result;
    }
}
