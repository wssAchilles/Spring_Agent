package tech.qiantong.qknow.module.kmc.service.rag;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

interface EntityExtractionStrategy {

    Map<String, Object> extract(String text, ChatModel chatModel);
}
