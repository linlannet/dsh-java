package net.linlan.dsh.domain.agent.model.entity;

import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;

/**
 * Agent 运行时的模型渠道配置。
 *
 * @param channelCode     渠道编码
 * @param provider        模型提供方
 * @param model           模型名称
 * @param maxTokens       最大令牌数
 * @param reasoningEffort 推理强度（可为空）
 * @param baseUrl         接口地址
 * @param apiKey          API Key
 * @param protocol        协议
 * @param approvalMode    审批模式
 */
public record AgentOptions(
        String channelCode,
        String provider,
        String model,
        Integer maxTokens,
        String reasoningEffort,
        String baseUrl,
        String apiKey,
        String protocol,
        ApprovalModeVO approvalMode
) {
    public AgentOptions(String channelCode, String provider, String model, Integer maxTokens,
                        String baseUrl, String apiKey, String protocol) {
        this(channelCode, provider, model, maxTokens, null, baseUrl, apiKey, protocol,
                ApprovalModeVO.REQUEST_APPROVAL);
    }

    public AgentOptions(String channelCode, String provider, String model, Integer maxTokens,
                        String reasoningEffort, String baseUrl, String apiKey, String protocol) {
        this(channelCode, provider, model, maxTokens, reasoningEffort, baseUrl, apiKey, protocol,
                ApprovalModeVO.REQUEST_APPROVAL);
    }

    public AgentOptions {
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be a positive integer");
        }
    }

    /**
     * 返回一个新的、更新了推理强度的配置副本。
     * @param value 推理强度。
     * @return 新配置。
     */
    public AgentOptions withReasoningEffort(String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        return new AgentOptions(channelCode, provider, model, maxTokens,
                value.trim().toLowerCase(), baseUrl, apiKey, protocol, approvalMode);
    }

    /**
     * 返回一个新的、更新了审批模式的配置副本。
     * @param mode 审批模式。
     * @return 新配置。
     */
    public AgentOptions withApprovalMode(ApprovalModeVO mode) {
        if (mode == null) {
            return this;
        }
        return new AgentOptions(channelCode, provider, model, maxTokens,
                reasoningEffort, baseUrl, apiKey, protocol, mode);
    }
}