package net.linlan.dsh.api.dto;

/**
 * 向 Agent 发送消息的请求。
 *
 * @param agentId Agent ID
 * @param channelCode 模型渠道；为空时使用当前激活渠道
 * @param maxTokens 最大输出 Token 数；为空时使用默认值
 * @param cwd 工作目录；为空时使用进程工作目录
 * @param message 用户消息文本
 * @param images 图片附件列表；每项为 data URL（data:image/png;base64,...）或裸 base64，
 *               可为 null/空。仅多模态模型支持，文本-only 模型会忽略或报错。
 * @param approvalMode 运行审批模式：REQUEST_APPROVAL / AUTO_APPROVE / FULL_OPEN
 * @param reasoningEffort 推理力度：low / medium / high；为空时使用模型默认
 * @param sandboxRoots 沙箱额外根目录列表；为空时不扩展沙箱
 *
 *
 */
public record AgentMessageRequestDTO(
        String agentId,
        String channelCode,
        Integer maxTokens,
        String cwd,
        String message,
        java.util.List<String> images,
        String approvalMode,
        String reasoningEffort,
        java.util.List<String> sandboxRoots
) {
    public AgentMessageRequestDTO {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must be non-blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must be non-blank");
        }
    }
}
