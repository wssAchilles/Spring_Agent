package tech.qiantong.qknow.module.kb.convert.flow;

import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;

import java.util.ArrayList;
import java.util.List;


/**
 * bot流程节点 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
public class KbFlowNodeConvert {

    /**
     * JSONObject 转 KbFlowNodeDO
     *
     * @param nodeJson node 的 json 数据
     * @return KbFlowNodeDO
     */
    public static KbFlowNodeDO toDO(JSONObject nodeJson) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        FlowNodeTypeEnums nodeType = FlowNodeTypeEnums.getByName(nodeJson.getString("type"));
        JSONObject dataJson = nodeJson.getJSONObject("data");
        JSONObject configJson = dataJson.getJSONObject("config");
        assert nodeType != null;

        node.setUuid(nodeJson.getString("id"));
        node.setName(configJson.getString("label"));
        node.setType(nodeType.getCode());
        node.setConfig(configJson.toString());
        node.setInput(dataJson.getJSONArray("input").toString());
        node.setOutput(dataJson.getJSONArray("output").toString());
        node.setStyle(nodeJson.toJSONString());
        return node;
    }

    /**
     * KbFlowVO 转 KbFlowNodeDO 列表
     *
     * @param flowVO flowVO
     * @return KbFlowNodeDO
     */
    public static List<KbFlowNodeDO> toDOList(KbFlowVO flowVO) {
        List<KbFlowNodeDO> resultList = new ArrayList<>(flowVO.getNodes().size());
        for (JSONObject nodeJson : flowVO.getNodes()) {
            KbFlowNodeDO node = toDO(nodeJson);
            node.setBotId(flowVO.getBotId());
            node.setWorkspaceId(flowVO.getWorkspaceId());
            resultList.add(node);
        }
        return resultList;
    }
}
