package net.linlan.dsh.api.dto;

/**
 * 飞书一键配对发起入参。
 *
 * @param botName     期望的 Bot 名称（可空，默认 DeepSeek Bot）
 * @param agentBaseId 期望的 Agent 基 ID（可空，默认 feishu-agent）
 */
public record FeishuPairingStartDTO(String botName, String agentBaseId) {
}
