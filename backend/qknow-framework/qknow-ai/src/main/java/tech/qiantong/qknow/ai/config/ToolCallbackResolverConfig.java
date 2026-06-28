package tech.qiantong.qknow.ai.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author wang
 */
@Slf4j
@Configuration
public class ToolCallbackResolverConfig {

    @Resource
    private GenericApplicationContext applicationContext;

    @Bean
    public ToolCallbackResolver toolCallbackResolver() {
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

        log.info("自动包装工具: {} (Function<{}, {}>)", name, inputType.getSimpleName(), typeArgs[1].getTypeName());

        return FunctionToolCallback.builder(name, (Function<Object, String>) (Object) function)
                .inputType(inputType)
                .description("Tool: " + name)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ToolCallback wrapBiFunction(String name, BiFunction<?, ?, ?> biFunction) {
        Type[] typeArgs = ((ParameterizedType) biFunction.getClass().getGenericInterfaces()[0]).getActualTypeArguments();
        Class<?> inputType = (Class<?>) typeArgs[0];

        log.info("自动包装工具: {} (BiFunction)", name);

        return FunctionToolCallback.builder(name, (Function<Object, String>) (Object) biFunction)
                .inputType(inputType)
                .description("Tool: " + name)
                .build();
    }

    private ToolCallback wrapSupplier(String name, Supplier<?> supplier) {
        log.info("自动包装工具: {} (Supplier)", name);
        return FunctionToolCallback.builder(name, (Object input) -> supplier.get().toString())
                .inputType(Object.class)
                .description("Execute " + name)
                .build();
    }
}
