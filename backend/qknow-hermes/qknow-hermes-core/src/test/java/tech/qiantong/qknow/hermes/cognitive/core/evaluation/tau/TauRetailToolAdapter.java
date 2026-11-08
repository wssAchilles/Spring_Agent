package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TauRetailToolAdapter implements ToolCallbackResolver {
    private static final List<String> SMOKE_TOOL_NAMES = List.of(
            "calculate", "cancel_pending_order", "exchange_delivered_order_items",
            "find_user_id_by_name_zip", "get_order_details", "get_product_details",
            "get_user_details", "modify_pending_order_address", "modify_pending_order_items",
            "modify_user_address");
    private static final Map<String, ToolSpec> SPECS = specs();

    private final TauRetailTools tools;
    private final Map<String, ToolCallback> callbacks;
    private final List<ToolInvocation> invocations = new CopyOnWriteArrayList<>();

    public TauRetailToolAdapter(TauRetailTools tools) {
        this(tools, SMOKE_TOOL_NAMES);
    }

    public TauRetailToolAdapter(TauRetailTools tools, List<String> names) {
        this.tools = java.util.Objects.requireNonNull(tools, "tools");
        Map<String, ToolCallback> built = new LinkedHashMap<>();
        names.stream().distinct().sorted().forEach(name -> {
            ToolSpec spec = SPECS.get(name);
            if (spec == null) {
                throw new IllegalArgumentException("unsupported tau smoke tool: " + name);
            }
            built.put(name, callback(spec));
        });
        callbacks = Map.copyOf(built);
    }

    public static List<String> smokeToolNames() {
        return SMOKE_TOOL_NAMES;
    }

    public List<ToolCallback> callbacks() {
        return List.copyOf(callbacks.values());
    }

    public List<String> toolNames() {
        return callbacks.keySet().stream().sorted().toList();
    }

    public synchronized List<ToolInvocation> invocations() {
        return List.copyOf(invocations);
    }

    @Override
    public ToolCallback resolve(String name) {
        return callbacks.get(name);
    }

    private ToolCallback callback(ToolSpec spec) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(spec.name()).description(spec.description())
                        .inputSchema(spec.inputSchema()).build();
            }

            @Override
            public String call(String input) {
                JSONObject arguments;
                try {
                    arguments = input == null || input.isBlank() ? new JSONObject() : JSON.parseObject(input);
                } catch (RuntimeException invalidJson) {
                    arguments = new JSONObject();
                }
                TauRetailTools.ToolResult result;
                synchronized (tools) {
                    result = tools.call(new TauRetailTaskLoader.ToolCall(spec.name(), arguments));
                    invocations.add(new ToolInvocation(spec.name(), arguments, result.success(),
                            result.errorCode(), result.output()));
                }
                JSONObject response = new JSONObject();
                response.put("status", result.success() ? "success" : "error");
                if (result.success()) {
                    response.put("result", result.output());
                } else {
                    response.put("errorCode", result.errorCode());
                }
                return response.toJSONString();
            }
        };
    }

    private static Map<String, ToolSpec> specs() {
        Map<String, ToolSpec> result = new LinkedHashMap<>();
        add(result, "calculate", "Calculate a numeric expression.",
                "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}},\"required\":[\"expression\"],\"additionalProperties\":false}");
        add(result, "cancel_pending_order", "Cancel a pending order.",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\"},\"reason\":{\"type\":\"string\",\"enum\":[\"no longer needed\",\"ordered by mistake\"]}},\"required\":[\"order_id\",\"reason\"],\"additionalProperties\":false}");
        add(result, "exchange_delivered_order_items", "Exchange items in a delivered order.",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\"},\"item_ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"new_item_ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"payment_method_id\":{\"type\":\"string\"}},\"required\":[\"order_id\",\"item_ids\",\"new_item_ids\",\"payment_method_id\"],\"additionalProperties\":false}");
        add(result, "find_user_id_by_name_zip", "Find a customer by their name and postal code.",
                "{\"type\":\"object\",\"properties\":{\"first_name\":{\"type\":\"string\"},\"last_name\":{\"type\":\"string\"},\"zip\":{\"type\":\"string\"}},\"required\":[\"first_name\",\"last_name\",\"zip\"],\"additionalProperties\":false}");
        add(result, "get_order_details", "Get the current details for an order.",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\"}},\"required\":[\"order_id\"],\"additionalProperties\":false}");
        add(result, "get_product_details", "Get product and available variant details.",
                "{\"type\":\"object\",\"properties\":{\"product_id\":{\"type\":\"string\"}},\"required\":[\"product_id\"],\"additionalProperties\":false}");
        add(result, "get_user_details", "Get customer details and payment methods.",
                "{\"type\":\"object\",\"properties\":{\"user_id\":{\"type\":\"string\"}},\"required\":[\"user_id\"],\"additionalProperties\":false}");
        add(result, "modify_user_address", "Update a customer's address.",
                "{\"type\":\"object\",\"properties\":{\"user_id\":{\"type\":\"string\"},\"address1\":{\"type\":\"string\"},\"address2\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"country\":{\"type\":\"string\"},\"state\":{\"type\":\"string\"},\"zip\":{\"type\":\"string\"}},\"required\":[\"user_id\",\"address1\",\"address2\",\"city\",\"country\",\"state\",\"zip\"],\"additionalProperties\":false}");
        add(result, "modify_pending_order_address", "Update the shipping address for a pending order.",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\"},\"address1\":{\"type\":\"string\"},\"address2\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"country\":{\"type\":\"string\"},\"state\":{\"type\":\"string\"},\"zip\":{\"type\":\"string\"}},\"required\":[\"order_id\",\"address1\",\"address2\",\"city\",\"country\",\"state\",\"zip\"],\"additionalProperties\":false}");
        add(result, "modify_pending_order_items", "Update items in a pending order.",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\"},\"item_ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"new_item_ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"order_id\",\"item_ids\",\"new_item_ids\"],\"additionalProperties\":false}");
        return Map.copyOf(result);
    }

    private static void add(Map<String, ToolSpec> target, String name, String description, String schema) {
        target.put(name, new ToolSpec(name, description, schema));
    }

    public record ToolInvocation(String name, JSONObject arguments, boolean success, String errorCode,
                                 JSONObject output) {
        public ToolInvocation {
            arguments = arguments == null ? new JSONObject() : JSON.parseObject(arguments.toJSONString());
            output = output == null ? new JSONObject() : JSON.parseObject(output.toJSONString());
        }
    }

    private record ToolSpec(String name, String description, String inputSchema) { }
}
