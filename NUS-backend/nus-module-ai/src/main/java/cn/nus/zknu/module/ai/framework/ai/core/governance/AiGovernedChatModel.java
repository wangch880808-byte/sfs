package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 给模型调用加上熔断、额度和调用日志。每个 AiModelService#getChatModel 调用返回一个新的包装，
 * 绑定当次的模型配置；底层模型实例仍由工厂缓存复用。
 */
public class AiGovernedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final AiModelDO model;
    private final AiCallGovernor governor;

    AiGovernedChatModel(ChatModel delegate, AiModelDO model, AiCallGovernor governor) {
        this.delegate = delegate;
        this.model = model;
        this.governor = governor;
    }

    public ChatModel getDelegate() {
        return delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        AiCallContext context = governor.resolveContext();
        governor.beforeCall(model, context);
        LocalDateTime startTime = LocalDateTime.now();
        long startedAt = System.nanoTime();
        ChatResponse response;
        try {
            response = delegate.call(prompt);
        } catch (RuntimeException | Error ex) {
            governor.onFailure(model, context, startTime, elapsedMillis(startedAt), ex, null);
            throw ex;
        }
        governor.onSuccess(model, context, startTime, elapsedMillis(startedAt), response);
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 上下文在调用 stream 的线程上取，订阅可能发生在其他线程
        AiCallContext context = governor.resolveContext();
        return Flux.defer(() -> {
            governor.beforeCall(model, context);
            LocalDateTime startTime = LocalDateTime.now();
            long startedAt = System.nanoTime();
            AtomicReference<ChatResponse> usageHolder = new AtomicReference<>();
            AtomicBoolean finished = new AtomicBoolean();
            return delegate.stream(prompt)
                    .doOnNext(chunk -> {
                        if (AiCallGovernor.usageOf(chunk) != null) {
                            usageHolder.set(chunk);
                        }
                    })
                    .doOnComplete(() -> {
                        if (finished.compareAndSet(false, true)) {
                            governor.onSuccess(model, context, startTime, elapsedMillis(startedAt), usageHolder.get());
                        }
                    })
                    .doOnError(ex -> {
                        if (finished.compareAndSet(false, true)) {
                            governor.onFailure(model, context, startTime, elapsedMillis(startedAt), ex,
                                    usageHolder.get());
                        }
                    })
                    .doOnCancel(() -> {
                        // 用户中止或下游超时：记为取消，不计入熔断
                        if (finished.compareAndSet(false, true)) {
                            governor.onCancelled(model, context, startTime, elapsedMillis(startedAt),
                                    usageHolder.get());
                        }
                    });
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

}
