package tech.qiantong.qknow.module.kb.convert.flow;

import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;

import java.util.ArrayList;
import java.util.List;

/**
 * bot流程关系 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
public class KbFlowEdgeConvert {
    /**
     * JSONObject 转 KbFlowEdgeDO
     *
     * @param edgeJson edgeJson 的 json 数据
     * @return KbFlowEdgeDO
     */
    public static KbFlowEdgeDO toDO(JSONObject edgeJson) {
        KbFlowEdgeDO edge = new KbFlowEdgeDO();
        edge.setSourceNodeUuid(edgeJson.getString("source"));
        edge.setTargetNodeUuid(edgeJson.getString("target"));
        edge.setSourceHandle(edgeJson.getString("sourceHandle"));
        edge.setStyle(edgeJson.toJSONString());
        return edge;
    }

    /**
     * KbFlowVO 转 KbFlowEdgeDO 列表
     *
     * @param flowVO flowVO
     * @return KbFlowEdgeDO
     */
    public static List<KbFlowEdgeDO> toDOList(KbFlowVO flowVO) {
        List<KbFlowEdgeDO> resultList = new ArrayList<>(flowVO.getEdges().size());
        for (JSONObject edgeJson : flowVO.getEdges()) {
            KbFlowEdgeDO edge = toDO(edgeJson);
            edge.setBotId(flowVO.getBotId());
            edge.setWorkspaceId(flowVO.getWorkspaceId());
            resultList.add(edge);
        }
        return resultList;
    }
}
