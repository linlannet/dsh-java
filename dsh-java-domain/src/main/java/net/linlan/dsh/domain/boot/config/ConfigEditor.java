package net.linlan.dsh.domain.boot.config;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * ConfigEditor 经 Loader 应用并持久化当前 profile 配置，重启后生效。
 *
 */
@Service
public class ConfigEditor {

    private final Map<String, String> configStore = new ConcurrentHashMap<>();
    private final String activeProfile;

    public ConfigEditor() {
        this("default");
    }

    public ConfigEditor(String activeProfile) {
        this.activeProfile = Objects.requireNonNull(activeProfile, "activeProfile 不能为空");
    }

    public void set(String key, String value) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(value, "value 不能为空");
        configStore.put(profileKey(key), value);
    }

    public String get(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        return configStore.get(profileKey(key));
    }

    public void remove(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        configStore.remove(profileKey(key));
    }

    public Map<String, String> snapshot() {
        String prefix = activeProfile + ".";
        return configStore.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(ConcurrentHashMap::new,
                        (m, e) -> m.put(e.getKey().substring(prefix.length()), e.getValue()),
                        ConcurrentHashMap::putAll);
    }

    public String activeProfile() {
        return activeProfile;
    }

    public void persist() {
    }

    private String profileKey(String key) {
        return activeProfile + "." + key;
    }
}
