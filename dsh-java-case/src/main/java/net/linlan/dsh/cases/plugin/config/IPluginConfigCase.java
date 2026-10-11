package net.linlan.dsh.cases.plugin.config;

import java.util.List;
import java.util.Map;

public interface IPluginConfigCase {

    List<Map<String, String>> load(String pluginId);

    void save(String pluginId, List<Map<String, String>> configs);
}
