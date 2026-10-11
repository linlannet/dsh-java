package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.task.execution.adapter.port.IModelExecutionPort;
import net.linlan.dsh.domain.task.execution.model.valobj.ModelExecutionResultVO;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Adapts the execution-domain port to the unified LLM runtime.
 * Routes through the same adapter chain used by the agent loop.
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 */
@Component
public class ModelExecutionPort implements IModelExecutionPort {

    private final ILlmRuntimePort llmRuntimePort;
    public ModelExecutionPort(ILlmRuntimePort llmRuntimePort) {
        this.llmRuntimePort = llmRuntimePort;
    }

    @Override
    public ModelExecutionResultVO execute(GenerateOptions options) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> content = new AtomicReference<>("");
        AtomicReference<String> responseModel = new AtomicReference<>(options.model());
        AtomicReference<Throwable> error = new AtomicReference<>(null);

        llmRuntimePort.stream(options).subscribe(new Flow.Subscriber<>() {
            /**
             * on Subscribe。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             * @param s s参数。
             */

            private Flow.Subscription subscription;
            @Override public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            /**
             * on Next。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             * @param item item参数。
             */

            }
            @Override public void onNext(StreamChunk item) {
                if (item instanceof StreamChunk.TextDelta delta) {
                    content.updateAndGet(c -> c + delta.text());
                } else if (item instanceof StreamChunk.Finish finish) {
                    latch.countDown();
                    latch.countDown();
                }
            /**
             * on Error。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             * @param t t参数。
             */

            }
            @Override public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            /**
             * on Complete。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             */

            }
            @Override public void onComplete() { latch.countDown(); }
        });

        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                throw new IllegalStateException("model execution timed out for session: " + options.sessionId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model execution interrupted", e);
        }

        if (error.get() != null) {
            throw new IllegalStateException("model execution failed: " + error.get().getMessage(), error.get());
        }

        return new ModelExecutionResultVO(options.provider(), responseModel.get(), content.get());
    }
}
