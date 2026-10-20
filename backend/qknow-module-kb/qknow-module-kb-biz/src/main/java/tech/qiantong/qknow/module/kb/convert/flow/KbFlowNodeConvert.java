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
        String typeStr = nodeJson.getString("type");
        FlowNodeTypeEnums nodeType = FlowNodeTypeEnums.getByName(typeStr);
        if (nodeType == null) {
            nodeType = FlowNodeTypeEnums.START;
        }
        JSONObject dataJson = nodeJson.getJSONObject("data");
        if (dataJson == null) {
            dataJson = new JSONObject();
        }
        JSONObject configJson = dataJson.getJSONObject("config");
        if (configJson == null) {
            configJson = new JSONObject();
            for (String key : dataJson.keySet()) {
                if (!"input".equals(key) && !"output".equals(key) && !"config".equals(key)) {
                    configJson.put(key, dataJson.get(key));
                }
            }
        }
        if (nodeType == FlowNodeTypeEnums.LLM) {
            if (!configJson.containsKey("provider") || configJson.get("provider") == null) {
                configJson.put("provider", 1L);
            }
            if (!configJson.containsKey("model") || configJson.getString("model") == null || configJson.getString("model").isBlank()) {
                configJson.put("model", "deepseek-flash");
            }
        }

        node.setUuid(nodeJson.getString("id"));
        String label = configJson.getString("label");
        if (label == null || label.isBlank()) {
            label = nodeJson.getString("id");
        }
        node.setName(label);
        node.setType(nodeType.getCode());
        node.setConfig(configJson.toString());
        node.setInput(dataJson.getJSONArray("input") != null ? dataJson.getJSONArray("input").toString() : "[]");
        node.setOutput(dataJson.getJSONArray("output") != null ? dataJson.getJSONArray("output").toString() : "[]");
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
