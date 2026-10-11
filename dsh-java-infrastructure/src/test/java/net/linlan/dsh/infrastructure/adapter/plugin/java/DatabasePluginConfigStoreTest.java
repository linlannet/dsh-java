package net.linlan.dsh.infrastructure.adapter.plugin.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.linlan.dsh.infrastructure.dao.IHarnessPluginConfigDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessPluginConfigPO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatabasePluginConfigStoreTest {

    @Test
    void defaultsReplaceOnlyDefaultLayer() {
        RecordingDao dao = new RecordingDao();
        DatabasePluginConfigStore store = new DatabasePluginConfigStore(dao);

        store.setDefaults("demo", Map.of("region", "cn"));
        store.setDefaults("demo", Map.of("region", "eu"));

        assertEquals(Optional.of("eu"), store.get("demo", "region"));
        assertEquals(Map.of("region", "eu"), store.getAll("demo"));
    }

    @Test
    void overrideWinsAndCanBeCleared() {
        RecordingDao dao = new RecordingDao();
        DatabasePluginConfigStore store = new DatabasePluginConfigStore(dao);

        store.setDefaults("demo", Map.of("region", "cn"));
        store.setOverride("demo", "region", "eu");
        assertEquals(Optional.of("eu"), store.get("demo", "region"));

        store.removeOverride("demo", "region");
        assertEquals(Optional.of("cn"), store.get("demo", "region"));

        store.setOverride("demo", "region", "eu");
        store.clearOverrides("demo");
        assertEquals(Optional.of("cn"), store.get("demo", "region"));
    }

    @Test
    void getAllMergesOverrideOverDefault() {
        RecordingDao dao = new RecordingDao();
        DatabasePluginConfigStore store = new DatabasePluginConfigStore(dao);

        store.setDefaults("demo", Map.of("region", "cn", "timeout", "30"));
        store.setOverride("demo", "region", "eu");

        assertEquals(Map.of("region", "eu", "timeout", "30"), store.getAll("demo"));
    }

    @Test
    void blankKeysAreIgnoredAndValuesAreNormalized() {
        RecordingDao dao = new RecordingDao();
        DatabasePluginConfigStore store = new DatabasePluginConfigStore(dao);

        store.setDefaults("demo", Map.of(" region ", ""));

        assertEquals(Optional.of(""), store.get("demo", "region"));
        assertEquals(Map.of("region", ""), store.getAll("demo"));
    }

    private static final class RecordingDao implements IHarnessPluginConfigDao {

        private final Map<String, HarnessPluginConfigPO> rows = new HashMap<>();

        @Override
        public void save(HarnessPluginConfigPO configPO) {
            rows.put(configPO.pluginId() + ":" + configPO.configKey() + ":" + configPO.configLayer(), configPO);
        }

        @Override
        public List<HarnessPluginConfigPO> findEffective(String pluginId, String configKey) {
            List<HarnessPluginConfigPO> result = new ArrayList<>();
            Optional<HarnessPluginConfigPO> override = Optional.ofNullable(
                    rows.get(pluginId + ":" + configKey + ":OVERRIDE"));
            Optional<HarnessPluginConfigPO> fallback = Optional.ofNullable(
                    rows.get(pluginId + ":" + configKey + ":DEFAULT"));
            override.ifPresent(result::add);
            fallback.ifPresent(result::add);
            return result;
        }

        @Override
        public List<HarnessPluginConfigPO> listByPluginId(String pluginId) {
            List<HarnessPluginConfigPO> result = new ArrayList<>();
            rows.values().stream()
                    .filter(row -> pluginId.equals(row.pluginId()))
                    .filter(row -> "DEFAULT".equals(row.configLayer()))
                    .forEach(result::add);
            rows.values().stream()
                    .filter(row -> pluginId.equals(row.pluginId()))
                    .filter(row -> "OVERRIDE".equals(row.configLayer()))
                    .forEach(result::add);
            return result;
        }

        @Override
        public void deleteByPluginIdAndLayer(String pluginId, String configLayer) {
            rows.keySet().removeIf(key -> key.startsWith(pluginId + ":") && key.endsWith(":" + configLayer));
        }

        @Override
        public void deleteByPluginIdAndKeyAndLayer(String pluginId, String configKey, String configLayer) {
            rows.remove(pluginId + ":" + configKey + ":" + configLayer);
        }
    }
}
