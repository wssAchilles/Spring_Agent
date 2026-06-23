package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端接口
 */
public interface McpClient {

    void initialize() throws McpException;

    List<JSONObject> listTools() throws McpException;

    JSONObject callTool(String toolName, Map<String, Object> arguments) throws McpException;

    void disconnect();
}
