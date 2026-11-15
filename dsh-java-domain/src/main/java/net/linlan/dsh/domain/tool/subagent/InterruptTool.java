package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;

import java.util.Map;
import java.util.Objects;

/**
 * InterruptTool 中断子代理执行。
 *
 * <p>调用流程：读取 agent（名称或会话 id） → 解析会话 → 标记中断。</p>
 * <p>示例：{@code interrupt_agent(agent="reviewer")}。</p>
 */
public final class InterruptTool {

    private final InProcessSubagentDriver driver;

    public InterruptTool(InProcessSubagentDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    public Map<String, Object> execute(Map<String, Object> args) {
        rejectLegacyIdParams(args);
        String agent = requireString(args, "agent");
        String subagentId = driver.resolveAgent(agent);
        driver.interrupt(subagentId);
        return Map.of("agent", agent, "subagentId", subagentId, "interrupted", true);
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
