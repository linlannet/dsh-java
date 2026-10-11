package net.linlan.dsh.domain.core.scope.model.valobj;

/**
 * ScopedEvent 是带有作用域键的事件。
 *
 * @param scopeKey  事件所属的作用域
 * @param type      事件类型（如 "session/event"、"tool/call"）
 * @param payload   事件负载
 * @param timestamp 事件时间戳（epoch 纳秒）
 * @param <T>       负载类型
 */
public record ScopedEvent<T>(ScopeKey scopeKey, String type, T payload, long timestamp) {

    public ScopedEvent {
        if (scopeKey == null) {
            throw new IllegalArgumentException("scopeKey must not be null");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
    }

    /**
     * 创建带当前时间戳的事件。
     */
    public static <T> ScopedEvent<T> of(ScopeKey scopeKey, String type, T payload) {
        return new ScopedEvent<>(scopeKey, type, payload, System.currentTimeMillis());
    }
}
