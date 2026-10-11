package net.linlan.dsh.domain.core.scope.service;

import net.linlan.dsh.domain.core.scope.adapter.port.IScopeSubscriptionPort;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeSubscription;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopedEvent;
import net.linlan.dsh.domain.core.scope.model.valobj.SubscriptionToken;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ScopeEventDispatcher 是作用域过滤的事件分发器。
 * <p>订阅者注册在某个 scope，事件发布在某个 scope。
 * 当事件的 scope 是订阅者 scope 的后代或相等时，订阅者收到事件（事件接纳向上扩展）。</p>
 * <p>线程安全：内部用 {@link ConcurrentHashMap} 维护订阅，支持并发 dispatch/subscribe/unsubscribe。</p>
 */
public class ScopeEventDispatcher {

    private final ConcurrentHashMap<SubscriptionToken, ScopeSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Optional<IScopeSubscriptionPort> port;

    /**
     * 创建不带持久化端口的事件分发器。
     */
    public ScopeEventDispatcher() {
        this.port = Optional.empty();
    }

    /**
     * 创建带可选持久化端口的事件分发器。
     * @param port 持久化端口，null 则不持久化
     */
    public ScopeEventDispatcher(IScopeSubscriptionPort port) {
        this.port = Optional.ofNullable(port);
    }

    /**
     * 订阅指定 scope 的事件。
     * @param scope   订阅的作用域
     * @param handler 事件处理器
     * @return 订阅令牌（用于取消订阅）
     */
    public SubscriptionToken subscribe(ScopeKey scope, Consumer<ScopedEvent<?>> handler) {
        var token = SubscriptionToken.generate();
        var subscription = new ScopeSubscription(scope, handler, token);
        subscriptions.put(token, subscription);
        port.ifPresent(p -> p.recordSubscription(scope, token));
        return token;
    }

    /**
     * 取消订阅。
     * @param token 订阅令牌
     */
    public void unsubscribe(SubscriptionToken token) {
        if (token == null) {
            return;
        }
        subscriptions.remove(token);
        port.ifPresent(p -> p.removeSubscription(token));
    }

    /**
     * 分发事件。
     * <p>遍历所有订阅者，按 {@code event.scope.isDescendantOfOrEquals(sub.scope)} 过滤通知。
     * handler 异常不中断其他订阅者的通知。</p>
     * @param event 待分发的事件
     */
    public void dispatch(ScopedEvent<?> event) {
        if (event == null) {
            return;
        }
        for (var subscription : subscriptions.values()) {
            if (event.scopeKey().isDescendantOfOrEquals(subscription.scope())) {
                try {
                    subscription.handler().accept(event);
                } catch (RuntimeException ignored) {
                    // handler 异常不中断其他订阅者
                }
            }
        }
    }

    /**
     * 返回当前活跃订阅数。
     */
    public int activeSubscriptionCount() {
        return subscriptions.size();
    }
}
