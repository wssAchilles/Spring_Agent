package tech.qiantong.qknow.hermes.flow.bo;

import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

public class SuspendNodeBO extends BaseNodeBO {

    public SuspendNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        super(nodeDefinition, edgeList);
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        JSONObject config = getNodeDefinition().getConfig() == null || getNodeDefinition().getConfig().isBlank()
                ? new JSONObject()
                : JSONObject.parseObject(getNodeDefinition().getConfig());
        String reason = config.getString("reason");
        return NodeRunResultBO.suspended(getNodeDefinition().getUuid(), getNodeDefinition().getName(), Map.of(
                "reason", reason != null && !reason.isBlank() ? reason : "waiting_for_human",
                "suspendedAt", System.currentTimeMillis()
        ));
    }
}
