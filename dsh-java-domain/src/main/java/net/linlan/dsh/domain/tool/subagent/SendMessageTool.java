package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;

import java.util.Map;
import java.util.Objects;

/**
 * SendMessageTool 向子代理投递消息。
 *
 * <p>调用流程：读取 agent（名称或会话 id） → 解析会话 → 投递消息。</p>
 * <p>示例：{@code send_message(agent="reviewer/checker", message="...")}。</p>
 */
public final class SendMessageTool {

    private final InProcessSubagentDriver driver;

    public SendMessageTool(InProcessSubagentDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    public Map<String, Object> execute(Map<String, Object> args) {
        rejectLegacyIdParams(args);
        String agent = requireString(args, "agent");
        String message = requireString(args, "message");
        String subagentId = driver.resolveAgent(agent);
        driver.send(subagentId, message);
        return Map.of("agent", agent, "subagentId", subagentId, "delivered", true);
    }

    private static void rejectLegacyIdParams(Map<String, Object> args) {
        if (args.containsKey("agent_id") || args.containsKey("subagentId")) {
            throw new ToolArgsError("agent_id is not supported; use agent");
        }
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new ToolArgsError(key + " is required");
        }
        return String.valueOf(v);
    }
}
