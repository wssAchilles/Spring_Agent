package tech.qiantong.qknow.hermes.flow.bo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KbFlowEdgeDO {
    private Long id;
    private Long workspaceId;
    private Long botId;
    private String sourceNodeUuid;
    private String targetNodeUuid;
    private String sourceHandle;
    private String style;
    private Boolean validFlag;
    private Boolean delFlag;
}
