package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ListAgentsTool 列出当前会话创建的子代理。
 *
 * <p>调用流程：读取已注册会话 → 解析名称/状态/描述 → 返回结构化列表。</p>
 * <p>示例：返回 {@code [{name:"reviewer", status:"running"}]}。</p>
 */
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
        List<Map<String, Object>> agents = knownAgentIds.stream()
                .map(driver::info)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(InProcessSubagentDriver.AgentInfo::name))
                .map(ListAgentsTool::row)
                .toList();
        return Map.of(
                "capacity", Map.of("total", agents.size(), "limit", driver.capacityLimit()),
                "agents", agents
        );
    }

    private static Map<String, Object> row(InProcessSubagentDriver.AgentInfo info) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", info.name());
        row.put("status", info.alive() ? "running" : "done");
        if (info.description() != null && !info.description().isBlank()) {
            row.put("description", info.description());
        }
        return row;
    }
}
