package net.linlan.dsh.api.dto;

/**
 * 上游模型同步请求。
 *
 * @param baseUrl   渠道基础地址（必填）
 * @param apiKeyRef API Key（可选，本地渠道可为空）
 * @param protocol  渠道协议编码（openai / anthropic / ollama），空值按 openai 兼容处理
 *
 */
public record DiscoverModelsRequestDTO(String baseUrl, String apiKeyRef, String protocol) {
}
