package tech.qiantong.qknow.module.kg.controller.admin.graph;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.utils.SecurityUtils;
import tech.qiantong.qknow.module.kg.service.GraphCommunityService;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Tag(name = "知识图谱")
@RestController
@RequestMapping("/kg/graph")
public class KgGraphController extends BaseController {

    private static final int MAX_GRAPH_ROWS = 2000;

    @Resource
    private DataSource dataSource;

    @Autowired(required = false)
    private GraphCommunityService communityService;

    /**
     * 解析经过租户隔离校验的有效工作区 ID，非管理员禁止越权指定其他工作区
     */
    private Long resolveAuthorizedWorkspaceId(Long requestedWorkspaceId) {
        Long currentWsId = getWorkSpaceId();
        if (requestedWorkspaceId == null) {
            return currentWsId;
        }
        try {
            if (SecurityUtils.isAdmin(getUserId())) {
                return requestedWorkspaceId;
            }
        } catch (Exception ignored) {
        }
        return currentWsId;
    }

    @Operation(summary = "获取图谱数据")
    @PreAuthorize("@ss.hasPermi('kg:graph:query')")
    @GetMapping("/data")
    public CommonResult<Map<String, Object>> getGraphData() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Long workspaceId = getWorkSpaceId();

        try (Connection conn = dataSource.getConnection()) {
            // 按当前登录工作区参数化隔离查询节点并施加返回上限，阻断全表导出
            String nodeSql = "SELECT id, label, type, properties FROM kg_node WHERE workspace_id = ? AND del_flag = 0 LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(nodeSql)) {
                pstmt.setLong(1, workspaceId);
                pstmt.setInt(2, MAX_GRAPH_ROWS);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> node = new HashMap<>();
                        node.put("id", rs.getLong("id"));
                        node.put("label", rs.getString("label"));
                        node.put("type", rs.getString("type"));
                        node.put("properties", rs.getString("properties"));
                        nodes.add(node);
                    }
                }
            }

            // 按当前登录工作区参数化隔离查询边并施加返回上限
            String edgeSql = "SELECT id, source_id, target_id, label, properties FROM kg_edge WHERE workspace_id = ? AND del_flag = 0 LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(edgeSql)) {
                pstmt.setLong(1, workspaceId);
                pstmt.setInt(2, MAX_GRAPH_ROWS);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> edge = new HashMap<>();
                        edge.put("id", rs.getLong("id"));
                        edge.put("source", rs.getLong("source_id"));
                        edge.put("target", rs.getLong("target_id"));
                        edge.put("label", rs.getString("label"));
                        edge.put("properties", rs.getString("properties"));
                        edges.add(edge);
                    }
                }
            }
        } catch (SQLException e) {
            return CommonResult.error(500, "获取图谱数据失败: " + e.getMessage());
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        return CommonResult.success(result);
    }

    @Operation(summary = "添加节点")
    @PreAuthorize("@ss.hasPermi('kg:graph:edit')")
    @PostMapping("/node")
    public CommonResult<Long> addNode(@RequestBody Map<String, Object> nodeData) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO kg_node (workspace_id, label, type, properties) VALUES (?, ?, ?, ?::jsonb) RETURNING id";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, getWorkSpaceId());
                pstmt.setString(2, (String) nodeData.get("label"));
                pstmt.setString(3, (String) nodeData.getOrDefault("type", "concept"));
                pstmt.setString(4, (String) nodeData.getOrDefault("properties", "{}"));
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return CommonResult.success(rs.getLong(1));
                    }
                }
            }
        } catch (SQLException e) {
            return CommonResult.error(500, "添加节点失败: " + e.getMessage());
        }
        return CommonResult.error(500, "添加节点失败");
    }

    @Operation(summary = "添加边")
    @PreAuthorize("@ss.hasPermi('kg:graph:edit')")
    @PostMapping("/edge")
    public CommonResult<Long> addEdge(@RequestBody Map<String, Object> edgeData) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO kg_edge (workspace_id, source_id, target_id, label, properties) VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, getWorkSpaceId());
                pstmt.setLong(2, ((Number) edgeData.get("sourceId")).longValue());
                pstmt.setLong(3, ((Number) edgeData.get("targetId")).longValue());
                pstmt.setString(4, (String) edgeData.getOrDefault("label", "related_to"));
                pstmt.setString(5, (String) edgeData.getOrDefault("properties", "{}"));
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return CommonResult.success(rs.getLong(1));
                    }
                }
            }
        } catch (SQLException e) {
            return CommonResult.error(500, "添加边失败: " + e.getMessage());
        }
        return CommonResult.error(500, "添加边失败");
    }

    @Operation(summary = "执行社区检测")
    @PreAuthorize("@ss.hasPermi('kg:graph:edit')")
    @PostMapping("/communities/detect")
    public CommonResult<?> detectCommunities(@RequestParam(required = false) Long workspaceId) {
        Long effectiveWorkspaceId = resolveAuthorizedWorkspaceId(workspaceId);
        if (communityService == null) {
            return CommonResult.error(501, "社区检测需要 Neo4j 配置，请先配置 spring.neo4j.uri");
        }
        try {
            var communities = communityService.detectCommunities(String.valueOf(effectiveWorkspaceId));
            communityService.saveCommunities(String.valueOf(effectiveWorkspaceId), communities);
            return CommonResult.success(communities);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("gds.") || msg.contains("Unknown function") || msg.contains("procedure")) {
                return CommonResult.error(501, "Neo4j GDS 插件未安装，社区检测功能不可用。请安装 GDS 插件: docker exec agent-neo4j neo4j-admin plugin install graph-data-science");
            }
            return CommonResult.error(500, "社区检测失败: " + msg);
        }
    }

    @Operation(summary = "获取已检测的社区列表")
    @PreAuthorize("@ss.hasPermi('kg:graph:query')")
    @GetMapping("/communities")
    public CommonResult<List<Map<String, Object>>> getCommunities(@RequestParam(required = false) Long workspaceId) {
        Long effectiveWorkspaceId = resolveAuthorizedWorkspaceId(workspaceId);
        List<Map<String, Object>> communities = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT community_id, size, entities, labels FROM kg_community WHERE workspace_id = ? ORDER BY size DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, effectiveWorkspaceId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> community = new HashMap<>();
                        community.put("communityId", rs.getLong("community_id"));
                        community.put("size", rs.getInt("size"));
                        community.put("entities", rs.getString("entities"));
                        community.put("labels", rs.getString("labels"));
                        communities.add(community);
                    }
                }
            }
        } catch (SQLException e) {
            // kg_community 表可能不存在（未执行社区检测）
            return CommonResult.success(List.of());
        }
        return CommonResult.success(communities);
    }

    @Operation(summary = "生成社区摘要")
    @PreAuthorize("@ss.hasPermi('kg:graph:edit')")
    @PostMapping("/communities/summarize")
    public CommonResult<?> summarizeCommunities(@RequestParam(required = false) Long workspaceId) {
        Long effectiveWorkspaceId = resolveAuthorizedWorkspaceId(workspaceId);
        if (communityService == null) {
            return CommonResult.error(501, "社区摘要需要 Neo4j 配置，请先配置 spring.neo4j.uri");
        }
        try {
            return CommonResult.success(communityService.summarizeCommunities(String.valueOf(effectiveWorkspaceId)));
        } catch (Exception e) {
            return CommonResult.error(500, "社区摘要生成失败: " + e.getMessage());
        }
    }

    @Operation(summary = "社区摘要 Global Search")
    @PreAuthorize("@ss.hasPermi('kg:graph:query')")
    @GetMapping("/global-search")
    public CommonResult<?> globalSearch(@RequestParam(required = false) Long workspaceId,
                                        @RequestParam String query,
                                        @RequestParam(defaultValue = "5") Integer topK) {
        Long effectiveWorkspaceId = resolveAuthorizedWorkspaceId(workspaceId);
        if (communityService == null) {
            return CommonResult.error(501, "Global Search 需要 Neo4j 配置，请先配置 spring.neo4j.uri");
        }
        try {
            return CommonResult.success(communityService.globalSearch(
                    String.valueOf(effectiveWorkspaceId), query, topK != null ? topK : 5));
        } catch (Exception e) {
            return CommonResult.error(500, "Global Search 失败: " + e.getMessage());
        }
    }
}
