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
    private boolean rpReactEnabled = true;
    private int maxTasks = 5;
    private int maxReflectionRetries = 3;
    private int maxConcurrentPlanTasks = 4;
    private int maxConcurrentReactRuns = 16;
    private int maxLlmConcurrent = 8;
    private int maxTokenBudget = 0;
    // [溯源] 算法优化指南 §4.2: 增强复杂问题检测
    private List<String> complexKeywords = new ArrayList<>(List.of(
            "对比", "比较", "分析", "总结", "综合", "分别", "最后", "写成", "财报", "邮件",
            "区别", "异同", "优劣", "权衡", "评估", "推荐", "方案"
    ));
}
