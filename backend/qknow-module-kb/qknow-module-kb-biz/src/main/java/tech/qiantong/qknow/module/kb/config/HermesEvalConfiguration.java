package tech.qiantong.qknow.module.kb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

/**
 * 在主后端中仅引入知识库评估所需的 Hermes 组件。
 */
@Configuration
@Import({ChatModelFactory.class, RagasEvalConfig.class, RagasEvaluator.class})
public class HermesEvalConfiguration {
}
