package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.channel.service.OpenAiCompatibleUriResolver;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import net.linlan.dsh.domain.llm.model.valobj.*;
import net.linlan.dsh.domain.model.valobj.*;
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
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek LLM 适配器，兼容 OpenAI 协议。
 * <p>
 * 通过 HTTP POST 调用 {@code /chat/completions}，接收 SSE 流式分片，
 * 并转换为 Harness 的 {@link StreamChunk}。对应 TS 侧 {@code DeepSeekAdapter}。
 *
 * <p>调用流程：模型请求 → HTTP 客户端 → 解析 JSON/SSE → 归一化为领域事件。</p>
 * <p>示例：上游返回 token delta 时，适配器转换为 LLM 增量事件。</p>
 *
 */
public class DeepSeekAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAdapter.class);

    public static final int DEFAULT_STREAM_IDLE_TIMEOUT_MS = 300_000;
    public static final int DEFAULT_CONTEXT_WINDOW = 1_000_000;
    public static final int DEFAULT_MAX_TOKENS = 8_192;

    /**
     * 单次 LLM HTTP 请求总超时（秒）。多步工具调用后的汇总生成、长上下文推理
     * 很容易超过 120s，因此做成可配置并默认放宽到 300s。
     * 可用系统属性 harness.llm.request-timeout-seconds 或环境变量覆盖。
     */
    private static final long REQUEST_TIMEOUT_SECONDS = Long.parseLong(
            System.getProperty("harness.llm.request-timeout-seconds",
                    System.getenv().getOrDefault("HARNESS_LLM_REQUEST_TIMEOUT_SECONDS", "300")));

    private final DeepSeekAdapterOptions options;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 处理Deep Seek Adapter。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param options Agent 配置。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public DeepSeekAdapter(DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        this.options = options;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String providerId() {
        return "deepseek";
    }
    /**
     * 返回 provider Info。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param provider 模型提供方。
     * @return 方法执行结果。
     */


    @Override
    public LlmProviderInfo providerInfo(String provider) {
        return new LlmProviderInfo(provider, "DeepSeek");
    }
    /**
     * retry Policy。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param provider 模型提供方。
     * @return 方法执行结果。
     */


    @Override
    public ResolvedRetryPolicy retryPolicy(String provider) {
        return options.retryPolicy();
    }
    /**
     * 列出Models。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param provider 模型提供方。
     * @return 方法执行结果。
     */


    @Override
    public List<LlmModelInfo> listModels(String provider) {
        return listAvailableCatalogModels().stream()
                .filter(m -> !"codex".equals(m.id()))
                .map(m -> new LlmModelInfo(
                        provider,
                        m.id(),
                        m.name() != null ? m.name() : m.id(),
                        m.description(),
                        List.of("text")
                ))
                .toList();
    }
    /**
     * 解析Model。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param provider 模型提供方。
     * @param model 模型设置。
     * @return 方法执行结果。
     */


    @Override
    public LlmResolvedModelInfo resolveModel(String provider, String model) {
        var configured = listAvailableCatalogModels().stream()
                .filter(m -> m.id().equals(model))
                .findFirst();
        int contextWindow = configured.flatMap(DirectSeekCatalogModel::contextWindowOpt)
                .orElse(options.defaultContextWindow());
        Integer maxTokens = configured.flatMap(DirectSeekCatalogModel::maxTokensOpt)
                .orElse(options.maxTokens());
        return new LlmResolvedModelInfo(
                provider,
                model,
                configured.map(DirectSeekCatalogModel::name).orElse(model),
                configured.flatMap(DirectSeekCatalogModel::descriptionOpt).orElse(null),
                List.of("text"),
                new LlmModelContext(contextWindow),
                maxTokens,
                null // reasoning info not supported in Java adapter yet
        );
    }
    /**
     * stream。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param genOptions gen Options参数。
     * @return 方法执行结果。
     */


    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions genOptions) {
        SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>();
        new Thread(() -> {
            try {
                doStream(genOptions, publisher);
            } catch (Exception e) {
                publishError(publisher, e);
            } finally {
                publisher.close();
            }
        }).start();
        return publisher;
    }

    /**
     * 处理do 流。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param genOptions gen 配置参数。
     * @param publisher publisher参数。
     */
    private void doStream(GenerateOptions genOptions, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        String effectiveModel = resolveEffectiveModel(genOptions.model());
        if (!Objects.equals(effectiveModel, genOptions.model())) {
            log.warn("Requested model '{}' is unavailable on upstream '{}'; falling back to '{}'",
                    genOptions.model(), baseUrl, effectiveModel);
        }
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        String payload = objectMapper.writeValueAsString(body);
        if (log.isDebugEnabled()) {
            JsonNode tools = body.path("tools");
            log.debug("LLM request model={} toolsCount={} temperature={} maxTokens={} payloadLen={}",
                    effectiveModel, tools.isArray() ? tools.size() : 0,
                    body.path("temperature").isMissingNode() ? "<omitted>" : body.path("temperature").asText(),
                    body.path("max_tokens").asInt(-1), payload.length());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        if (genOptions.sessionId() != null) {
            builder.header("x-dsh-session-id", genOptions.sessionId());
        }
        if ("compaction".equals(genOptions.purpose())) {
            builder.header("x-dsh-compact", "1");
        }

        HttpResponse<java.io.InputStream> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new LlmAdapterException(
                    extractErrorMessage(errorBody),
                    httpErrorCode(response.statusCode(), errorBody),
                    response.statusCode()
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            boolean streamed = translateSse(reader, publisher);
            if (!streamed) {
                log.warn("Upstream returned empty stream for model={}, falling back to non-stream request", effectiveModel);
                doNonStreamFallback(genOptions, effectiveModel, publisher);
            }
        }
    }

    /**
     * 处理do Non 流 Fallback。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param genOptions gen 配置参数。
     * @param effectiveModel effective 模型参数。
     * @param publisher publisher参数。
     */
    private void doNonStreamFallback(GenerateOptions genOptions, String effectiveModel, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        body.put("stream", false);

        String payload = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmAdapterException(
                    extractErrorMessage(response.body()),
                    httpErrorCode(response.statusCode(), response.body()),
                    response.statusCode()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmAdapterException("non-stream response has no choices", LlmErrorCodes.EMPTY_RESPONSE);
        }

        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        String reasoning = message.path("reasoning_content").asText(null);

        int nextIdx = 0;
        Integer textIdx = null;
        Integer reasoningIdx = null;
        if (content != null && !content.isEmpty()) {
            textIdx = nextIdx++;
            publisher.submit(new StreamChunk.BlockStart(textIdx, "text"));
            publisher.submit(new StreamChunk.TextDelta(textIdx, content));
        }
        if (reasoning != null && !reasoning.isEmpty()) {
            reasoningIdx = nextIdx++;
            publisher.submit(new StreamChunk.BlockStart(reasoningIdx, "reasoning"));
            publisher.submit(new StreamChunk.ReasoningDelta(reasoningIdx, reasoning));
            if (textIdx == null) {
                int tIdx = nextIdx++;
                publisher.submit(new StreamChunk.BlockStart(tIdx, "text"));
                publisher.submit(new StreamChunk.TextDelta(tIdx, reasoning));
            }
        }

        if (textIdx == null && reasoningIdx == null) {
            throw new LlmAdapterException("non-stream response has no content", LlmErrorCodes.EMPTY_RESPONSE);
        }

        java.util.List<OpenBlock> blocks = new java.util.ArrayList<>();
        if (textIdx != null) {
            OpenBlock tb = new OpenBlock(textIdx, "text");
            tb.text.append(content);
            blocks.add(tb);
        }
        if (reasoningIdx != null) {
            OpenBlock rb = new OpenBlock(reasoningIdx, "reasoning");
            rb.text.append(reasoning);
            blocks.add(rb);
            if (textIdx == null) {
                // synthesized text block already emitted
                OpenBlock synth = new OpenBlock(textIdx != null ? textIdx : reasoningIdx + 1, "text");
                synth.text.append(reasoning);
                blocks.add(synth);
            }
        }
        for (OpenBlock block : blocks) {
            publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
        }

        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode()) {
            publisher.submit(new StreamChunk.Usage(mapUsage(usageNode)));
        }

        String finishReason = choices.get(0).path("finish_reason").asText("stop");
        publisher.submit(new StreamChunk.Finish(mapFinishReason(finishReason)));
    }

    /**
     * 处理serialize 请求。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param options Agent 配置。
     * @return 转换后的目标对象。
     */
    private ObjectNode serializeRequest(GenerateOptions options) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", options.model());
        root.put("stream", true);

        // Messages
        ArrayNode messages = objectMapper.createArrayNode();
        if (options.system() != null && !options.system().isBlank()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", options.system());
            messages.add(sysMsg);
        }
        for (var msg : options.messages()) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());

            // Collect text content
            StringBuilder textContent = new StringBuilder();
            List<ToolCallBlock> msgToolCalls = new ArrayList<>();
            List<ToolResultBlock> msgToolResults = new ArrayList<>();
            List<ImageBlock> msgImages = new ArrayList<>();

            for (var block : msg.content()) {
                if (block instanceof TextBlock tb) {
                    textContent.append(tb.text());
                } else if (block instanceof ToolCallBlock tcb) {
                    msgToolCalls.add(tcb);
                } else if (block instanceof ToolResultBlock trb) {
                    msgToolResults.add(trb);
                } else if (block instanceof ImageBlock ib) {
                    msgImages.add(ib);
                }
            }

            // Tool result messages: role=tool, tool_call_id, content
            if (!msgToolResults.isEmpty() && "tool".equals(msg.role())) {
                for (ToolResultBlock trb : msgToolResults) {
                    ObjectNode trNode = objectMapper.createObjectNode();
                    trNode.put("role", "tool");
                    trNode.put("tool_call_id", trb.toolCallId());
                    StringBuilder trContent = new StringBuilder();
                    for (var inner : trb.content()) {
                        if (inner instanceof TextBlock tb) trContent.append(tb.text());
                    }
                    trNode.put("content", trContent.toString());
                    messages.add(trNode);
                }
                continue;
            }

            // Assistant messages with tool calls
            if (!msgToolCalls.isEmpty()) {
                if (textContent.length() > 0) {
                    msgNode.put("content", textContent.toString());
                } else {
                    msgNode.putNull("content");
                }
                ArrayNode callsNode = objectMapper.createArrayNode();
                for (ToolCallBlock tcb : msgToolCalls) {
                    ObjectNode callNode = objectMapper.createObjectNode();
                    callNode.put("id", tcb.id());
                    callNode.put("type", "function");
                    ObjectNode fnNode = objectMapper.createObjectNode();
                    fnNode.put("name", tcb.name());
                    fnNode.put("arguments", tcb.arguments() != null ? tcb.arguments() : "{}");
                    callNode.set("function", fnNode);
                    callsNode.add(callNode);
                }
                msgNode.set("tool_calls", callsNode);
            } else if (!msgImages.isEmpty()) {
                // Multimodal user message: content = [{type:text},{type:image_url}...]
                ArrayNode contentArr = objectMapper.createArrayNode();
                if (textContent.length() > 0) {
                    ObjectNode textPart = objectMapper.createObjectNode();
                    textPart.put("type", "text");
                    textPart.put("text", textContent.toString());
                    contentArr.add(textPart);
                }
                for (ImageBlock ib : msgImages) {
                    ObjectNode imgPart = objectMapper.createObjectNode();
                    imgPart.put("type", "image_url");
                    ObjectNode urlNode = imgPart.putObject("image_url");
                    String mime = ib.attachment() != null && ib.attachment().mimeType() != null
                            ? ib.attachment().mimeType() : "image/png";
                    byte[] data = ib.attachment() != null ? ib.attachment().data() : new byte[0];
                    urlNode.put("url", "data:" + mime + ";base64,"
                            + Base64.getEncoder().encodeToString(data));
                    contentArr.add(imgPart);
                }
                msgNode.set("content", contentArr);
            } else {
                // Plain text message
                msgNode.put("content", textContent.toString());
            }

            messages.add(msgNode);
        }
        root.set("messages", messages);

        // Tools
        if (options.tools() != null && !options.tools().isEmpty()) {
            ArrayNode tools = objectMapper.createArrayNode();
            for (var tool : options.tools()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.set("parameters", objectMapper.valueToTree(tool.parameters()));
                toolNode.set("function", fn);
                tools.add(toolNode);
            }
            root.set("tools", tools);
        }

        if (options.temperature() != null) root.put("temperature", options.temperature());
        if (options.reasoningEffort() != null && !options.reasoningEffort().isBlank()) {
            root.put("reasoning_effort", options.reasoningEffort());
        }
        if (options.maxTokens() != null) root.put("max_tokens", options.maxTokens());
        if (options.stop() != null && !options.stop().isEmpty()) {
            root.set("stop", objectMapper.valueToTree(options.stop()));
        }

        return root;
    }

    /**
     * 处理with 模型。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param options Agent 配置。
     * @param model 模型设置。
     * @return 方法执行结果。
     */
    private GenerateOptions withModel(GenerateOptions options, String model) {
        return new GenerateOptions(
                options.provider(),
                model,
                options.reasoningEffort(),
                options.messages(),
                options.system(),
                options.tools(),
                options.temperature(),
                options.maxTokens(),
                options.stop(),
                options.sessionId(),
                options.purpose(),
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );
    }

    /**
     * 解析e Effective Model。
     */
    private String resolveEffectiveModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel;
        }
        List<DirectSeekCatalogModel> discovered = discoverUpstreamModels();
        if (discovered.isEmpty()) {
            return requestedModel;
        }
        Set<String> modelIds = new LinkedHashSet<>();
        for (DirectSeekCatalogModel model : discovered) {
            modelIds.add(model.id());
        }
        if (modelIds.contains(requestedModel)) {
            return requestedModel;
        }
        for (DirectSeekCatalogModel configured : options.models()) {
            if (modelIds.contains(configured.id())) {
                return configured.id();
            }
        }
        return preferredDiscoveredModel(discovered);
    }

    /**
     * 列出Available Catalog Models。
     */
    private List<DirectSeekCatalogModel> listAvailableCatalogModels() {
        List<DirectSeekCatalogModel> discovered = discoverUpstreamModels();
        if (discovered.isEmpty()) {
            return options.models();
        }
        Map<String, DirectSeekCatalogModel> configuredById = new LinkedHashMap<>();
        for (DirectSeekCatalogModel configured : options.models()) {
            configuredById.put(configured.id(), configured);
        }
        List<DirectSeekCatalogModel> merged = new ArrayList<>();
        for (DirectSeekCatalogModel discoveredModel : discovered) {
            merged.add(configuredById.getOrDefault(discoveredModel.id(), discoveredModel));
        }
        return merged;
    }

    /**
     * 发现 Upstream Models。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    private List<DirectSeekCatalogModel> discoverUpstreamModels() {
        String apiKey = options.resolveApiKey();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveModelsUri(options.baseUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Failed to discover upstream models from {}: HTTP {} {}",
                        options.baseUrl(), response.statusCode(), extractErrorMessage(response.body()));
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<DirectSeekCatalogModel> models = new ArrayList<>();
            for (JsonNode modelNode : data) {
                String id = modelNode.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                models.add(new DirectSeekCatalogModel(id, id, null, options.defaultContextWindow(), options.maxTokens()));
            }
            return models;
        } catch (Exception exception) {
            log.warn("Failed to discover upstream models from {}: {}", options.baseUrl(), exception.getMessage());
            return List.of();
        }
    }

    /**
     * 处理preferred Discovered 模型。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param discovered discovered参数。
     * @return 方法执行结果。
     */
    private String preferredDiscoveredModel(List<DirectSeekCatalogModel> discovered) {
        return discovered.get(0).id();
    }

    /**
     * 转换 SSE。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param reader reader参数。
     * @param publisher publisher参数。
     * @return 方法执行结果。
     */
    private boolean translateSse(BufferedReader reader, SubmissionPublisher<StreamChunk> publisher) throws IOException {
        int nextIndex = 0;
        Integer textBlockIndex = null;
        Integer reasoningBlockIndex = null;
        Map<Integer, Integer> toolWireIndexToBlockIndex = new LinkedHashMap<>();
        List<OpenBlock> openBlocks = new ArrayList<>();
        FinishReason pendingFinish = null;
        TokenUsage pendingUsage = null;    boolean receivedData = false;


        long firstByteDeadline = System.currentTimeMillis() + 10_000L; // 10s first-byte timeout
        String line;
        // Wait for first byte with timeout: upstream may accept stream=true but never emit data
        while (!reader.ready()) {
            if (System.currentTimeMillis() > firstByteDeadline) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                // Reasoning-only fallback: if the model returned reasoning_content
                // but no text content, synthesize a text block from the reasoning
                // so downstream consumers (extractText) can see it.
                if (textBlockIndex == null && reasoningBlockIndex != null) {
                    OpenBlock reasoningBlock = openBlocks.get(reasoningBlockIndex);
                    int newIdx = nextIndex++;
                    OpenBlock textBlock = new OpenBlock(newIdx, "text");
                    textBlock.text.append(reasoningBlock.text.toString());
                    openBlocks.add(textBlock);
                    publisher.submit(new StreamChunk.BlockStart(newIdx, "text"));
                    publisher.submit(new StreamChunk.TextDelta(newIdx, reasoningBlock.text.toString()));
                }

                // Close all blocks
                for (OpenBlock block : openBlocks) {
                    publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
                }
                if (pendingUsage != null) {
                    publisher.submit(new StreamChunk.Usage(pendingUsage));
                }
                FinishReason finish = pendingFinish != null ? pendingFinish : new FinishReason.Stop();
                if (finish instanceof FinishReason.Stop && openBlocks.isEmpty()) {
                    finish = new FinishReason.Error(new LlmFailure(
                            "model returned a completed response with no content",
                            LlmErrorCodes.EMPTY_RESPONSE
                    ));
                }
                publisher.submit(new StreamChunk.Finish(finish));
                return true;
            }

            receivedData = true;


            JsonNode chunk;
            try {
                chunk = objectMapper.readTree(data);
            } catch (Exception e) {
                throw new LlmAdapterException(
                        "malformed SSE payload: " + data.substring(0, Math.min(data.length(), 120)),
                        LlmErrorCodes.MALFORMED_RESPONSE
                );
            }

            JsonNode choices = chunk.path("choices");
            for (JsonNode choiceNode : choices) {
                JsonNode delta = choiceNode.path("delta");

                // Reasoning content
                String reasoning = delta.path("reasoning_content").asText(null);
                if (reasoning != null && !reasoning.isEmpty()) {
                    if (reasoningBlockIndex == null) {
                        reasoningBlockIndex = nextIndex++;
                        openBlocks.add(new OpenBlock(reasoningBlockIndex, "reasoning"));
                        publisher.submit(new StreamChunk.BlockStart(reasoningBlockIndex, "reasoning"));
                    }
                    OpenBlock block = openBlocks.get(reasoningBlockIndex);
                    block.text.append(reasoning);
                    publisher.submit(new StreamChunk.ReasoningDelta(reasoningBlockIndex, reasoning));
                }

                // Text content
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    if (textBlockIndex == null) {
                        textBlockIndex = nextIndex++;
                        openBlocks.add(new OpenBlock(textBlockIndex, "text"));
                        publisher.submit(new StreamChunk.BlockStart(textBlockIndex, "text"));
                    }
                    OpenBlock block = openBlocks.get(textBlockIndex);
                    block.text.append(content);
                    publisher.submit(new StreamChunk.TextDelta(textBlockIndex, content));
                }

                // Tool calls
                JsonNode toolCalls = delta.path("tool_calls");
                for (JsonNode call : toolCalls) {
                    int wireIndex = call.path("index").asInt(0);
                    Integer blockIndex = toolWireIndexToBlockIndex.get(wireIndex);
                    if (blockIndex == null) {
                        blockIndex = nextIndex++;
                        toolWireIndexToBlockIndex.put(wireIndex, blockIndex);
                        openBlocks.add(new OpenBlock(blockIndex, "tool-call"));
                        publisher.submit(new StreamChunk.BlockStart(blockIndex, "tool-call"));
                    }
                    OpenBlock block = openBlocks.get(blockIndex);
                    String callId = call.path("id").asText(null);
                    if (callId != null) block.callId = callId;
                    String fnName = call.path("function").path("name").asText(null);
                    if (fnName != null) block.callName = fnName;
                    String argsDelta = call.path("function").path("arguments").asText("");
                    block.text.append(argsDelta);
                    publisher.submit(new StreamChunk.ToolCallDelta(
                            blockIndex,
                            block.callId != null ? block.callId : "",
                            block.callName,
                            argsDelta
                    ));
                }

                String finishReason = choiceNode.path("finish_reason").asText(null);
                if (finishReason != null) {
                    pendingFinish = mapFinishReason(finishReason);
                }
            }

            // Usage
            JsonNode usageNode = chunk.path("usage");
            if (!usageNode.isMissingNode()) {
                pendingUsage = mapUsage(usageNode);
            }
        }

        // Stream ended without [DONE]
        if (!receivedData) {
            return false;
        }
        throw new LlmAdapterException("SSE payload stream ended without [DONE]", LlmErrorCodes.STREAM_CLOSED);
    }

    /**
     * 映射 Finish Reason。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param reason reason参数。
     * @return 方法执行结果。
     */
    private static FinishReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> new FinishReason.Stop();
            case "tool_calls" -> new FinishReason.ToolCalls();
            case "length" -> new FinishReason.MaxTokens();
            default -> new FinishReason.Error(new LlmFailure(
                    "model stopped: " + reason, reason.toUpperCase()
            ));
        };
    }

    /**
     * 映射 用量。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param usage 用量参数。
     * @return 方法执行结果。
     */
    private static TokenUsage mapUsage(JsonNode usage) {
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        Integer cacheRead = null;
        JsonNode details = usage.path("prompt_tokens_details");
        if (!details.isMissingNode()) {
            cacheRead = details.path("cached_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        if (cacheRead == null) {
            cacheRead = usage.path("prompt_cache_hit_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        Integer reasoning = null;
        JsonNode compDetails = usage.path("completion_tokens_details");
        if (!compDetails.isMissingNode()) {
            reasoning = compDetails.path("reasoning_tokens").asInt(0);
            if (reasoning == 0) reasoning = null;
        }
        int inputTokens = cacheRead != null ? promptTokens - cacheRead : promptTokens;
        return new TokenUsage(inputTokens, completionTokens, cacheRead, null, reasoning);
    }

    /**
     * 处理HTTP Error 编码。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param status 状态对象。
     * @param errorBody error Body参数。
     * @return 方法执行结果。
     */
    private String httpErrorCode(int status, String errorBody) {
        if (status == 401 || status == 403) return LlmErrorCodes.AUTH;
        if (status == 429) return LlmErrorCodes.RATE_LIMIT;
        if (status == 400) return LlmErrorCodes.INVALID_REQUEST;
        if (status >= 500) return LlmErrorCodes.SERVER;
        return "HTTP_" + status;
    }

    /**
     * 提取 Error 消息。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param body body参数。
     * @return 方法执行结果。
     */
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

    /**
     * 发布 Error。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param publisher publisher参数。
     * @param e 异常对象。
     */
    private void publishError(SubmissionPublisher<StreamChunk> publisher, Exception e) {
        LlmFailure failure;
        if (e instanceof LlmAdapterException lae) {
            failure = new LlmFailure(lae.getMessage(), lae.code(), lae.status(), null, null);
        } else {
            failure = new LlmFailure(e.getMessage() != null ? e.getMessage() : "LLM adapter failed", LlmErrorCodes.UNKNOWN);
        }
        log.error("DeepSeek adapter stream error: {}", failure.message(), e);
        publisher.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
    }

    // ── Internal types ────────────────────────────────────────

    /**
     * OpenBlock 是基础设施适配器，负责外部资源与领域端口对接。
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
                case "reasoning" -> new ReasoningBlock(text.toString());
                case "tool-call" -> new ToolCallBlock(
                        callId != null ? callId : "call-" + index,
                        callName != null ? callName : "",
                        text.toString()
                );
                default -> throw new IllegalStateException("cannot close block of type: " + kind);
            };
        }
    }

/** 适配器内部异常。 */
    public static final class LlmAdapterException extends RuntimeException {
        private final String code;
        private final Integer status;

        /**
         * 处理LLM Adapter Exception。
         * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
         * @param message 消息内容或对象。
         * @param code 编码参数。
         * @return 方法执行结果。
         */
        public LlmAdapterException(String message, String code) {
            this(message, code, null);
        }

        /**
         * 处理LLM Adapter Exception。
         * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
         * @param message 消息内容或对象。
         * @param code 编码参数。
         * @param status 状态对象。
         * @return 方法执行结果。
         */
        public LlmAdapterException(String message, String code, Integer status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        /**
         * 处理编码。
         * @return 方法执行结果。
         */
        public String code() { return code; }
        /**
         * 处理status。
         * @return 方法执行结果。
         */
        public Integer status() { return status; }
    }
}
