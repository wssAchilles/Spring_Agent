package tech.qiantong.qknow.thirdparty.domain.dify.knowledge;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * 知识库检索条件
 *
 * @author qknow
 * @date 2025-03-17
 */
@Data
public class Retrieve {

    /** 检索关键词 */
     private String query;

    /** 检索方法 */
    private String searchMethod;

    private Boolean rerankingEnable;

    private String rerankingMode;

    private String rerankingProviderName;

    private String rerankingModelName;

    private String weights;

    private Integer topK;

    private Boolean scoreThresholdEnabled;

    private Double scoreThreshold;


    public String toJSONString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("query", query);
        JSONObject retrievalModel = new JSONObject();
        retrievalModel.put("search_method", searchMethod);
        retrievalModel.put("reranking_enable", rerankingEnable);
        retrievalModel.put("reranking_mode", rerankingMode);
        JSONObject rerankingModel = new JSONObject();
        rerankingModel.put("reranking_provider_name", rerankingProviderName);
        rerankingModel.put("reranking_model_name", rerankingModelName);
        retrievalModel.put("reranking_model", rerankingModel);
        retrievalModel.put("weights", weights);
        retrievalModel.put("top_k", topK);
        retrievalModel.put("score_threshold_enabled", scoreThresholdEnabled);
        retrievalModel.put("score_threshold", scoreThreshold);
        jsonObject.put("retrieval_model", retrievalModel);
        return jsonObject.toJSONString();
    }
}
