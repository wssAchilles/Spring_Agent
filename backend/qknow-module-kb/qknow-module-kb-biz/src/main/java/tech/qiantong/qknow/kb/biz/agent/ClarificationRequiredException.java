package tech.qiantong.qknow.kb.biz.agent;

import lombok.Getter;

import java.util.List;

@Getter
public class ClarificationRequiredException extends RuntimeException {
    
    private final List<String> options;

    public ClarificationRequiredException(String message, List<String> options) {
        super(message);
        this.options = options;
    }
}
