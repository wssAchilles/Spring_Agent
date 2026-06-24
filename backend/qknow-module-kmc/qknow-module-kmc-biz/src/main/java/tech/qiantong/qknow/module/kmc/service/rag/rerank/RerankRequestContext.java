package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequestContext {

    private String query;

    private Long providerName;

    private String modelName;
}
