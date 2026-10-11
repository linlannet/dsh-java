package net.linlan.dsh.domain.spi;

import java.time.Instant;

/**
 * 插件事件总线上流转的不可变事件信封。
 * <p>source 和 name 必须非空；timestamp 为空时自动取当前时间；payload 可以为 null。
 * <p>数据示例：source="host"，name="plugin.started"，payload="time" 表示 time 插件已启动。
 *
 * @param source    事件来源：插件 ID 或宿主专用值 "host"
 * @param name      事件名称，例如 "tool.before" 或 "session.start"
 * @param payload   任意事件负载，可为 null
 * @param timestamp 创建时间戳
 */
public record PluginEvent(String source, String name, Object payload, Instant timestamp) {

    public PluginEvent {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must be non-blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be non-blank");
        }
        timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
