package net.linlan.dsh.domain.llm.model.valobj;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.valobj.ToolSchema;

import java.util.List;
import java.util.Objects;

/**
 * GenerateOptions 表示GenerateOptions智能体选项。
 *
 * @param protocol 渠道协议编码（openai / anthropic / ollama）；空值按 openai 兼容协议处理
 */
public record GenerateOptions(
        String provider,
        String model,
        String reasoningEffort,
        List<Message> messages,
        String system,
        List<ToolSchema> tools,
        Double temperature,
        Integer maxTokens,
        List<String> stop,
        String sessionId,
        String purpose,
        String baseUrl,
        String apiKey,
        String protocol
) {
    public GenerateOptions {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        messages = List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
        stop = stop == null ? null : List.copyOf(stop);
    }

    /**
     * 兼容旧调用方的构造器：协议按 openai 兼容处理。
     */
    public GenerateOptions(
            String provider,
            String model,
            String reasoningEffort,
            List<Message> messages,
            String system,
            List<ToolSchema> tools,
            Double temperature,
            Integer maxTokens,
            List<String> stop,
            String sessionId,
            String purpose,
            String baseUrl,
            String apiKey
    ) {
        this(provider, model, reasoningEffort, messages, system, tools, temperature,
                maxTokens, stop, sessionId, purpose, baseUrl, apiKey, null);
    }

    /**
     * 处理toCallConfig。
     * @return 领域处理结果。
     */
    public LlmCallConfig toCallConfig() {
        return new LlmCallConfig(provider, model, reasoningEffort, temperature, maxTokens, stop);
    }
}
