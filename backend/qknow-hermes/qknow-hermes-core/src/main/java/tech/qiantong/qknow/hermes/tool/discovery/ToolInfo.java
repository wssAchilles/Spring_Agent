package tech.qiantong.qknow.hermes.tool.discovery;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 工具信息描述
 */
@Data
@AllArgsConstructor
public class ToolInfo {

    private String name;
    private String description;
    private String category;
    private boolean available;
}
