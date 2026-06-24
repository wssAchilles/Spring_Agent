package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 召回测试调试返回信息
 *
 * @author qknow
 */
@Data
public class RecallDebugRespVO {

    /**
     * 召回结果列表
     */
    private List<RetrieveResultRespVO> results;

    /**
     * 调试信息，包含耗时、意图分析、各路召回数量等
     */
    private Map<String, Object> debugInfo;

    /**
     * 最终注入上下文的预览文本
     */
    private String contextPreview;
}
