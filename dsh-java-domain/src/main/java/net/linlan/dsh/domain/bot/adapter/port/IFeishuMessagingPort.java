package net.linlan.dsh.domain.bot.adapter.port;

/**
 * 飞书出站消息端口（由基础设施层适配）。
 */
public interface IFeishuMessagingPort {

    /**
     * 发送文本消息。
     *
     * @param appId  飞书应用 app_id
     * @param chatId 会话 ID
     * @param text   文本内容
     * @return 消息 ID（失败返回空）
     */
    String sendText(String appId, String chatId, String text);

    /**
     * 就地更新文本消息。
     *
     * @param appId     飞书应用 app_id
     * @param messageId 目标消息 ID
     * @param text      文本内容
     * @return 更新成功返回 true
     */
    boolean updateText(String appId, String messageId, String text);

    /**
     * 发送交互式 Markdown 卡片。
     *
     * @param appId    飞书应用 app_id
     * @param chatId   会话 ID
     * @param markdown Markdown 内容
     * @return 消息 ID（失败返回空）
     */
    String sendMarkdownCard(String appId, String chatId, String markdown);
}
