package tech.qiantong.qknow.hermes.flow.bo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KbFlowNodeDO {
    private Long id;
    private Long workspaceId;
    private Long botId;
    private String uuid;
    private String name;
    private Integer type;
    private String config;
    private String style;
    private String input;
    private String output;
}
