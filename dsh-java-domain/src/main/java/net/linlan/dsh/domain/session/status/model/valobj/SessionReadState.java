package net.linlan.dsh.domain.session.status.model.valobj;

/**
 * SessionReadState 表示宿主内存中的单会话阅读记录。
 *
 * <p>调用流程：turn/end 事件 → 更新完成位置 → 阅读确认推进已读位置。</p>
 * <p>示例：完成第 3 轮但只确认到第 2 轮时，会话为未读。</p>
 *
 * @param sessionId     会话标识
 * @param completedTurn 宿主最近完成的轮次（0 表示无）
 * @param failed        最近完成轮次是否以执行错误结束
 * @param readTurn      已确认的阅读位置（0 表示无）
 * @param manualUnread  手动未读标记
 */
public record SessionReadState(
        String sessionId,
        long completedTurn,
        boolean failed,
        long readTurn,
        boolean manualUnread
) {

    /**
     * 创建空阅读记录。
     *
     * @param sessionId 会话标识
     * @return 阅读记录
     */
    public static SessionReadState empty(String sessionId) {
        return new SessionReadState(sessionId, 0, false, 0, false);
    }

    /**
     * 是否存在未读完成轮次或手动标记。
     *
     * @return true 表示未读
     */
    public boolean completionUnread() {
        return manualUnread || completedTurn > readTurn;
    }

    /**
     * 记录一个完成轮次。
     *
     * @param failed 该轮是否失败
     * @return 更新后的记录
     */
    public SessionReadState withCompletion(boolean failed) {
        return new SessionReadState(sessionId, completedTurn + 1, failed, readTurn, manualUnread);
    }

    /**
     * 推进已读位置（单调、且不超过最近完成轮次）。
     *
     * @param throughTurn 请求确认到的轮次
     * @return 更新后的记录
     */
    public SessionReadState advanceRead(long throughTurn) {
        long target = Math.min(Math.max(throughTurn, readTurn), completedTurn);
        return new SessionReadState(sessionId, completedTurn, failed, target, manualUnread);
    }

    /**
     * 设置手动未读标记。
     *
     * @param unread 是否未读
     * @return 更新后的记录
     */
    public SessionReadState withManualUnread(boolean unread) {
        return new SessionReadState(sessionId, completedTurn, failed, readTurn, unread);
    }
}
