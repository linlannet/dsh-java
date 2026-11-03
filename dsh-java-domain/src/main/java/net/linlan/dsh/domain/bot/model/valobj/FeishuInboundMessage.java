package net.linlan.dsh.domain.bot.model.valobj;

/**
 * 飞书入站消息值对象。
 *
 * @param botId        关联的 Bot ID
 * @param messageId    飞书消息 ID（用于去重）
 * @param chatId       会话 ID（单聊/群聊）
 * @param chatType     会话类型（p2p 单聊 / group 群聊）
 * @param messageType  消息类型（text / post 等）
 * @param text         抽取后的文本内容
 * @param senderOpenId 发送者 open_id
 * @param senderName   发送者展示名
 */
public record FeishuInboundMessage(String botId, String messageId, String chatId, String chatType,
                                   String messageType, String text, String senderOpenId, String senderName) {

    /**
     * 是否为单聊（p2p）。
     *
     * @return 单聊返回 true
     */
    public boolean isP2P() {
        return "p2p".equals(this.chatType);
    }

    /**
     * 是否包含可用文本。
     *
     * @return 文本非空白返回 true
     */
    public boolean hasText() {
        return this.text != null && !this.text.isBlank();
    }
}
