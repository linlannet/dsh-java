package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.spi.PluginConfigStore;
import net.linlan.dsh.infrastructure.dao.IHarnessPluginConfigDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessPluginConfigPO;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DatabasePluginConfigStore implements PluginConfigStore {

    private static final String LAYER_DEFAULT = "DEFAULT";
    private static final String LAYER_OVERRIDE = "OVERRIDE";

    private final IHarnessPluginConfigDao pluginConfigDao;

    public DatabasePluginConfigStore(IHarnessPluginConfigDao pluginConfigDao) {
        this.pluginConfigDao = pluginConfigDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> get(String pluginId, String key) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(key, "key");
        return pluginConfigDao.findEffective(pluginId, key).stream()
                .findFirst()
                .map(HarnessPluginConfigPO::configValue);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAll(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        Map<String, String> merged = new HashMap<>();
        for (HarnessPluginConfigPO config : pluginConfigDao.listByPluginId(pluginId)) {
            merged.put(config.configKey(), value(config.configValue()));
        }
        return Map.copyOf(merged);
    }

    @Override
    @Transactional
    public void setOverride(String pluginId, String key, String value) {
        save(pluginId, key, value, LAYER_OVERRIDE);
    }

    @Override
    @Transactional
    public void removeOverride(String pluginId, String key) {
        pluginConfigDao.deleteByPluginIdAndKeyAndLayer(pluginId, key, LAYER_OVERRIDE);
    }

    @Override
    @Transactional
    public void clearOverrides(String pluginId) {
        pluginConfigDao.deleteByPluginIdAndLayer(pluginId, LAYER_OVERRIDE);
    }

    @Override
    @Transactional
    public void setDefaults(String pluginId, Map<String, String> defaults) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(defaults, "defaults");
        pluginConfigDao.deleteByPluginIdAndLayer(pluginId, LAYER_DEFAULT);
        defaults.forEach((key, value) -> save(pluginId, key, value, LAYER_DEFAULT));
    }

    private void save(String pluginId, String key, String value, String layer) {
        Objects.requireNonNull(key, "key");
        String normalizedKey = key.trim();
        if (normalizedKey.isEmpty()) {
            return;
        }
        pluginConfigDao.save(new HarnessPluginConfigPO(
                pluginId, normalizedKey, value == null ? "" : value, layer, Instant.now()
        ));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
