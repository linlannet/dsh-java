package net.linlan.dsh.infrastructure.adapter.llm.anthropic;

import net.linlan.dsh.domain.channel.model.valobj.ChannelProtocol;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.LlmFailure;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.ReasoningBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anthropic Messages 协议适配器。
 * <p>
 * 将 Harness 的统一 {@link GenerateOptions} 翻译为 Anthropic {@code /v1/messages} 请求，
 * 并把 Anthropic SSE 事件流转回 {@link StreamChunk}。鉴权使用 {@code x-api-key} 头
 * 并携带 {@code anthropic-version}。参考 WaLiAPI 的 claude adaptor 设计：
 * 协议差异（鉴权头、请求/响应 JSON 转换）封装在适配器内部，对外只暴露统一契约。
 *
 *
 */
public class AnthropicAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final long REQUEST_TIMEOUT_SECONDS = Long.parseLong(
            System.getProperty("harness.llm.request-timeout-seconds",
                    System.getenv().getOrDefault("HARNESS_LLM_REQUEST_TIMEOUT_SECONDS", "300")));

    private final String providerId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 处理Anthropic Adapter。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     *
     * @param providerId   该适配器实例服务的 provider 编码（如 anthropic）
     * @param objectMapper JSON 序列化器
     */
    public AnthropicAdapter(String providerId, ObjectMapper objectMapper) {
        this.providerId = providerId;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public LlmProviderInfo providerInfo(String provider) {
        return new LlmProviderInfo(provider, "Anthropic");
    }

    @Override
    public List<LlmModelInfo> listModels(String provider) {
        // 模型列表由模型发现服务按协议分派（GET /v1/models），这里不做硬编码目录
        return List.of();
    }

    @Override
    public LlmResolvedModelInfo resolveModel(String provider, String model) {
        return new LlmResolvedModelInfo(
                provider, model, model, null, List.of("text"),
                new LlmModelContext(200_000), 8_192, null
        );
    }

    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions genOptions) {
        SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>();
        Thread worker = new Thread(() -> {
            try {
                doStream(genOptions, publisher);
            } catch (Exception e) {
                publishError(publisher, e);
            } finally {
                publisher.close();
            }
        });
        worker.setName("llm-anthropic-" + providerId);
        worker.setDaemon(true);
        worker.start();
        return publisher;
    }

    /**
     * 发起一次流式 Messages 调用。
     */
    private void doStream(GenerateOptions genOptions, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey();
        String baseUrl = genOptions.baseUrl();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AnthropicAdapterException(
                    "Anthropic 渠道缺少 API Key，请在模型设置中填写 apiKeyRef", LlmErrorCodes.AUTH);
        }
        ObjectNode body = serializeRequest(genOptions);
        String payload = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveMessagesUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new AnthropicAdapterException(
                    extractErrorMessage(errorBody),
                    httpErrorCode(response.statusCode()),
                    response.statusCode()
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            translateSse(reader, publisher);
        }
    }

    /**
     * 将统一 GenerateOptions 序列化为 Anthropic Messages 请求体。
     * <p>
     * 转换规则（对应 WaLiAPI 的 convert_openai_messages_to_claude）：
     * system 提升为顶层字段；tool 结果并入后续 user 消息的 content 数组；
     * assistant 的 tool_calls 转为 tool_use 块；工具 schema 平铺为 name/description/input_schema。
     */
    private ObjectNode serializeRequest(GenerateOptions options) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", options.model());
        root.put("stream", true);
        root.put("max_tokens", options.maxTokens() != null ? options.maxTokens() : 8_192);

        if (options.system() != null && !options.system().isBlank()) {
            root.put("system", options.system());
        }

        ArrayNode messages = objectMapper.createArrayNode();
        for (var msg : options.messages()) {
            StringBuilder textContent = new StringBuilder();
            List<ToolCallBlock> toolCalls = new ArrayList<>();
            List<ToolResultBlock> toolResults = new ArrayList<>();
            List<ImageBlock> images = new ArrayList<>();
            for (var block : msg.content()) {
                if (block instanceof TextBlock tb) {
                    textContent.append(tb.text());
                } else if (block instanceof ToolCallBlock tcb) {
                    toolCalls.add(tcb);
                } else if (block instanceof ToolResultBlock trb) {
                    toolResults.add(trb);
                } else if (block instanceof ImageBlock ib) {
                    images.add(ib);
                }
            }

            // tool 结果 → user 消息中的 tool_result 块
            if (!toolResults.isEmpty() && "tool".equals(msg.role())) {
                ObjectNode userNode = objectMapper.createObjectNode();
                userNode.put("role", "user");
                ArrayNode content = objectMapper.createArrayNode();
                for (ToolResultBlock trb : toolResults) {
                    ObjectNode trNode = objectMapper.createObjectNode();
                    trNode.put("type", "tool_result");
                    trNode.put("tool_use_id", trb.toolCallId());
                    StringBuilder trText = new StringBuilder();
                    for (var inner : trb.content()) {
                        if (inner instanceof TextBlock tb) trText.append(tb.text());
                    }
                    trNode.put("content", trText.toString());
                    content.add(trNode);
                }
                userNode.set("content", content);
                messages.add(userNode);
                continue;
            }

            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", "assistant".equals(msg.role()) ? "assistant" : "user");

            boolean hasRichContent = !toolCalls.isEmpty() || !images.isEmpty();
            if (!hasRichContent) {
                msgNode.put("content", textContent.toString());
            } else {
                ArrayNode content = objectMapper.createArrayNode();
                if (textContent.length() > 0) {
                    ObjectNode textPart = objectMapper.createObjectNode();
                    textPart.put("type", "text");
                    textPart.put("text", textContent.toString());
                    content.add(textPart);
                }
                for (ImageBlock ib : images) {
                    ObjectNode imgPart = objectMapper.createObjectNode();
                    imgPart.put("type", "image");
                    ObjectNode source = imgPart.putObject("source");
                    source.put("type", "base64");
                    String mime = ib.attachment() != null && ib.attachment().mimeType() != null
                            ? ib.attachment().mimeType() : "image/png";
                    byte[] data = ib.attachment() != null ? ib.attachment().data() : new byte[0];
                    source.put("media_type", mime);
                    source.put("data", Base64.getEncoder().encodeToString(data));
                    content.add(imgPart);
                }
                for (ToolCallBlock tcb : toolCalls) {
                    ObjectNode callPart = objectMapper.createObjectNode();
                    callPart.put("type", "tool_use");
                    callPart.put("id", tcb.id());
                    callPart.put("name", tcb.name());
                    try {
                        callPart.set("input", objectMapper.readTree(
                                tcb.arguments() != null && !tcb.arguments().isBlank() ? tcb.arguments() : "{}"));
                    } catch (Exception e) {
                        callPart.putObject("input");
                    }
                    content.add(callPart);
                }
                msgNode.set("content", content);
            }
            messages.add(msgNode);
        }
        root.set("messages", messages);

        if (options.tools() != null && !options.tools().isEmpty()) {
            ArrayNode tools = objectMapper.createArrayNode();
            for (var tool : options.tools()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", objectMapper.valueToTree(tool.parameters()));
                tools.add(toolNode);
            }
            root.set("tools", tools);
        }

        if (options.temperature() != null) root.put("temperature", options.temperature());
        if (options.stop() != null && !options.stop().isEmpty()) {
            root.set("stop_sequences", objectMapper.valueToTree(options.stop()));
        }
        return root;
    }

    /**
     * 翻译 Anthropic SSE 事件流为统一 StreamChunk。
     * <p>
     * 事件序列：message_start → content_block_start → content_block_delta* →
     * content_block_stop → message_delta（stop_reason + usage）→ message_stop。
     */
    private boolean translateSse(BufferedReader reader, SubmissionPublisher<StreamChunk> publisher) throws IOException {
        List<OpenBlock> openBlocks = new ArrayList<>();
        FinishReason pendingFinish = null;
        TokenUsage pendingUsage = null;
        boolean receivedAny = false;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || !line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            JsonNode event;
            try {
                event = objectMapper.readTree(data);
            } catch (Exception e) {
                throw new AnthropicAdapterException(
                        "malformed SSE payload: " + data.substring(0, Math.min(data.length(), 120)),
                        LlmErrorCodes.MALFORMED_RESPONSE);
            }
            String type = event.path("type").asText("");

            switch (type) {
                case "content_block_start" -> {
                    receivedAny = true;
                    int index = event.path("index").asInt(0);
                    JsonNode block = event.path("content_block");
                    String blockType = block.path("type").asText("text");
                    OpenBlock openBlock = new OpenBlock(index,
                            "tool_use".equals(blockType) ? "tool-call" : blockType);
                    if ("tool_use".equals(blockType)) {
                        openBlock.callId = block.path("id").asText(null);
                        openBlock.callName = block.path("name").asText(null);
                    }
                    openBlocks.add(openBlock);
                    publisher.submit(new StreamChunk.BlockStart(index, openBlock.kind));
                }
                case "content_block_delta" -> {
                    receivedAny = true;
                    int index = event.path("index").asInt(0);
                    OpenBlock block = findBlock(openBlocks, index);
                    JsonNode delta = event.path("delta");
                    String deltaType = delta.path("type").asText("");
                    if ("text_delta".equals(deltaType)) {
                        String text = delta.path("text").asText("");
                        if (!text.isEmpty()) {
                            if (block != null) block.text.append(text);
                            publisher.submit(new StreamChunk.TextDelta(index, text));
                        }
                    } else if ("thinking_delta".equals(deltaType)) {
                        String text = delta.path("thinking").asText("");
                        if (!text.isEmpty()) {
                            if (block != null) block.text.append(text);
                            publisher.submit(new StreamChunk.ReasoningDelta(index, text));
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        String partial = delta.path("partial_json").asText("");
                        if (block != null) block.text.append(partial);
                        publisher.submit(new StreamChunk.ToolCallDelta(
                                index,
                                block != null && block.callId != null ? block.callId : "",
                                block != null ? block.callName : null,
                                partial
                        ));
                    }
                }
                case "content_block_stop" -> {
                    int index = event.path("index").asInt(0);
                    OpenBlock block = removeBlock(openBlocks, index);
                    if (block != null) {
                        publisher.submit(new StreamChunk.BlockEnd(index, block.toContentBlock()));
                    }
                }
                case "message_delta" -> {
                    receivedAny = true;
                    String stopReason = event.path("delta").path("stop_reason").asText(null);
                    if (stopReason != null) {
                        pendingFinish = mapStopReason(stopReason);
                    }
                    JsonNode usage = event.path("usage");
                    if (!usage.isMissingNode()) {
                        pendingUsage = new TokenUsage(
                                usage.path("input_tokens").asInt(0),
                                usage.path("output_tokens").asInt(0),
                                null, null, null);
                    }
                }
                case "message_stop" -> {
                    if (pendingUsage != null) {
                        publisher.submit(new StreamChunk.Usage(pendingUsage));
                    }
                    publisher.submit(new StreamChunk.Finish(
                            pendingFinish != null ? pendingFinish : new FinishReason.Stop()));
                    return true;
                }
                case "error" -> {
                    String message = event.path("error").path("message").asText("anthropic stream error");
                    throw new AnthropicAdapterException(message, LlmErrorCodes.SERVER);
                }
                default -> {
                    // message_start / ping 等事件忽略
                }
            }
        }

        if (!receivedAny) {
            throw new AnthropicAdapterException(
                    "anthropic stream ended without any content", LlmErrorCodes.EMPTY_RESPONSE);
        }
        throw new AnthropicAdapterException(
                "anthropic SSE stream ended without message_stop", LlmErrorCodes.STREAM_CLOSED);
    }

    private static OpenBlock findBlock(List<OpenBlock> blocks, int index) {
        for (OpenBlock block : blocks) {
            if (block.index == index) return block;
        }
        return null;
    }

    private static OpenBlock removeBlock(List<OpenBlock> blocks, int index) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).index == index) {
                return blocks.remove(i);
            }
        }
        return null;
    }

    /**
     * 映射 Anthropic stop_reason 为统一 FinishReason。
     */
    private static FinishReason mapStopReason(String reason) {
        return switch (reason) {
            case "end_turn", "stop_sequence" -> new FinishReason.Stop();
            case "tool_use" -> new FinishReason.ToolCalls();
            case "max_tokens" -> new FinishReason.MaxTokens();
            default -> new FinishReason.Error(new LlmFailure(
                    "model stopped: " + reason, reason.toUpperCase()));
        };
    }

    /**
     * 解析 Messages 端点地址；baseUrl 允许携带或不携带 /v1 后缀。
     */
    private URI resolveMessagesUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return URI.create("https://api.anthropic.com/v1/messages");
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/messages")) return URI.create(normalized);
        if (normalized.endsWith("/v1")) return URI.create(normalized + "/messages");
        if (normalized.endsWith("/")) return URI.create(normalized + "v1/messages");
        return URI.create(normalized + "/v1/messages");
    }

    private String httpErrorCode(int status) {
        if (status == 401 || status == 403) return LlmErrorCodes.AUTH;
        if (status == 429) return LlmErrorCodes.RATE_LIMIT;
        if (status == 400) return LlmErrorCodes.INVALID_REQUEST;
        if (status >= 500) return LlmErrorCodes.SERVER;
        return "HTTP_" + status;
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            if (msg != null && !msg.isBlank()) return msg;
        } catch (Exception ignored) {
            return body;
        }
        return body;
    }

    private void publishError(SubmissionPublisher<StreamChunk> publisher, Exception e) {
        LlmFailure failure;
        if (e instanceof AnthropicAdapterException aae) {
            failure = new LlmFailure(aae.getMessage(), aae.code(), aae.status(), null, null);
        } else {
            failure = new LlmFailure(e.getMessage() != null ? e.getMessage() : "LLM adapter failed",
                    LlmErrorCodes.UNKNOWN);
        }
        log.error("Anthropic adapter stream error: {}", failure.message(), e);
        publisher.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
    }

    /** 当前适配器服务的协议。 */
    public String protocol() {
        return ChannelProtocol.ANTHROPIC.code();
    }

    /**
     * 流式过程中打开的块。
     */
    private static final class OpenBlock {
        final int index;
        final String kind;
        final StringBuilder text = new StringBuilder();
        String callId;
        String callName;

        OpenBlock(int index, String kind) {
            this.index = index;
            this.kind = kind;
        }

        ContentBlock toContentBlock() {
            return switch (kind) {
                case "text" -> new TextBlock(text.toString());
                case "thinking", "reasoning" -> new ReasoningBlock(text.toString());
                case "tool-call" -> new ToolCallBlock(
                        callId != null ? callId : "call-" + index,
                        callName != null ? callName : "",
                        text.toString()
                );
                default -> new TextBlock(text.toString());
            };
        }
    }

    /** Anthropic 适配器内部异常。 */
    public static final class AnthropicAdapterException extends RuntimeException {
        private final String code;
        private final Integer status;

        public AnthropicAdapterException(String message, String code) {
            this(message, code, null);
        }

        public AnthropicAdapterException(String message, String code, Integer status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public Integer status() { return status; }
    }
}
