package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.spi.PluginConfigStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的插件配置存储实现。
 * <p>运行时覆盖值与安装时基础值分层保存，读取顺序为 override -> default -> empty。
 * 该实现适合单实例运行时；重启后运行时覆盖值会丢失，持久化需要替换适配器实现。
 * <p>数据示例：defaults.region="cn"，overrides.region="eu"，getAll 返回 region=eu。
 */
public final class InMemoryPluginConfigStore implements PluginConfigStore {

    private final Map<String, Map<String, String>> overrides = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> defaults = new ConcurrentHashMap<>();

    /**
     * 优先读取运行时覆盖值，再读取安装时基础配置。
     */
    @Override
    public Optional<String> get(String pluginId, String key) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(key, "key");
        Map<String, String> overrideMap = overrides.get(pluginId);
        if (overrideMap != null && overrideMap.containsKey(key)) {
            return Optional.ofNullable(overrideMap.get(key));
        }
        Map<String, String> defaultMap = defaults.get(pluginId);
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return Optional.ofNullable(defaultMap.get(key));
        }
        return Optional.empty();
    }

    /**
     * 合并基础配置和覆盖配置；覆盖值优先，并返回不可变 Map。
     */
    @Override
    public Map<String, String> getAll(String pluginId) {
        Map<String, String> merged = new HashMap<>();
        Map<String, String> defaultMap = defaults.get(pluginId);
        if (defaultMap != null) merged.putAll(defaultMap);
        Map<String, String> overrideMap = overrides.get(pluginId);
        if (overrideMap != null) merged.putAll(overrideMap);
        return Map.copyOf(merged);
    }

    /**
     * 设置一个运行时覆盖值。
     */
    @Override
    public void setOverride(String pluginId, String key, String value) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(key, "key");
        overrides.computeIfAbsent(pluginId, id -> new ConcurrentHashMap<>()).put(key, value);
    }

    /**
     * 移除一个运行时覆盖值；基础配置不受影响。
     */
    @Override
    public void removeOverride(String pluginId, String key) {
        Map<String, String> overrideMap = overrides.get(pluginId);
        if (overrideMap != null) overrideMap.remove(key);
    }

    /**
     * 清空指定插件的全部运行时覆盖值。
     */
    @Override
    public void clearOverrides(String pluginId) {
        overrides.remove(pluginId);
    }

    /**
     * 替换指定插件的基础配置，输入会被复制为不可变 Map。
     */
    @Override
    public void setDefaults(String pluginId, Map<String, String> configDefaults) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(configDefaults, "defaults");
        defaults.put(pluginId, Map.copyOf(configDefaults));
    }
}
