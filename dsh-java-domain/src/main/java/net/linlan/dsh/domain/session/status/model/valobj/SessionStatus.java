package net.linlan.dsh.domain.session.status.model.valobj;

/**
 * SessionStatus 表示会话的宿主状态投影。
 *
 * <p>调用流程：宿主阅读记录 + 运行状态 → 状态投影 → 前端展示。</p>
 * <p>示例：完成轮次未确认时 {@code completionUnread=true}，该轮失败时同时
 * {@code completionFailed=true}。</p>
 *
 * @param running          是否运行中
 * @param completionUnread 是否存在未确认的完成轮次或手动未读标记
 * @param completionFailed 未读完成轮次是否以执行错误结束
 */
public record SessionStatus(boolean running, boolean completionUnread, boolean completionFailed) {

    /**
     * 已读且空闲状态。
     *
     * @return 状态投影
     */
    public static SessionStatus idle() {
        return new SessionStatus(false, false, false);
    }

    /**
     * 按运行状态和阅读记录生成投影。
     *
     * @param running 是否运行中
     * @param state   阅读记录，可为 null（按已读处理）
     * @return 状态投影
     */
    public static SessionStatus of(boolean running, SessionReadState state) {
        if (state == null) {
            return new SessionStatus(running, false, false);
        }
        boolean unread = state.completionUnread();
        return new SessionStatus(running, unread, unread && state.failed());
    }
}
