package net.linlan.dsh.domain.bot.adapter.port;

import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;

/**
 * 飞书 Bot 长连接端口（由基础设施层适配）。
 */
public interface IFeishuBotConnectionPort {

    /**
     * 建立长连接。
     *
     * @param bot Bot 聚合
     */
    void start(FeishuBotAggregate bot);

    /**
     * 断开长连接。
     *
     * @param botId Bot ID
     */
    void stop(String botId);

    /**
     * 是否已连接。
     *
     * @param botId Bot ID
     * @return 已连接返回 true
     */
    boolean isConnected(String botId);

    /**
     * 连接状态描述。
     *
     * @param botId Bot ID
     * @return 状态名，缺省返回 IDLE
     */
    String stateOf(String botId);

    /**
     * 最近错误描述。
     *
     * @param botId Bot ID
     * @return 最近错误，无则返回 null
     */
    String lastErrorOf(String botId);
}
