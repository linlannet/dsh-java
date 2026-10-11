package net.linlan.dsh.domain.core.scope.model.valobj;

import java.util.function.Consumer;

/**
 * ScopeSubscription 是作用域事件订阅。
 *
 * @param scope   订阅的作用域（订阅者在此 scope 或其祖先 scope 注册，可收到此 scope 及其后代的事件）
 * @param handler 事件处理器
 * @param token   订阅令牌（用于取消订阅）
 */
public record ScopeSubscription(ScopeKey scope, Consumer<ScopedEvent<?>> handler, SubscriptionToken token) {

    public ScopeSubscription {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
    }
}
