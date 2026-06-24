package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;

import java.util.*;

@Slf4j
@Component
public class EntityExtractionService {

    static final String METADATA_ENTITIES = "entities";
    static final String METADATA_RELATIONS = "relations";

    private final EntityExtractionStrategy llmStrategy;
    private final EntityExtractionStrategy fallbackStrategy;

    public EntityExtractionService() {
        this(new LlmEntityExtractionStrategy(), new RegexEntityExtractionStrategy());
    }

    EntityExtractionService(EntityExtractionStrategy llmStrategy, EntityExtractionStrategy fallbackStrategy) {
        this.llmStrategy = llmStrategy;
        this.fallbackStrategy = fallbackStrategy;
    }

    public List<Document> enrichParentChildMetadata(List<Document> documents) {
        return enrichParentChildMetadata(documents, null);
    }

    public List<Document> enrichParentChildMetadata(List<Document> documents, ChatModel chatModel) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        Map<String, Map<String, Object>> parentMetadata = new HashMap<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            if (RecursiveSplitter.CHUNK_LEVEL_PARENT.equals(metadata.get(RecursiveSplitter.METADATA_CHUNK_LEVEL))) {
                Map<String, Object> extracted = extract(document.getText(), chatModel);
                metadata.putAll(extracted);
                parentMetadata.put(document.getId(), extracted);
            }
        }

        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String parentId = stringValue(metadata.get(RecursiveSplitter.METADATA_PARENT_SEGMENT_ID));
            if (StrUtil.isNotBlank(parentId) && parentMetadata.containsKey(parentId)) {
                metadata.putAll(parentMetadata.get(parentId));
            }
        }
        return documents;
    }

    private Map<String, Object> extract(String text, ChatModel chatModel) {
        if (chatModel != null) {
            try {
                return llmStrategy.extract(text, chatModel);
            } catch (Exception e) {
                log.warn("LLM entity extraction failed, falling back to regex extraction: {}", e.getMessage());
            }
        }
        return fallbackStrategy.extract(text, null);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
