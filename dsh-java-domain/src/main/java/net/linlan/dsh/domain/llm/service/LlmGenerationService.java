package net.linlan.dsh.domain.llm.service;

import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.waterfall.LlmStreamWaterfall;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Synchronous helper for assembling a complete LLM response from a stream.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class LlmGenerationService {

    private final LlmStreamWaterfall waterfall;

    /**
     * 处理LLM Generation Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param llmRuntimePort LLM 运行时 Port参数。
     * @return 方法执行结果。
     */
    public LlmGenerationService(ILlmRuntimePort llmRuntimePort) {
        this.waterfall = new LlmStreamWaterfall(llmRuntimePort);
    }

    /**
     * 处理LLM Generation Service（带 waterfall）。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param waterfall LLM 流式 waterfall参数。
     * @return 方法执行结果。
     */
    public LlmGenerationService(LlmStreamWaterfall waterfall) {
        this.waterfall = waterfall;
    }

    /**
     * 返回底层 waterfall，供外部注册拦截器。
     * @return LlmStreamWaterfall 实例。
     */
    public LlmStreamWaterfall waterfall() {
        return waterfall;
    }

    /**
     * 处理generate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param options Agent 配置。
     * @return 方法执行结果。
     */
    public CompletableFuture<Message> generate(GenerateOptions options) {
        var assembler = new BlockAssembler();
        var done = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();

        waterfall.execute(options).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk item) {
                assembler.push(item);
            }
            /**
             * on Error。
             * 流程：应用领域规则 → 更新领域对象 → 返回结果。
             * @param throwable throwable参数。
             */
            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }
            /**
             * on Complete。
             * 流程：应用领域规则 → 更新领域对象 → 返回结果。
             */
            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        return CompletableFuture.supplyAsync(() -> {
            try {
                done.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
            return assembler.message(new MessageSource.ModelMessageSource(options.provider(), options.model()));
        });
    }
}
