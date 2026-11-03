package net.linlan.dsh.api.dto;

/**
 * 飞书 Bot 保存入参。
 *
 * @param botId              Bot ID（新建可为空，由服务端生成）
 * @param botName            Bot 展示名
 * @param appId              飞书应用 app_id
 * @param appSecret          飞书应用 app_secret（仅入参，不落库）
 * @param agentBaseId        Agent 基 ID
 * @param channelCode        模型渠道编码
 * @param cwd                Agent 工作目录
 * @param groupRequireMention 群聊是否需要 @（可空，缺省不覆盖）
 */
public record FeishuBotSaveDTO(String botId, String botName, String appId, String appSecret, String agentBaseId,
                               String channelCode, String cwd, Boolean groupRequireMention) {
}
