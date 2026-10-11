package net.linlan.dsh.domain.model.entity;

/**
 * 调用方提供的一次工具调用描述。
 * 对应 TS 侧 {@code ToolExecutionInput}。
 *
 * @param callId     稳定的调用 ID，来自模型 ToolCallBlock.id
 * @param rootCallId 拥有本次执行树的根调用 ID
 * @param name       工具名称
 * @param arguments  已解析且可无损 JSON 序列化的参数
 *
 *
 */
public record ToolExecutionInput(
        String callId,
        String rootCallId,
        String name,
        Object arguments
) {
    public ToolExecutionInput {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must be non-blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be non-blank");
        }
    }

/** 根调用的便捷构造器（rootCallId 等于 callId）。 */
    public static ToolExecutionInput root(String callId, String name, Object arguments) {
        return new ToolExecutionInput(callId, callId, name, arguments);
    }
}
