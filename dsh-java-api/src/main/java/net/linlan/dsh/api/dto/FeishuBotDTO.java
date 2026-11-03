package net.linlan.dsh.api.dto;

/**
 * 飞书 Bot 视图对象（不含 appSecret 明文）。
 *
 * @param botId              Bot ID
 * @param botName            Bot 展示名
 * @param appId              飞书应用 app_id
 * @param secretRef          凭据引用键
 * @param secretConfigured   app_secret 是否已配置
 * @param enabled            是否启用
 * @param agentBaseId        Agent 基 ID
 * @param channelCode        模型渠道编码
 * @param cwd                Agent 工作目录
 * @param groupRequireMention 群聊是否需要 @
 * @param connectionState    连接状态
 * @param lastError          最近错误
 * @param updatedAt          更新时间（ISO-8601）
 */
public record FeishuBotDTO(String botId, String botName, String appId, String secretRef, boolean secretConfigured,
                           boolean enabled, String agentBaseId, String channelCode, String cwd,
                           boolean groupRequireMention, String connectionState, String lastError, String updatedAt) {
}
