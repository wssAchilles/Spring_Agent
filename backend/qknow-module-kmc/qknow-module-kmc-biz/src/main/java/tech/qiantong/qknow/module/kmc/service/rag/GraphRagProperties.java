package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.rag.graph")
public class GraphRagProperties {
    private boolean enabled = false;
    private int maxHops = 2;
    private int topK = 20;
    // [溯源] 算法优化指南 §3.3: HippoRAG PPR 开关
    private boolean pprEnabled = true;
}
