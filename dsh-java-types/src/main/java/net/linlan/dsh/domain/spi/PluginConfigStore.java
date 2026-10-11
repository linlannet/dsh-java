package net.linlan.dsh.domain.spi;

import java.util.Map;
import java.util.Optional;

/**
 * 插件运行时配置存储。
 * <p>配置解析顺序为：运行时覆盖值 -> 宿主基础配置 -> 插件默认值。
 * 插件只通过 {@link PluginContext#getConfig} 读取配置，不直接依赖具体存储实现。
 * <p>数据示例：key="region" 有 host default="cn" 和 override="eu"，插件读取到 "eu"。
 */
public interface PluginConfigStore {

    /**
     * 按插件 ID 和键解析配置。
     * <p>先查运行时覆盖值，再查宿主基础配置，最后查插件默认值。
     */
    Optional<String> get(String pluginId, String key);

    /**
     * 返回指定插件的全量合并配置。
     */
    Map<String, String> getAll(String pluginId);

    /**
     * 设置某个插件配置键的运行时覆盖值。
     */
    void setOverride(String pluginId, String key, String value);

    /**
     * 移除某个插件配置键的运行时覆盖值。
     */
    void removeOverride(String pluginId, String key);

    /**
     * 清空指定插件的全部运行时覆盖值。
     */
    void clearOverrides(String pluginId);

    /**
     * 设置插件的基础配置，通常在安装或启动时调用一次。
     */
    void setDefaults(String pluginId, Map<String, String> defaults);
}
