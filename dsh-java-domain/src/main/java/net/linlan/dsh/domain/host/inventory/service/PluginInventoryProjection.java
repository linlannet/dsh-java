package net.linlan.dsh.domain.host.inventory.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * PluginInventoryProjection 提供当前 Loader 条目的只读快照。
 *
 * <p>经 Remote pluginInventory/list 暴露，不暴露可变 Loader 状态。</p>
 *
 */
@Service
public class PluginInventoryProjection {

    private final Map<String, PluginEntry> entries = new ConcurrentHashMap<>();

    public void register(String pluginId, String displayName, String version, String status) {
        Objects.requireNonNull(pluginId, "pluginId 不能为空");
        entries.put(pluginId, new PluginEntry(pluginId, displayName, version, status));
    }

    public List<PluginEntry> list() {
        return List.copyOf(entries.values());
    }

    public PluginEntry get(String pluginId) {
        return entries.get(pluginId);
    }

    public int count() {
        return entries.size();
    }

    public record PluginEntry(String pluginId, String displayName, String version, String status) {
        public PluginEntry {
            Objects.requireNonNull(pluginId, "pluginId 不能为空");
        }
    }
}
