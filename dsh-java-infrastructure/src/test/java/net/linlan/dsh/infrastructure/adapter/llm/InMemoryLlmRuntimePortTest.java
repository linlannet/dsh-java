package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.LlmFailure;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class InMemoryLlmRuntimePortTest {

    @Test
    void retriesRetryableFailureAndExposesRetryNumber() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ResolvedRetryPolicy policy = new ResolvedRetryPolicy.Normal(
                1, 10, 0, 2, List.of(LlmErrorCodes.SERVER)
        );
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(policy, () -> {
            if (attempts.incrementAndGet() == 1) {
                return failureChunks("upstream unavailable", LlmErrorCodes.SERVER);
            }
            return successChunks("ok");
        }));

        List<StreamChunk> chunks = collect(runtime.stream(options()));

        assertEquals(2, attempts.get());
        StreamChunk.Retry retry = assertInstanceOf(StreamChunk.Retry.class, chunks.get(0));
        assertEquals(1, retry.attempt());
        assertEquals(2, retry.maxAttempts());
        assertEquals("upstream unavailable", retry.failureMessage());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(chunks.size() - 1));
        assertTrue(finish.reason() instanceof FinishReason.Stop);
    }

    @Test
    void reportsRetryCountWhenBudgetIsExhausted() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ResolvedRetryPolicy policy = new ResolvedRetryPolicy.Normal(
                1, 10, 0, 2, List.of(LlmErrorCodes.SERVER)
        );
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(policy, () -> {
            attempts.incrementAndGet();
            return failureChunks("upstream unavailable", LlmErrorCodes.SERVER);
        }));

        List<StreamChunk> chunks = collect(runtime.stream(options()));

        assertEquals(3, attempts.get());
        assertEquals(2, chunks.stream().filter(StreamChunk.Retry.class::isInstance).count());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(chunks.size() - 1));
        FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertEquals("upstream unavailable（已重试 2 次）", error.failure().message());
    }

    private GenerateOptions options() {
        var messages = List.of(Message.createUser(
                List.of(new TextBlock("hello")),
                new MessageSource.UserSource()
        ));
        return new GenerateOptions(
                "test",
                "test-model",
                null,
                messages,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private List<StreamChunk> chunks(StreamChunk... values) {
        return List.of(values);
    }

    private List<StreamChunk> successChunks(String content) {
        return chunks(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, content),
                new StreamChunk.BlockEnd(0, new TextBlock(content)),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
    }

    private List<StreamChunk> failureChunks(String message, String code) {
        return chunks(new StreamChunk.Finish(new FinishReason.Error(new LlmFailure(message, code))));
    }

    private List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<StreamChunk> chunks = new CopyOnWriteArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override public void onNext(StreamChunk item) {
                chunks.add(item);
            }

            @Override public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(2, TimeUnit.SECONDS));
        return chunks;
    }

    @FunctionalInterface
    private interface ChunkFactory {
        List<StreamChunk> get();
    }

    private static final class FakeAdapter implements LlmAdapter {
        private final ResolvedRetryPolicy policy;
        private final ChunkFactory factory;

        private FakeAdapter(ResolvedRetryPolicy policy, ChunkFactory factory) {
            this.policy = policy;
            this.factory = factory;
        }

        @Override public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            List<StreamChunk> chunks = factory.get();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                chunks.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        @Override public List<LlmModelInfo> listModels(String provider) {
            return List.of();
        }

        @Override public LlmResolvedModelInfo resolveModel(String provider, String model) {
            throw new UnsupportedOperationException();
        }

        @Override public LlmProviderInfo providerInfo(String provider) {
            return new LlmProviderInfo(provider, provider);
        }

        @Override public String providerId() {
            return "test";
        }

        @Override public ResolvedRetryPolicy retryPolicy(String provider) {
            return policy;
        }
    }
}
