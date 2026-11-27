package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.infrastructure.adapter.llm.LlmAdapterFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeepSeek 流式协议验收")
class DeepSeekAdapterAcceptanceTest {

    @Test
    @DisplayName("路由通过认证提供方签名，内容拒答返回 CONTENT_REFUSED")
    void routeSignsAndMapsStreamingRefusal() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"refusal\"}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            DeepSeekAuthProvider auth = DeepSeekAuthProvider.bearer("provider-key");
            LlmAdapter route = new LlmAdapterFactory(new ObjectMapper(), auth).create("deepseek");
            List<StreamChunk> chunks = collect(route.stream(options(baseUrl)));

            StreamChunk.Finish finish = chunks.stream()
                    .filter(StreamChunk.Finish.class::isInstance)
                    .map(StreamChunk.Finish.class::cast)
                    .reduce((first, second) -> second)
                    .orElseThrow();
            FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
            assertEquals(LlmErrorCodes.CONTENT_REFUSED, error.failure().code());
            assertEquals("Bearer provider-key", authorization.get());
            assertTrue(chunks.stream().anyMatch(chunk -> chunk instanceof StreamChunk.TextDelta delta
                    && "partial".equals(delta.text())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("旧错误码仍用于 malformed SSE，而不是内容拒答")
    void malformedResponseKeepsLegacyCode() {
        assertEquals("MALFORMED_RESPONSE", LlmErrorCodes.MALFORMED_RESPONSE);
        assertNotEquals(LlmErrorCodes.MALFORMED_RESPONSE, LlmErrorCodes.CONTENT_REFUSED);
    }

    private static GenerateOptions options(String baseUrl) {
        return new GenerateOptions("deepseek", "deepseek-chat", null, List.of(), null,
                List.of(), null, null, null, null, null, baseUrl, null, "openai");
    }

    private static List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher) throws Exception {
        List<StreamChunk> chunks = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk item) {
                chunks.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "stream did not complete");
        if (failure.get() != null) {
            fail(failure.get());
        }
        return chunks;
    }
}
