package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class RegexEntityExtractionStrategy implements EntityExtractionStrategy {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("Day\\s*\\d{1,2}|[A-Za-z][A-Za-z0-9_-]{2,}|[\\p{IsHan}]{2,8}");

    @Override
    public Map<String, Object> extract(String text, ChatModel chatModel) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        Matcher matcher = ENTITY_PATTERN.matcher(StrUtil.blankToDefault(text, ""));
        while (matcher.find() && entities.size() < 32) {
            String entity = matcher.group().trim();
            if (entity.length() >= 2) {
                entities.add(entity);
            }
        }

        return Map.of(
                EntityExtractionService.METADATA_ENTITIES, new ArrayList<>(entities),
                EntityExtractionService.METADATA_RELATIONS, entities.stream()
                        .limit(8)
                        .map(entity -> Map.of("entity", entity, "relation", "mentioned_in_parent_chunk"))
                        .collect(Collectors.toList())
        );
    }
}
