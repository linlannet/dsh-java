package net.linlan.dsh.domain.core.scope.adapter.port;

import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.SubscriptionToken;

import java.util.List;

/**
 * IScopeSubscriptionPort 是作用域订阅元数据的持久化端口。
 * <p>用于可选地持久化订阅的 scope 元数据（不含 handler），支持跨重启恢复订阅关系。</p>
 * <p>handler 由 {@link net.linlan.dsh.domain.core.scope.service.ScopeEventDispatcher} 在内存中维护，
 * 此端口仅记录 scope ↔ token 映射用于管理/监控。</p>
 */
public interface IScopeSubscriptionPort {

    /**
     * 记录订阅元数据。
     */
    void recordSubscription(ScopeKey scope, SubscriptionToken token);

    /**
     * 移除订阅元数据。
     */
    void removeSubscription(SubscriptionToken token);

    /**
     * 列出全部活跃订阅元数据。
     */
    List<SubscriptionRecord> listSubscriptions();

    /**
     * 订阅元数据记录。
     */
    record SubscriptionRecord(ScopeKey scope, SubscriptionToken token) {}
}
