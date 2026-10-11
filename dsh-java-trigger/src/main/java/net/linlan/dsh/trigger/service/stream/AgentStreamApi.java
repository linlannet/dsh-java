package net.linlan.dsh.trigger.service.stream;

import net.linlan.dsh.api.IAgentApi;
import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.api.stream.IAgentStreamApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentStreamApi implements IAgentStreamApi {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamApi.class);
    private static final long SSE_TIMEOUT_MS = 0L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final IAgentApi agentApi;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    public AgentStreamApi(IAgentApi agentApi, ObjectMapper objectMapper) {
        this.agentApi = agentApi;
        this.objectMapper = objectMapper;
        this.sseExecutor = Executors.newCachedThreadPool(
                runnable -> {
                    Thread thread = new Thread(runnable, "sse-stream-worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "sse-heartbeat");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @Override
    public SseEmitter sendMessageStreaming(AgentMessageRequestDTO request) {
        log.info("[SSE] 收到流式请求 agentId={} channel={}",
                request.agentId(), request.channelCode());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> emitterClosed.set(true));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitterClosed.set(true));

        sseExecutor.execute(() -> emitAgentStream(request, emitter, emitterClosed));
        return emitter;
    }

    private void emitAgentStream(
            AgentMessageRequestDTO request,
            SseEmitter emitter,
            AtomicBoolean emitterClosed
    ) {
        AtomicInteger chunkIndex = new AtomicInteger(0);
        DeltaBatch textBatch = new DeltaBatch(content -> sendDelta(emitter, emitterClosed, chunkIndex, content));
        DeltaBatch reasoningBatch = new DeltaBatch(content -> sendReasoning(emitter, emitterClosed, content));
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, emitterClosed);
        try {
            sendMeta(request, emitter);
            Consumer<String> deltaSink = textBatch::accept;
            Consumer<String> reasoningSink = reasoningBatch::accept;
            Consumer<AgentToolCallEventResponseDTO> toolCallSink = event -> {
                textBatch.flush();
                reasoningBatch.flush();
                sendToolCall(emitter, emitterClosed, event);
            };
            Runnable finishSink = () -> {
                textBatch.flush();
                reasoningBatch.flush();
                sendFinish(emitter, emitterClosed);
            };

            Response<AgentMessageResponseDTO> wrappedResponse =
                    agentApi.sendMessageStreaming(request, deltaSink, reasoningSink, toolCallSink, finishSink);
            if (wrappedResponse.data() == null) {
                throw new IllegalStateException(wrappedResponse.info());
            }
            textBatch.flush();
            reasoningBatch.flush();
            sendDone(emitter, emitterClosed, wrappedResponse.data(), chunkIndex.get());
        } catch (Exception exception) {
            textBatch.flush();
            reasoningBatch.flush();
            sendError(emitter, emitterClosed, exception);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, AtomicBoolean emitterClosed) {
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (emitterClosed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception ignored) {
                emitterClosed.set(true);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void sendMeta(AgentMessageRequestDTO request, SseEmitter emitter) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("agentId", request.agentId());
        emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));
    }

    private void sendDelta(
            SseEmitter emitter,
            AtomicBoolean emitterClosed,
            AtomicInteger chunkIndex,
            String delta
    ) {
        if (emitterClosed.get()) {
            return;
        }
        try {
            Map<String, Object> chunkData = new LinkedHashMap<>();
            chunkData.put("content", delta);
            chunkData.put("index", chunkIndex.getAndIncrement());
            emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunkData)));
        } catch (IOException exception) {
            emitterClosed.set(true);
        }
    }

    private void sendReasoning(
            SseEmitter emitter,
            AtomicBoolean emitterClosed,
            String delta
    ) {
        if (emitterClosed.get()) return;
        try {
            Map<String, Object> reasoningData = new LinkedHashMap<>();
            reasoningData.put("content", delta);
            emitter.send(SseEmitter.event().name("reasoning").data(objectMapper.writeValueAsString(reasoningData)));
        } catch (IOException exception) {
            emitterClosed.set(true);
        }
    }

    private void sendToolCall(
            SseEmitter emitter,
            AtomicBoolean emitterClosed,
            AgentToolCallEventResponseDTO event
    ) {
        if (emitterClosed.get()) {
            return;
        }
        try {
            boolean isResult = "result".equals(event.phase());
            log.info("[SSE] 推送 {} 事件：toolName={} callId={}",
                    isResult ? "tool_result" : "step_break", event.toolName(), event.callId());
            Map<String, Object> toolCallData = new LinkedHashMap<>();
            toolCallData.put("toolName", event.toolName());
            toolCallData.put("callId", event.callId());
            toolCallData.put("args", event.arguments());
            toolCallData.put("status", event.status());
            if (event.result() != null) toolCallData.put("result", event.result());
            if (event.durationMs() != null) toolCallData.put("durationMs", event.durationMs());
            emitter.send(SseEmitter.event()
                    .name(isResult ? "tool_result" : "step_break")
                    .data(objectMapper.writeValueAsString(toolCallData)));
        } catch (IOException exception) {
            emitterClosed.set(true);
            log.warn("[SSE] step_break 推送失败，emitter 已关闭：{}", exception.getMessage());
        }
    }

    private void sendFinish(
            SseEmitter emitter,
            AtomicBoolean emitterClosed
    ) {
        if (emitterClosed.get()) return;
        try {
            emitter.send(SseEmitter.event().name("finish").data("{}"));
        } catch (IOException ignored) {
            emitterClosed.set(true);
        }
    }

    private void sendDone(
            SseEmitter emitter,
            AtomicBoolean emitterClosed,
            AgentMessageResponseDTO response,
            int totalChunks
    ) throws IOException {
        if (emitterClosed.get()) {
            return;
        }
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("agentId", response.agentId());
        done.put("sessionId", response.sessionId());
        done.put("status", response.status());
        List<Map<String, Object>> allMessages = response.messages();
        done.put("messages", DonePayloads.currentTurnMessages(allMessages));
        done.put("messageCount", DonePayloads.messageCount(allMessages));
        if (response.artifacts() != null && !response.artifacts().isEmpty()) {
            done.put("artifacts", response.artifacts());
        }
        if (response.error() != null && !response.error().isBlank()) {
            done.put("error", response.error());
            log.warn("[SSE] 回合异常收口，done 携带 error：agentId={} sessionId={} error={}",
                    response.agentId(), response.sessionId(), response.error());
        }
        done.put("totalChunks", totalChunks);
        log.info("[SSE] 流式完成，推送 done 事件：agentId={} sessionId={} status={} chunks={}",
                response.agentId(), response.sessionId(), response.status(), totalChunks);
        emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
        emitter.complete();
    }

    private void sendError(
            SseEmitter emitter,
            AtomicBoolean emitterClosed,
            Exception exception
    ) {
        if (emitterClosed.get()) {
            emitter.complete();
            return;
        }
        log.error("[SSE] 流式处理异常：{}", exception.getMessage(), exception);
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", exception.getClass().getSimpleName());
            error.put("message", exception.getMessage());
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(error)));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(exception);
    }
}
