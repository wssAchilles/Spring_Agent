package tech.qiantong.qknow.hermes.flow.bo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KbRuntimeDO {
    private Long id;
    private Long workspaceId;
    private Long botId;
    private Long conversationId;
    private Integer status;
    private String output;
    private Date startTime;
    private Date endTime;
}
