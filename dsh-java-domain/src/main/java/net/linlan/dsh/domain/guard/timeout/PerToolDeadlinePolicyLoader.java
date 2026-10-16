package net.linlan.dsh.domain.guard.timeout;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * PerToolDeadlinePolicyLoader 从 Properties 或 Map 配置构建 PerToolDeadlinePolicy。
 *
 * <p>配置格式：key 为工具名，value 为超时时间（支持 "30s"、"5m"、"1h"、"500ms" 等后缀）。</p>
 * <p>特殊 key "default" 设置默认超时。</p>
 */
public final class PerToolDeadlinePolicyLoader {

    private static final String DEFAULT_KEY = "default";

    public static PerToolDeadlinePolicy fromProperties(Properties props) {
        Map<String, Duration> perTool = new LinkedHashMap<>();
        Duration defaultDeadline = Duration.ofSeconds(30);

        for (String name : props.stringPropertyNames()) {
            String value = props.getProperty(name);
            Duration duration = parseDuration(value);
            if (DEFAULT_KEY.equalsIgnoreCase(name)) {
                defaultDeadline = duration;
            } else {
                perTool.put(name, duration);
            }
        }

        return new PerToolDeadlinePolicy(perTool, defaultDeadline);
    }

    public static PerToolDeadlinePolicy fromMap(Map<String, String> config) {
        Properties props = new Properties();
        config.forEach(props::setProperty);
        return fromProperties(props);
    }

    public static Duration parseDuration(String value) {
        String trimmed = value.trim().toLowerCase();
        if (trimmed.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim()));
        }
        if (trimmed.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
        }
        if (trimmed.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
        }
        if (trimmed.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
        }
        return Duration.ofSeconds(Long.parseLong(trimmed));
    }
}
