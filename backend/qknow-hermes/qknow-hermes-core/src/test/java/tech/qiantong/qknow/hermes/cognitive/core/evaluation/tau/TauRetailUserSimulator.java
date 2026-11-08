package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TauRetailUserSimulator {
    private static final java.util.regex.Pattern CONFIRMATION = java.util.regex.Pattern.compile(
            "\\b(?:should i|shall i|can i|may i|would you like me to|do you want me to|please confirm|confirm that|"
                    + "could you confirm|would you confirm|should we proceed|shall we proceed|would you like to proceed|can i proceed)\\b");
    private static final java.util.regex.Pattern INSTRUCTION_RESPONSE = java.util.regex.Pattern.compile(
            "(?i)if asked for ([^,.;]+), say ([^.]+)\\.");

    public Session start(TauRetailTaskLoader.Scenario scenario) {
        return new Session(scenario);
    }

    public static final class Session {
        private final TauRetailTaskLoader.Scenario scenario;
        private final List<String> transcript = new ArrayList<>();

        private Session(TauRetailTaskLoader.Scenario scenario) {
            this.scenario = scenario;
        }

        public String initialMessage() {
            transcript.add("USER: " + scenario.reasonForCall());
            return scenario.reasonForCall();
        }

        public String answer(String agentQuestion) {
            String query = agentQuestion == null ? "" : agentQuestion.toLowerCase(Locale.ROOT);
            String answer;
            if (CONFIRMATION.matcher(query).find()) {
                answer = "Yes, please proceed.";
            } else {
                var instructionResponse = INSTRUCTION_RESPONSE.matcher(scenario.taskInstructions());
                if (instructionResponse.find() && query.contains(instructionResponse.group(1).toLowerCase(Locale.ROOT))) {
                    answer = instructionResponse.group(2).trim();
                } else if (query.contains("name") || query.contains("zip") || query.contains("postal")) {
                    answer = scenario.knownInfo();
                } else if (query.contains("email") || query.contains("forgot") || query.contains("unknown")) {
                    answer = scenario.unknownInfo();
                } else {
                    answer = scenario.knownInfo();
                }
            }
            if (answer == null || answer.isBlank()) {
                answer = "I do not have more information to provide.";
            }
            transcript.add("AGENT: " + agentQuestion);
            transcript.add("USER: " + answer);
            return answer;
        }

        public List<String> transcript() {
            return List.copyOf(transcript);
        }
    }
}
