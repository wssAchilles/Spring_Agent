package tech.qiantong.qknow.hermes.config;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Configuration
public class ToolCallbackResolverConfig {

    @Bean
    public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext) {
        return name -> {
            if (!applicationContext.containsBean(name)) {
                return null;
            }
            Object bean = applicationContext.getBean(name);

            // 1. 已经是 ToolCallback，直接返回
            if (bean instanceof ToolCallback toolCallback) {
                return toolCallback;
            }

            // 2. Function bean → 自动包装为 FunctionToolCallback
            if (bean instanceof Function<?, ?> function) {
                return wrapFunction(name, function);
            }

            // 3. BiFunction bean → 自动包装为 FunctionToolCallback
            if (bean instanceof BiFunction<?, ?, ?> biFunction) {
                return wrapBiFunction(name, biFunction);
            }

            // 4. Supplier bean → 自动包装为 FunctionToolCallback
            if (bean instanceof Supplier<?> supplier) {
                return wrapSupplier(name, supplier);
            }

            log.warn("Tool '{}' 存在但类型不支持: {}", name, bean.getClass().getSimpleName());
            return null;
        };
    }

    @SuppressWarnings("unchecked")
    private ToolCallback wrapFunction(String name, Function<?, ?> function) {
        Type[] typeArgs = ((ParameterizedType) function.getClass().getGenericInterfaces()[0]).getActualTypeArguments();
        Class<?> inputType = (Class<?>) typeArgs[0];
        Class<?> outputType = (Class<?>) typeArgs[1];

        String description = getDescriptionFromType(inputType);
        String inputSchema = buildInputSchema(inputType);

        log.info("自动包装工具: {} (Function<{}, {}>)", name, inputType.getSimpleName(), outputType.getSimpleName());

        return FunctionToolCallback.builder(name, (Function<Object, String>) (Object) function)
                .inputType(inputType)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ToolCallback wrapBiFunction(String name, BiFunction<?, ?, ?> biFunction) {
        Type[] typeArgs = ((ParameterizedType) biFunction.getClass().getGenericInterfaces()[0]).getActualTypeArguments();
        Class<?> inputType = (Class<?>) typeArgs[0];
        Class<?> outputType = (Class<?>) typeArgs[2];

        String description = getDescriptionFromType(inputType);
        String inputSchema = buildInputSchema(inputType);

        log.info("自动包装工具: {} (BiFunction<{}, {}, {}>)", name, inputType.getSimpleName(), typeArgs[1].getTypeName(), outputType.getSimpleName());

        return FunctionToolCallback.builder(name, (Function<Object, String>) (Object) biFunction)
                .inputType(inputType)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    private ToolCallback wrapSupplier(String name, Supplier<?> supplier) {
        log.info("自动包装工具: {} (Supplier)", name);
        return FunctionToolCallback.builder(name, (Object input) -> supplier.get().toString())
                .inputType(Object.class)
                .description("Execute " + name)
                .build();
    }

    private String getDescriptionFromType(Class<?> type) {
        JsonClassDescription classDesc = type.getAnnotation(JsonClassDescription.class);
        if (classDesc != null) {
            return classDesc.value();
        }
        return "Tool: " + type.getSimpleName();
    }

    private String buildInputSchema(Class<?> type) {
        StringBuilder schema = new StringBuilder();
        schema.append("{\"type\":\"object\",\"properties\":{");
        Field[] fields = type.getDeclaredFields();
        boolean first = true;
        for (Field field : fields) {
            JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
            JsonPropertyDescription descProp = field.getAnnotation(JsonPropertyDescription.class);
            String fieldName = jsonProp != null && !jsonProp.value().isEmpty() ? jsonProp.value() : field.getName();
            String description = descProp != null ? descProp.value() : field.getName();
            if (!first) schema.append(",");
            schema.append("\"").append(fieldName).append("\":{\"type\":\"string\",\"description\":\"").append(description).append("\"}");
            first = false;
        }
        schema.append("}}");
        return schema.toString();
    }
}
