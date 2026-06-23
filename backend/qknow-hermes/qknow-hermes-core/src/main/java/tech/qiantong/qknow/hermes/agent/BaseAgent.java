package tech.qiantong.qknow.hermes.agent;

/**
 * 智能体抽象基类
 */
public abstract class BaseAgent {

    private final String name;
    private final String description;

    protected BaseAgent(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 执行对话
     *
     * @param question 用户问题
     * @param context  上下文（可包含 history 等）
     * @return 回答文本
     */
    public abstract String chat(String question, java.util.Map<String, Object> context);
}
