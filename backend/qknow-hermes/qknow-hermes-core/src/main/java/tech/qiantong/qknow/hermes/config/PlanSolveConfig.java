package tech.qiantong.qknow.hermes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.hermes.plan-solve")
public class PlanSolveConfig {
    private boolean enabled = false;
    private int maxTasks = 5;
    private int maxReflectionRetries = 1;
    private List<String> complexKeywords = new ArrayList<>(List.of(
            "对比", "比较", "分析", "总结", "综合", "分别", "最后", "写成", "财报", "邮件"
    ));
}
