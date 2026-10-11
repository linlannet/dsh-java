package net.linlan.dsh.domain.llm.service;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.waterfall.LlmStreamWaterfall;
import net.linlan.dsh.domain.llm.waterfall.LlmReplayInterceptor;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmGenerationService waterfall 接入")
class LlmGenerationServiceTest {

    @Test
    @DisplayName("无拦截器时行为与 v0.1.8 一致：完整流式生成返回正确 Message")
    void noInterceptorsBackwardCompat() {
        var chunks = List.<StreamChunk>of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "Hello"),
                new StreamChunk.TextDelta(0, " World"),
                new StreamChunk.BlockEnd(0, new TextBlock("Hello World")),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<>(chunks));
        var service = new LlmGenerationService(runtime);

        var message = service.generate(defaultOptions("provider-a", "model-a")).join();

        assertNotNull(message);
        assertEquals("assistant", message.role());
        assertEquals(1, message.content().size());
        assertInstanceOf(TextBlock.class, message.content().get(0));
        assertEquals("Hello World", ((TextBlock) message.content().get(0)).text());
        assertInstanceOf(MessageSource.ModelMessageSource.class, message.source());
        var src = (MessageSource.ModelMessageSource) message.source();
        assertEquals("provider-a", src.provider());
        assertEquals("model-a", src.model());
    }

    @Test
    @DisplayName("waterfall 包装后拦截器可介入：Replay 注入前置 chunk")
    void interceptorCanIntervene() {
        var liveChunks = List.<StreamChunk>of(
                new StreamChunk.BlockStart(1, "text"),
                new StreamChunk.TextDelta(1, "live"),
                new StreamChunk.BlockEnd(1, new TextBlock("live")),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var replayChunks = List.<StreamChunk>of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "replayed-"),
                new StreamChunk.BlockEnd(0, new TextBlock("replayed-"))
        );
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<>(liveChunks));
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmReplayInterceptor(opts -> replayChunks)
        ));
        var service = new LlmGenerationService(waterfall);

        var message = service.generate(defaultOptions("p", "m")).join();

        assertNotNull(message);
        assertEquals(2, message.content().size());
        assertEquals("replayed-", ((TextBlock) message.content().get(0)).text());
        assertEquals("live", ((TextBlock) message.content().get(1)).text());
    }

    @Test
    @DisplayName("waterfall() 访问器返回底层 waterfall 实例")
    void waterfallAccessor() {
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<>(List.<StreamChunk>of(
                new StreamChunk.Finish(new FinishReason.Stop())
        )));
        var service = new LlmGenerationService(runtime);

        var wf = service.waterfall();
        assertNotNull(wf);
        assertEquals(0, wf.interceptorCount());
    }

    @Test
    @DisplayName("waterfall() 返回的实例可动态注册拦截器")
    void dynamicInterceptorRegistration() {
        var callCount = new AtomicInteger(0);
        var runtime = new StubLlmRuntimePort(opts -> {
            callCount.incrementAndGet();
            return new ListPublisher<>(List.<StreamChunk>of(
                    new StreamChunk.BlockStart(1, "text"),
                    new StreamChunk.TextDelta(1, "x"),
                    new StreamChunk.BlockEnd(1, new TextBlock("x")),
                    new StreamChunk.Finish(new FinishReason.Stop())
            ));
        });
        var service = new LlmGenerationService(runtime);
        service.waterfall().registerInterceptor(
                new LlmReplayInterceptor(opts -> List.<StreamChunk>of(
                        new StreamChunk.BlockStart(0, "text"),
                        new StreamChunk.TextDelta(0, "prefix-"),
                        new StreamChunk.BlockEnd(0, new TextBlock("prefix-"))
                ))
        );

        var message = service.generate(defaultOptions("p", "m")).join();

        assertNotNull(message);
        assertEquals(1, callCount.get());
        assertEquals(2, message.content().size());
        assertEquals("prefix-", ((TextBlock) message.content().get(0)).text());
    }

    @Test
    @DisplayName("流式错误经 waterfall 传播为 CompletableFuture 异常")
    void streamErrorPropagated() {
        var runtime = new StubLlmRuntimePort(opts -> new ErrorPublisher<StreamChunk>(
                new RuntimeException("stream failed")
        ));
        var service = new LlmGenerationService(runtime);

        var future = service.generate(defaultOptions("p", "m"));
        var ex = assertThrows(java.util.concurrent.CompletionException.class, future::join);
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("stream failed"));
    }

    // ── Helpers ───────────────────────────────────────────────

    private static GenerateOptions defaultOptions(String provider, String model) {
        return new GenerateOptions(
                provider, model, null,
                List.of(Message.createUser(List.of(), new MessageSource.UserSource())),
                null, null, null, null, null,
                "session-1", "test", null, null, null
        );
    }

    // ── Stubs ─────────────────────────────────────────────────

    private static final class StubLlmRuntimePort implements ILlmRuntimePort {
        private final Function<GenerateOptions, Flow.Publisher<StreamChunk>> streamFn;

        StubLlmRuntimePort(Function<GenerateOptions, Flow.Publisher<StreamChunk>> streamFn) {
            this.streamFn = streamFn;
        }

        @Override public void registerAdapter(String provider, LlmAdapter adapter) { }
        @Override public void unregisterAdapter(String provider) { }
        @Override public List<LlmAdapter.LlmProviderInfo> listProviders() { return List.of(); }
        @Override public List<LlmAdapter.LlmModelInfo> listModels(String provider) { return List.of(); }
        @Override public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) { return null; }
        @Override public Flow.Publisher<StreamChunk> stream(GenerateOptions options) { return streamFn.apply(options); }
    }

    private static final class ListPublisher<T> implements Flow.Publisher<T> {
        private final List<T> items;
        ListPublisher(List<T> items) { this.items = items; }
        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int index = 0;
                @Override public void request(long n) {
                    for (long i = 0; i < n && index < items.size(); i++) {
                        subscriber.onNext(items.get(index++));
                    }
                    if (index >= items.size()) {
                        subscriber.onComplete();
                    }
                }
                @Override public void cancel() { }
            });
        }
    }

    private static final class ErrorPublisher<T> implements Flow.Publisher<T> {
        private final Throwable error;
        ErrorPublisher(Throwable error) { this.error = error; }
        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) { subscriber.onError(error); }
                @Override public void cancel() { }
            });
        }
    }
}
