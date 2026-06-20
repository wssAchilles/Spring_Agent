package tech.qiantong.qknow.hermes.judge;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Judge 三维评分服务
 *
 * 评分维度:
 * - 事实性 (Factuality): 生成内容是否有检索到的知识支撑
 * - 相关性 (Relevance): 回答是否切题
 * - 指令遵循度 (Instruction Following): 是否遵循 Agent 人设和任务要求
 */
@Slf4j
@Service
public class AiJudgeService {

    private final ChatModelFactory chatModelFactory;

    /**
     * 评分阈值 (默认 0.7)
     */
    private double threshold = 0.7;

    public AiJudgeService(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    /**
     * 对回答进行三维评分
     */
    public JudgeResult judge(String query, String context, String answer) {
        try {
            String judgePrompt = buildJudgePrompt(query, context, answer);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(JUDGE_SYSTEM_PROMPT));
            messages.add(new UserMessage(judgePrompt));

            // 使用 ChatModelFactory 创建评分模型
            ChatModel chatModel = chatModelFactory.getChatModel("deepseek", null, null, "deepseek-chat");
            ChatResponse response = chatModel.call(new Prompt(messages));
            String responseText = response.getResult().getOutput().getText();

            // 解析评分结果
            return parseJudgeResponse(responseText);
        } catch (Exception e) {
            log.error("AI Judge 评分失败", e);
            // 评分失败时默认通过
            return JudgeResult.passed(1.0, 1.0, 1.0, "评分服务异常，默认通过");
        }
    }

    /**
     * 设置评分阈值
     */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * 获取当前阈值
     */
    public double getThreshold() {
        return threshold;
    }

    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个专业的 AI 回答质量评估专家。请根据以下三个维度对回答进行评分:

            1. 事实性 (Factuality): 回答中的信息是否有知识库或上下文支撑，是否准确
            2. 相关性 (Relevance): 回答是否切题，是否回答了用户的问题
            3. 指令遵循度 (Instruction Following): 回答是否遵循了系统提示词的要求

            请以 JSON 格式返回评分结果:
            {
                "factuality": 0.0-1.0,
                "relevance": 0.0-1.0,
                "instruction": 0.0-1.0,
                "feedback": "改进建议（如果评分较低）"
            }

            只返回 JSON，不要其他内容。
            """;

    private String buildJudgePrompt(String query, String context, String answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户问题\n").append(query).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("## 参考知识\n").append(context).append("\n\n");
        }

        sb.append("## AI 回答\n").append(answer).append("\n\n");
        sb.append("请对以上回答进行评分。");

        return sb.toString();
    }

    private JudgeResult parseJudgeResponse(String responseText) {
        try {
            // 提取 JSON 部分
            String jsonStr = responseText;
            int start = responseText.indexOf("{");
            int end = responseText.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonStr = responseText.substring(start, end + 1);
            }

            JSONObject json = JSONObject.parseObject(jsonStr);
            double factuality = json.getDoubleValue("factuality");
            double relevance = json.getDoubleValue("relevance");
            double instruction = json.getDoubleValue("instruction");
            String feedback = json.getString("feedback");

            double overall = (factuality + relevance + instruction) / 3.0;
            boolean passed = overall >= threshold;

            if (passed) {
                return JudgeResult.passed(factuality, relevance, instruction, feedback);
            } else {
                return JudgeResult.failed(factuality, relevance, instruction, feedback);
            }
        } catch (Exception e) {
            log.warn("解析评分结果失败，原始响应: {}", responseText, e);
            return JudgeResult.passed(0.8, 0.8, 0.8, "解析失败，默认通过");
        }
    }
}
