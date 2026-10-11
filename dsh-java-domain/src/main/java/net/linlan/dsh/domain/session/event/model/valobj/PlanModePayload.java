package net.linlan.dsh.domain.session.event.model.valobj;

/**
 * Payload for the {@code plan/mode} session event.
 * <p>
 * Records a plan-mode transition: entering or exiting plan mode, with
 * the approved plan content when exiting.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public record PlanModePayload(
        String mode,         // "enter" | "exit"
        String plan          // the approved plan text (null on enter)
) {
    public PlanModePayload {
        if (mode == null || (!mode.equals("enter") && !mode.equals("exit"))) {
            throw new IllegalArgumentException("mode must be 'enter' or 'exit'");
        }
    }
}
