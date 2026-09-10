package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.Data;

/**
 * Multi-turn chat turn for retrieval query compression (H4b).
 */
@Data
public class KmcChatTurnDTO {
    private String role;
    private String content;

    public KmcChatTurnDTO() {
    }

    public KmcChatTurnDTO(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
