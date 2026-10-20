package tech.qiantong.qknow.module.kb.service.flow.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowEdgeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowNodeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowTemplateService;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流模板与原生数据库持久化初始化服务实现
 *
 * @author qknow
 * @date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbFlowTemplateServiceImpl implements IKbFlowTemplateService {

    private final IKbFlowNodeService flowNodeService;
    private final IKbFlowEdgeService flowEdgeService;
    private final tech.qiantong.qknow.module.kb.dal.mapper.bot.KbBotMapper botMapper;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public KbFlowVO initBotWorkflowByTemplate(Long botId, Integer workflowType, String templateKey, Long workspaceId) {
        log.info("[WorkflowTemplate] Initializing database workflow for botId: {}, workflowType: {}, templateKey: {}, workspaceId: {}",
                botId, workflowType, templateKey, workspaceId);

        // 多级安全解析 workspaceId：优先显式入参 -> 查 Bot 实体 -> 上下文租户 -> 保底 1001L
        Long resolvedWorkspaceId = workspaceId;
        if (resolvedWorkspaceId == null && botId != null && botMapper != null) {
            try {
                tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotDO bot = botMapper.selectById(botId);
                if (bot != null && bot.getWorkspaceId() != null) {
                    resolvedWorkspaceId = bot.getWorkspaceId();
                }
            } catch (Exception ex) {
                log.warn("[WorkflowTemplate] 查询 Bot 获取 workspaceId 异常: botId={}, msg={}", botId, ex.getMessage());
            }
        }
        if (resolvedWorkspaceId == null) {
            try {
                Long deptId = tech.qiantong.qknow.common.utils.SecurityUtils.getDeptId();
                if (deptId != null && deptId > 0) {
                    resolvedWorkspaceId = deptId;
                }
            } catch (Exception ignored) {
            }
            if (resolvedWorkspaceId == null) {
                resolvedWorkspaceId = 1001L;
            }
        }

        String activeKey = templateKey;
        if (activeKey == null || activeKey.isBlank()) {
            if (workflowType != null && workflowType == 1) {
                activeKey = "tpl_chat";
            } else if (workflowType != null && workflowType == 2) {
                activeKey = "tpl_agent_tool";
            } else {
                activeKey = "tpl_rag_qa";
            }
        }

        JSONObject templateDsl = buildTemplateDsl(activeKey);
        KbFlowVO flowVO = new KbFlowVO();
        flowVO.setBotId(botId);
        flowVO.setWorkspaceId(resolvedWorkspaceId); // 严密注入 workspaceId，满足数据库 NOT NULL 约束

        JSONArray nodesArray = templateDsl.getJSONArray("nodes");
        List<JSONObject> nodesList = new ArrayList<>();
        if (nodesArray != null) {
            for (int i = 0; i < nodesArray.size(); i++) {
                nodesList.add(nodesArray.getJSONObject(i));
            }
        }
        flowVO.setNodes(nodesList);

        JSONArray edgesArray = templateDsl.getJSONArray("edges");
        List<JSONObject> edgesList = new ArrayList<>();
        if (edgesArray != null) {
            for (int i = 0; i < edgesArray.size(); i++) {
                edgesList.add(edgesArray.getJSONObject(i));
            }
        }
        flowVO.setEdges(edgesList);

        // 原生写入数据库表 kb_flow_node 与 kb_flow_edge
        flowNodeService.submitBatch(flowVO);
        flowEdgeService.submitBatch(flowVO);

        log.info("[WorkflowTemplate] Successfully initialized {} nodes and {} edges for botId: {} in workspace: {}",
                nodesList.size(), edgesList.size(), botId, resolvedWorkspaceId);
        return flowVO;
    }

    @Override
    public List<JSONObject> getAvailableTemplates(Integer workflowType) {
        List<JSONObject> list = new ArrayList<>();
        list.add(createTemplateMeta("tpl_rag_qa", "智能知识问答流", "结合知识库精准召回与 DeepSeek 深度推理的黄金 RAG 工作流", 0));
        list.add(createTemplateMeta("tpl_chat", "极简大模型对话流", "单轮或多轮端到端极速直出大模型对话流", 1));
        list.add(createTemplateMeta("tpl_agent_tool", "智能体工具协同流", "具备工具参数绑定与多轮自愈能力的自主智能体流", 2));
        return list;
    }

    private JSONObject createTemplateMeta(String key, String name, String desc, int type) {
        JSONObject meta = new JSONObject();
        meta.put("key", key);
        meta.put("name", name);
        meta.put("description", desc);
        meta.put("workflowType", type);
        return meta;
    }

    /**
     * 构建无任何院校测试脏数据、强类型规整的标准初始 DSL 结构
     */
    private JSONObject buildTemplateDsl(String key) {
        JSONObject dsl = new JSONObject();
        JSONArray nodes = new JSONArray();
        JSONArray edges = new JSONArray();

        if ("tpl_chat".equals(key)) {
            // 开始节点
            nodes.add(createNode("start_1", "start", 100, 240, "开始", createStartData()));
            // LLM 节点
            nodes.add(createNode("llm_1", "llm", 480, 240, "DeepSeek 推理", createLlmData("{{ start_1.query }}")));
            // 回复节点
            nodes.add(createNode("reply_1", "reply", 860, 240, "直接回复", createReplyData("{{ llm_1.text }}")));

            // 连线
            edges.add(createEdge("e-start-llm", "start_1", "llm_1"));
            edges.add(createEdge("e-llm-reply", "llm_1", "reply_1"));
        } else if ("tpl_agent_tool".equals(key)) {
            nodes.add(createNode("start_1", "start", 100, 240, "开始", createStartData()));
            nodes.add(createNode("tool_1", "tool", 460, 240, "扩展工具", createToolData()));
            nodes.add(createNode("llm_1", "llm", 820, 240, "DeepSeek 推理", createLlmData("工具输出：{{ tool_1.result }}\n用户输入：{{ start_1.query }}")));
            nodes.add(createNode("reply_1", "reply", 1180, 240, "直接回复", createReplyData("{{ llm_1.text }}")));

            edges.add(createEdge("e-start-tool", "start_1", "tool_1"));
            edges.add(createEdge("e-tool-llm", "tool_1", "llm_1"));
            edges.add(createEdge("e-llm-reply", "llm_1", "reply_1"));
        } else {
            // 默认为标准 tpl_rag_qa (RAG 知识检索问答)
            nodes.add(createNode("start_1", "start", 100, 240, "开始", createStartData()));
            nodes.add(createNode("knowledge_1", "knowledge", 460, 240, "知识库检索", createKnowledgeData("{{ start_1.query }}")));
            nodes.add(createNode("llm_1", "llm", 820, 240, "DeepSeek 推理", createLlmData("请参考以下检索内容：\n{{ knowledge_1.text }}\n\n用户问题：{{ start_1.query }}")));
            nodes.add(createNode("reply_1", "reply", 1180, 240, "直接回复", createReplyData("{{ llm_1.text }}")));

            edges.add(createEdge("e-start-knowledge", "start_1", "knowledge_1"));
            edges.add(createEdge("e-knowledge-llm", "knowledge_1", "llm_1"));
            edges.add(createEdge("e-llm-reply", "llm_1", "reply_1"));
        }

        dsl.put("nodes", nodes);
        dsl.put("edges", edges);
        return dsl;
    }

    private JSONObject createNode(String id, String type, int x, int y, String label, JSONObject data) {
        JSONObject node = new JSONObject();
        node.put("id", id);
        node.put("type", type);
        node.put("label", label);

        JSONObject position = new JSONObject();
        position.put("x", x);
        position.put("y", y);
        node.put("position", position);

        node.put("data", data);
        return node;
    }

    private JSONObject createEdge(String id, String source, String target) {
        JSONObject edge = new JSONObject();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", "default");
        return edge;
    }

    private JSONObject createStartData() {
        JSONObject data = new JSONObject();
        data.put("label", "开始");
        JSONArray fields = new JSONArray();
        JSONObject queryField = new JSONObject();
        queryField.put("id", "f_query");
        queryField.put("name", "query");
        queryField.put("label", "用户输入");
        queryField.put("type", "string");
        queryField.put("required", true);
        fields.add(queryField);
        data.put("fields", fields);
        return data;
    }

    private JSONObject createLlmData(String prompt) {
        JSONObject data = new JSONObject();
        data.put("label", "DeepSeek 推理");
        data.put("platform", "DeepSeek");
        data.put("model", "deepseek-flash");
        data.put("prompt", prompt);
        data.put("temperature", 1.0);
        data.put("max_tokens", 4096);

        JSONObject config = new JSONObject();
        config.put("label", "DeepSeek 推理");
        config.put("platform", "DeepSeek");
        config.put("model", "deepseek-flash");
        config.put("prompt", prompt);
        config.put("promptTemplate", prompt);
        config.put("temperature", 1.0);
        config.put("max_tokens", 4096);
        data.put("config", config);
        return data;
    }

    private JSONObject createKnowledgeData(String query) {
        JSONObject data = new JSONObject();
        data.put("label", "知识库检索");
        JSONObject config = new JSONObject();
        config.put("label", "知识库检索");
        config.put("knowledgeBaseId", null);
        config.put("knowledgeBaseName", "");
        config.put("query", query);
        config.put("topK", 5);
        config.put("scoreThreshold", 0.65);
        data.put("config", config);

        JSONArray input = new JSONArray();
        JSONObject inQuery = new JSONObject();
        inQuery.put("name", "query");
        inQuery.put("label", "检索词");
        inQuery.put("type", "string");
        input.add(inQuery);
        data.put("input", input);

        JSONArray output = new JSONArray();
        JSONObject outText = new JSONObject();
        outText.put("variableKey", "text");
        outText.put("variableLabel", "召回内容");
        outText.put("valueType", "string");
        outText.put("path", "text");
        output.add(outText);

        JSONObject outContext = new JSONObject();
        outContext.put("variableKey", "context");
        outContext.put("variableLabel", "知识切片");
        outContext.put("valueType", "string");
        outContext.put("path", "context");
        output.add(outContext);
        data.put("output", output);

        return data;
    }

    private JSONObject createToolData() {
        JSONObject data = new JSONObject();
        data.put("label", "扩展工具");
        data.put("toolName", "示例工具");
        data.put("description", "执行工具调用获取结构化数据");
        return data;
    }

    private JSONObject createReplyData(String content) {
        JSONObject data = new JSONObject();
        data.put("label", "直接回复");
        data.put("content", content);
        return data;
    }
}
