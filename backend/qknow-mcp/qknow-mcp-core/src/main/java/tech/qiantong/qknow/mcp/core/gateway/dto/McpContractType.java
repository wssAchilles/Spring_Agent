package tech.qiantong.qknow.mcp.core.gateway.dto;

/**
 * 多源异构 MCP 契约类型枚举
 * 统一抽象 RESTful API、SQL 存储过程、本地 CLI 进程与标准 MCP Server
 */
public enum McpContractType {
    /**
     * RESTful Web API 契约 (OpenAPI / Swagger JSON Schema)
     */
    REST_API,

    /**
     * 关系型/分析型数据库 SQL 查询与存储过程契约
     */
    JDBC_SQL,

    /**
     * 本地 CLI 进程与命令行工具契约
     */
    CLI_PROCESS,

    /**
     * 标准 Model Context Protocol (MCP) JSON-RPC 契约
     */
    MCP_SERVER
}
