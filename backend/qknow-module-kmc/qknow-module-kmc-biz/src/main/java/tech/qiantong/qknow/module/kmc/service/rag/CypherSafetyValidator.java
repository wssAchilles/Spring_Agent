package tech.qiantong.qknow.module.kmc.service.rag;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class CypherSafetyValidator {

    private static final Set<String> FORBIDDEN = Set.of(
            "CREATE", "MERGE", "SET", "DELETE", "REMOVE", "CALL", "LOAD", "UNWIND", "FOREACH", "DROP"
    );

    public boolean isReadOnly(String cypher) {
        return validate(cypher, true);
    }

    public boolean isTemplateReadOnly(String cypher) {
        return validate(cypher, false);
    }

    private boolean validate(String cypher, boolean blockUnwind) {
        if (cypher == null || cypher.isBlank() || cypher.contains(";")) {
            return false;
        }
        String normalized = cypher.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//.*", " ")
                .toUpperCase(Locale.ROOT);
        if (!normalized.contains("MATCH") || !normalized.contains("RETURN")) {
            return false;
        }
        for (String token : FORBIDDEN) {
            if (!blockUnwind && "UNWIND".equals(token)) {
                continue;
            }
            if (normalized.matches(".*\\b" + token + "\\b.*")) {
                return false;
            }
        }
        return true;
    }
}
