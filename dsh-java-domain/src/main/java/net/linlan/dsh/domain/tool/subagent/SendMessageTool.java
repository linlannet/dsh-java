package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;

import java.util.Map;
import java.util.Objects;

public final class SendMessageTool {

    private final InProcessSubagentDriver driver;

    public SendMessageTool(InProcessSubagentDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    public Map<String, Object> execute(Map<String, Object> args) {
        String subagentId = requireString(args, "subagentId");
        String message = requireString(args, "message");
        driver.send(subagentId, message);
        return Map.of("subagentId", subagentId, "delivered", true);
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new IllegalArgumentException(key + " required");
        }
        return String.valueOf(v);
    }
}
