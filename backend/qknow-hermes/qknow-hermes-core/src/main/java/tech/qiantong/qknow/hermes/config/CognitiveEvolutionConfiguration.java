package tech.qiantong.qknow.hermes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;

import java.nio.file.Path;

@Configuration
public class CognitiveEvolutionConfiguration {
    @Bean
    public ExperienceStore cognitiveExperienceStore(
            @Value("${qknow.hermes.cognitive-evolution.experience-root:${user.home}/.qknow/hermes/experience}")
            String experienceRoot) {
        return new FileSystemExperienceStore(Path.of(experienceRoot));
    }

    @Bean
    public ExperienceLibrary cognitiveExperienceLibrary(ExperienceStore store) {
        return new DefaultExperienceLibrary(store);
    }
}
