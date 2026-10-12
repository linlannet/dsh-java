package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ListAgentsTool {

    private final InProcessSubagentDriver driver;
    private final Set<String> knownAgentIds = ConcurrentHashMap.newKeySet();

    public ListAgentsTool(InProcessSubagentDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    public void register(String subagentId) {
        knownAgentIds.add(subagentId);
    }

    public Map<String, Object> execute(Map<String, Object> args) {
        List<String> alive = knownAgentIds.stream()
                .filter(driver::isAlive)
                .toList();
        return Map.of("agents", alive, "count", alive.size());
    }
}
