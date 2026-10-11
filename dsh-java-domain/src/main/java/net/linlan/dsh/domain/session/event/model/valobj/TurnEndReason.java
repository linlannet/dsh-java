package net.linlan.dsh.domain.session.event.model.valobj;
/**
 * TurnEndReason 定义当前模块中的interface契约与职责。
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 */
public sealed interface TurnEndReason permits
        TurnEndReason.Completed,
        TurnEndReason.Aborted,
        TurnEndReason.Blocked,
        TurnEndReason.Error,
        TurnEndReason.MaxTokens,
        TurnEndReason.Interrupted {
    /**
     * Completed 定义当前模块中的record契约与职责。
     */
    record Completed() implements TurnEndReason {}
    /**
     * Aborted 定义当前模块中的record契约与职责。
     */
    record Aborted(String reason) implements TurnEndReason {
        public Aborted { reason = reason == null ? "" : reason; }
    }
    /**
     * Blocked 定义当前模块中的record契约与职责。
     */
    record Blocked() implements TurnEndReason {}
    /**
     * Error 定义当前模块中的record契约与职责。
     */
    record Error(String message, String code) implements TurnEndReason {
        public Error { message = message == null ? "" : message; code = code == null ? "UNKNOWN" : code; }
    }
    /**
     * MaxTokens 定义当前模块中的record契约与职责。
     */
    record MaxTokens() implements TurnEndReason {}
    /**
     * Interrupted 定义当前模块中的record契约与职责。
     */
    record Interrupted() implements TurnEndReason {}
}
