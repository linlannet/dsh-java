package net.linlan.dsh.infrastructure.adapter.scope;

import net.linlan.dsh.domain.core.scope.adapter.port.IScopeSubscriptionPort;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.SubscriptionToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemoryScopeSubscriptionPort 是 IScopeSubscriptionPort 的内存适配器实现。
 * <p>用 {@link ConcurrentHashMap} 维护订阅元数据，进程重启后丢失。</p>
 */
@Component
@ConditionalOnProperty(name = "dsh.scope.enabled", havingValue = "true")
public class InMemoryScopeSubscriptionPort implements IScopeSubscriptionPort {

    private final ConcurrentHashMap<SubscriptionToken, ScopeKey> store = new ConcurrentHashMap<>();

    @Override
    public void recordSubscription(ScopeKey scope, SubscriptionToken token) {
        store.put(token, scope);
    }

    @Override
    public void removeSubscription(SubscriptionToken token) {
        if (token != null) {
            store.remove(token);
        }
    }

    @Override
    public List<SubscriptionRecord> listSubscriptions() {
        var records = new ArrayList<SubscriptionRecord>(store.size());
        store.forEach((token, scope) -> records.add(new SubscriptionRecord(scope, token)));
        return List.copyOf(records);
    }
}
