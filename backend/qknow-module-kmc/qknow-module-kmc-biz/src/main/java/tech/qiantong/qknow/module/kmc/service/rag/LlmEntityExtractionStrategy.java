package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

class LlmEntityExtractionStrategy implements EntityExtractionStrategy {

    private static final String SYSTEM_PROMPT = """
            You extract entities and relations from parent document chunks for retrieval metadata.
            Return only one raw JSON object. Do not return Markdown, code fences, or explanations outside JSON.
            Schema:
            {"entities":["entity"],"relations":[{"source":"entity","relation":"relationship","target":"entity","evidence":"short evidence"}]}
            Keep entities concise. Use the same language as the source text.
            """;

    @Override
    public Map<String, Object> extract(String text, ChatModel chatModel) {
        if (chatModel == null || StrUtil.isBlank(text)) {
            throw new IllegalArgumentException("ChatModel and text are required for LLM entity extraction");
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage("Parent chunk:\n" + text));

        ChatResponse response = chatModel.call(new Prompt(messages));
        String responseText = response.getResult().getOutput().getText();
        JSONObject json = JSON.parseObject(extractJsonObject(stripMarkdownFence(responseText)));
        return Map.of(
                EntityExtractionService.METADATA_ENTITIES, parseEntities(json.getJSONArray("entities")),
                EntityExtractionService.METADATA_RELATIONS, parseRelations(json.getJSONArray("relations"))
        );
    }

    private List<String> parseEntities(JSONArray array) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        if (array == null) {
            return List.of();
        }
        for (Object item : array) {
            String entity = StrUtil.blankToDefault(String.valueOf(item), "").trim();
            if (entity.length() >= 2) {
                entities.add(entity);
            }
            if (entities.size() >= 32) {
                break;
            }
        }
        return new ArrayList<>(entities);
    }

    private List<Map<String, Object>> parseRelations(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<Map<String, Object>> relations = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JSONObject relation) {
                relations.add(Map.of(
                        "source", StrUtil.blankToDefault(relation.getString("source"), ""),
                        "relation", StrUtil.blankToDefault(relation.getString("relation"), ""),
                        "target", StrUtil.blankToDefault(relation.getString("target"), ""),
                        "evidence", StrUtil.blankToDefault(relation.getString("evidence"), "")
                ));
            }
            if (relations.size() >= 16) {
                break;
            }
        }
        return relations;
    }

    private String stripMarkdownFence(String text) {
        String stripped = StrUtil.blankToDefault(text, "").trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("LLM entity extraction response is not a JSON object");
        }
        return text.substring(start, end + 1);
    }
}
