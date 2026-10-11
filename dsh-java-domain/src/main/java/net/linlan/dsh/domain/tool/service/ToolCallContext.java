package net.linlan.dsh.domain.tool.service;

import java.util.Map;

/**
 * ToolCallContext 是工具调用的上下文，在五阶段管线中传递。
 *
 * @param toolName  工具名称
 * @param arguments 工具参数
 * @param sessionId 会话 ID
 * @param agentId   代理 ID
 */
public record ToolCallContext(String toolName, Map<String, Object> arguments, String sessionId, String agentId) {

    public ToolCallContext {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
