package tech.qiantong.qknow.hermes.cognitive.control;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 请求与步级作用域 DeepSeek 思考深度及步级输出预算帽透传 {@link ChatModel} 装饰器
 * （Phase 9.1 支持步级动态努力度切换；Phase 11.1 支持步级 {@code max_completion_tokens} 动态预算帽）。
 * <p>
 * 解决 Spring AI {@code ReactAgent} 下发 {@code ToolCallingChatOptions} 时吞掉 {@code DeepSeekChatOptions} 的问题：
 * 在每次调用底层模型前后挂载 {@link DeepSeekCompatibleChatModel#runWithEffortOverride}，
 * 支持按单次步骤动态解析思考深度与输出帽并写入 API 请求体，同时提取 {@code reasoning_content} 供审计。
 * </p>
 */
public class ReasoningEffortInjectingChatModel implements ChatModel {

    /**
     * Phase 11.1 (ED-SCR) 新增：步级算力与思考预算联合指令。
     */
    public record StepEffortDirective(
            DeepSeekReasoningEffort effort,
            Integer maxCompletionTokensCap
    ) {
        public static StepEffortDirective of(DeepSeekReasoningEffort effort) {
            return new StepEffortDirective(effort, null);
        }
    }

    private final ChatModel delegate;
    private final AtomicReference<DeepSeekReasoningEffort> dynamicEffortRef;
    private final AtomicReference<Integer> dynamicMaxTokensCapRef = new AtomicReference<>(null);
    private final Supplier<DeepSeekReasoningEffort> effortSupplier;
    private final Supplier<Integer> maxTokensCapSupplier;
    private final Consumer<String> reasoningListener;

    public ReasoningEffortInjectingChatModel(ChatModel delegate, DeepSeekReasoningEffort reasoningEffort) {
        this(delegate, reasoningEffort, null);
    }

    public ReasoningEffortInjectingChatModel(
            ChatModel delegate,
            DeepSeekReasoningEffort reasoningEffort,
            Consumer<String> reasoningListener
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate ChatModel 不能为空");
        DeepSeekReasoningEffort initial = Objects.requireNonNull(reasoningEffort, "reasoningEffort 不能为空");
        this.dynamicEffortRef = new AtomicReference<>(initial);
        this.effortSupplier = this.dynamicEffortRef::get;
        this.maxTokensCapSupplier = this.dynamicMaxTokensCapRef::get;
        this.reasoningListener = reasoningListener;
    }

    /**
     * Phase 9.1 新增：支持通过步级动态 {@link Supplier} 在同一多轮会话内按步调节 DeepSeek 思考努力度。
     */
    public ReasoningEffortInjectingChatModel(
            ChatModel delegate,
            Supplier<DeepSeekReasoningEffort> effortSupplier,
            Consumer<String> reasoningListener
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate ChatModel 不能为空");
        Objects.requireNonNull(effortSupplier, "effortSupplier 不能为空");
        this.dynamicEffortRef = new AtomicReference<>(DeepSeekReasoningEffort.LOW);
        this.effortSupplier = () -> {
            DeepSeekReasoningEffort resolved = effortSupplier.get();
            return resolved != null ? resolved : this.dynamicEffortRef.get();
        };
        this.maxTokensCapSupplier = this.dynamicMaxTokensCapRef::get;
        this.reasoningListener = reasoningListener;
    }

    private ReasoningEffortInjectingChatModel(
            ChatModel delegate,
            Supplier<StepEffortDirective> directiveSupplier,
            Consumer<String> reasoningListener,
            boolean directiveMarker
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate ChatModel 不能为空");
        Objects.requireNonNull(directiveSupplier, "directiveSupplier 不能为空");
        this.dynamicEffortRef = new AtomicReference<>(DeepSeekReasoningEffort.LOW);
        this.effortSupplier = () -> {
            StepEffortDirective directive = directiveSupplier.get();
            if (directive != null && directive.effort() != null) {
                return directive.effort();
            }
            return this.dynamicEffortRef.get();
        };
        this.maxTokensCapSupplier = () -> {
            StepEffortDirective directive = directiveSupplier.get();
            if (directive != null && directive.maxCompletionTokensCap() != null && directive.maxCompletionTokensCap() > 0) {
                return directive.maxCompletionTokensCap();
            }
            return this.dynamicMaxTokensCapRef.get();
        };
        this.reasoningListener = reasoningListener;
    }

    /**
     * Phase 11.1 (ED-SCR) 静态工厂：同时按步注入 DeepSeek 思考努力度与动态 {@code max_completion_tokens} 预算帽。
     */
    public static ReasoningEffortInjectingChatModel withDirectiveSupplier(
            ChatModel delegate,
            Supplier<StepEffortDirective> directiveSupplier,
            Consumer<String> reasoningListener
    ) {
        return new ReasoningEffortInjectingChatModel(delegate, directiveSupplier, reasoningListener, true);
    }

    /**
     * 动态更新当前推理装饰器下一跳的 DeepSeek 思考努力度（供步级路由器实时推送）。
     */
    public void updateDynamicEffort(DeepSeekReasoningEffort nextEffort) {
        if (nextEffort != null) {
            this.dynamicEffortRef.set(nextEffort);
        }
    }

    /**
     * Phase 11.1 (ED-SCR) 新增：动态更新下一跳步级输出预算帽（传 {@code null} 清除覆盖）。
     */
    public void updateDynamicMaxTokensCap(Integer nextCap) {
        if (nextCap == null || nextCap <= 0) {
            this.dynamicMaxTokensCapRef.set(null);
        } else {
            this.dynamicMaxTokensCapRef.set(nextCap);
        }
    }

    public Integer getMaxCompletionTokensCap() {
        Integer resolved = maxTokensCapSupplier.get();
        return (resolved != null && resolved > 0) ? resolved : null;
    }

    public DeepSeekReasoningEffort getReasoningEffort() {
        DeepSeekReasoningEffort eff = effortSupplier.get();
        return eff != null ? eff : DeepSeekReasoningEffort.LOW;
    }

    public ChatModel getDelegate() {
        return delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = DeepSeekCompatibleChatModel.runWithEffortOverride(
                toOverride(), () -> delegate.call(prompt));
        captureReasoning(response);
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 关键：ReactAgent 在订阅期间可能惰性构造请求体，因此将上下文覆盖包裹至 delegate.stream(prompt) 构建期
        return Flux.defer(() -> DeepSeekCompatibleChatModel.runWithEffortOverride(
                        toOverride(), () -> delegate.stream(prompt)))
                .doOnNext(this::captureReasoning);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private void captureReasoning(ChatResponse response) {
        if (reasoningListener == null || response == null || response.getResult() == null) {
            return;
        }
        AssistantMessage msg = response.getResult().getOutput();
        if (msg == null || msg.getMetadata() == null) {
            return;
        }
        Object raw = msg.getMetadata().get("reasoning_content");
        if (raw == null) {
            raw = msg.getMetadata().get("reasoningContent");
        }
        if (raw != null) {
            String text = raw.toString();
            if (!text.isEmpty()) {
                reasoningListener.accept(text);
            }
        }
    }

    private DeepSeekCompatibleChatModel.ReasoningEffortOverride toOverride() {
        DeepSeekReasoningEffort current = getReasoningEffort();
        return new DeepSeekCompatibleChatModel.ReasoningEffortOverride(
                current.thinkingEnabled(),
                current.wireValue(),
                getMaxCompletionTokensCap()
        );
    }
}
