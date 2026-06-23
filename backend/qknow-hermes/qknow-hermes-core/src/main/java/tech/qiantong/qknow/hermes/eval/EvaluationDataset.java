package tech.qiantong.qknow.hermes.eval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
public class EvaluationDataset {
    private String name;
    private Long knowledgeBaseId;
    private List<EvalItem> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvalItem {
        private String query;
        private String expectedAnswer;
        private List<String> groundTruthContexts;
    }

    public String toJson() {
        return com.alibaba.fastjson2.JSON.toJSONString(this);
    }

    public static EvaluationDataset loadFromJson(String json) {
        return com.alibaba.fastjson2.JSON.parseObject(json, EvaluationDataset.class);
    }
}
