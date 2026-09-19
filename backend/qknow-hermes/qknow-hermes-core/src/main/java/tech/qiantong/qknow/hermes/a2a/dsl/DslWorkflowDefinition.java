package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.List;
import java.util.Map;

/**
 * 声明式多智能体工作流增强规约根模型（支持 JSON 与 YAML 规范化双向互转）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DslWorkflowDefinition(
        String workflowId,
        String name,
        int version,
        String description,
        List<DslWorkflowNode> nodes,
        List<DslWorkflowEdge> edges,
        Map<String, Object> metadata
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DslWorkflowDefinition {
        if (nodes == null) nodes = List.of();
        else nodes = List.copyOf(nodes);
        if (edges == null) edges = List.of();
        else edges = List.copyOf(edges);
        if (metadata == null) metadata = Map.of();
        else metadata = Map.copyOf(metadata);
        if (version <= 0) version = 1;
    }

    /**
     * 从 JSON 文本解析工作流定义
     */
    public static DslWorkflowDefinition fromJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON 内容不能为空");
        }
        return OBJECT_MAPPER.readValue(json, DslWorkflowDefinition.class);
    }

    /**
     * 序列化为规范 JSON 文本
     */
    public String toJson() throws Exception {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }

    /**
     * 从 YAML 文本解析工作流定义（采用 SnakeYAML 安全解析并由 Jackson 映射为强类型 AST）
     */
    @SuppressWarnings("unchecked")
    public static DslWorkflowDefinition fromYaml(String yamlStr) throws Exception {
        if (yamlStr == null || yamlStr.isBlank()) {
            throw new IllegalArgumentException("YAML 内容不能为空");
        }
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
        Object raw = yaml.load(yamlStr);
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("YAML 根节点必须为 Map 结构");
        }
        // 通过 Map 转对象，保证反序列化遵循 Record 规则
        return OBJECT_MAPPER.convertValue(raw, DslWorkflowDefinition.class);
    }

    /**
     * 序列化为规范 YAML 文本
     */
    @SuppressWarnings("unchecked")
    public String toYaml() throws Exception {
        Map<String, Object> map = OBJECT_MAPPER.convertValue(this, Map.class);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(new Representer(options), options);
        return yaml.dump(map);
    }
}
