package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * feishu_bot 表持久化对象。
 *
 * @param botId               Bot ID（主键）
 * @param botName             Bot 展示名
 * @param appId               飞书应用 app_id
 * @param secretRef           凭据引用键（app_secret 不落库）
 * @param enabled             是否启用（0/1）
 * @param agentBaseId         Agent 基 ID
 * @param channelCode         模型渠道编码
 * @param cwd                 Agent 工作目录
 * @param groupRequireMention 群聊是否需要 @（0/1）
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record FeishuBotPO(String botId, String botName, String appId, String secretRef, Integer enabled,
                          String agentBaseId, String channelCode, String cwd, Integer groupRequireMention,
                          Instant createdAt, Instant updatedAt) {
}
