package net.linlan.dsh.domain.session.event.model.valobj;
/**
 * SessionEndSeedPayload 定义当前模块中的record契约与职责。
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 */
public record SessionEndSeedPayload() {}
