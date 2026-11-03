package net.linlan.dsh.domain.bot.adapter.port;

import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;

/**
 * 飞书入站消息处理端口（由用例层实现）。
 */
public interface IFeishuInboundHandler {

    /**
     * 处理一条入站消息。
     *
     * @param bot     Bot 聚合
     * @param message 入站消息
     */
    void handle(FeishuBotAggregate bot, FeishuInboundMessage message);
}
