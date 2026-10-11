package net.linlan.dsh.cases.plugin.config;

import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.spi.PluginConfigStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PluginConfigService implements IPluginConfigCase {

    private final PluginConfigStore pluginConfigStore;
    private final IPluginRegistryService pluginRegistryService;

    public PluginConfigService(
            PluginConfigStore pluginConfigStore,
            IPluginRegistryService pluginRegistryService
    ) {
        this.pluginConfigStore = pluginConfigStore;
        this.pluginRegistryService = pluginRegistryService;
    }

    @Override
    public List<Map<String, String>> load(String pluginId) {
        pluginRegistryService.loadPlugin(pluginId);
        return pluginConfigStore.getAll(pluginId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.of("key", entry.getKey(), "value", entry.getValue() == null ? "" : entry.getValue()))
                .toList();
    }

    @Override
    public void save(String pluginId, List<Map<String, String>> configs) {
        pluginRegistryService.loadPlugin(pluginId);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map<String, String> item : configs == null ? List.<Map<String, String>>of() : configs) {
            String key = item.get("key");
            if (key == null || key.isBlank()) continue;
            String value = item.get("value");
            normalized.put(key.trim(), value == null ? "" : value);
        }
        pluginConfigStore.clearOverrides(pluginId);
        pluginConfigStore.setDefaults(pluginId, normalized);
    }
}
