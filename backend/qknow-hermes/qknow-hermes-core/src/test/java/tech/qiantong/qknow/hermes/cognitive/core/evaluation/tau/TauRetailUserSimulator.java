package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TauRetailUserSimulator {
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
            String answer = query.contains("unknown") || query.contains("forgot")
                    ? scenario.unknownInfo() : scenario.knownInfo();
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
