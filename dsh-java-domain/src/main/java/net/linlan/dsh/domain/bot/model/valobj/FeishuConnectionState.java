package net.linlan.dsh.domain.bot.model.valobj;

/**
 * FeishuConnectionState 表示飞书 Bot 长连接的连接状态。
 */
public enum FeishuConnectionState {

    /** 空闲：未建立长连接。 */
    IDLE,
    /** 连接中：已发起建连但尚未就绪。 */
    CONNECTING,
    /** 已连接：长连接就绪，可收发消息。 */
    CONNECTED,
    /** 异常：建连或运行期出错。 */
    ERROR;

    /**
     * 是否为在线状态。
     *
     * @return 仅当状态为 CONNECTED 时返回 true
     */
    public boolean isOnline() {
        return this == CONNECTED;
    }
}
