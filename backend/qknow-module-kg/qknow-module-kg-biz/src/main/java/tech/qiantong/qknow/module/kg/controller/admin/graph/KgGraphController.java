package tech.qiantong.qknow.module.kg.controller.admin.graph;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kg.service.GraphCommunityService;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Tag(name = "知识图谱")
@RestController
@RequestMapping("/kg/graph")
public class KgGraphController {

    @Resource
    private DataSource dataSource;

    @Autowired(required = false)
    private GraphCommunityService communityService;

    @Operation(summary = "获取图谱数据")
    @GetMapping("/data")
    public CommonResult<Map<String, Object>> getGraphData() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            // Get nodes
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, label, type, properties FROM kg_node WHERE del_flag = 0")) {
                while (rs.next()) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", rs.getLong("id"));
                    node.put("label", rs.getString("label"));
                    node.put("type", rs.getString("type"));
                    node.put("properties", rs.getString("properties"));
                    nodes.add(node);
                }
            }

            // Get edges
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, source_id, target_id, label, properties FROM kg_edge WHERE del_flag = 0")) {
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
        } catch (SQLException e) {
            return CommonResult.error(500, "获取图谱数据失败: " + e.getMessage());
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        return CommonResult.success(result);
    }

    @Operation(summary = "添加节点")
    @PostMapping("/node")
    public CommonResult<Long> addNode(@RequestBody Map<String, Object> nodeData) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO kg_node (workspace_id, label, type, properties) VALUES (?, ?, ?, ?::jsonb) RETURNING id";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1001L);
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
    @PostMapping("/edge")
    public CommonResult<Long> addEdge(@RequestBody Map<String, Object> edgeData) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO kg_edge (workspace_id, source_id, target_id, label, properties) VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1001L);
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
    @PostMapping("/communities/detect")
    public CommonResult<?> detectCommunities(@RequestParam(defaultValue = "1001") Long workspaceId) {
        if (communityService == null) {
            return CommonResult.error(501, "社区检测需要 Neo4j 配置，请先配置 spring.neo4j.uri");
        }
        try {
            var communities = communityService.detectCommunities(String.valueOf(workspaceId));
            communityService.saveCommunities(String.valueOf(workspaceId), communities);
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
    @GetMapping("/communities")
    public CommonResult<List<Map<String, Object>>> getCommunities(@RequestParam(defaultValue = "1001") Long workspaceId) {
        List<Map<String, Object>> communities = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT community_id, size, entities, labels FROM kg_community WHERE workspace_id = ? ORDER BY size DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, workspaceId);
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
}
