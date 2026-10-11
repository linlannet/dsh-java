package net.linlan.dsh.infrastructure.adapter.typert;

import net.linlan.dsh.domain.typert.adapter.port.*;
import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Typert registry contract implementation.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class InMemoryTypertRegistry implements ITypertRegistryContract {

    private final LocalRegistry local = new LocalRegistry();
    private final RemoteRegistry remotes = new RemoteRegistry();
    private final LookupRegistry lookups = new LookupRegistry();
    private final ContextRegistry contexts = new ContextRegistry();

    @Override public ITypertLocalRegistry local() { return local; }
    @Override public ITypertRemoteRegistry remotes() { return remotes; }
    @Override public ITypertLookupRegistry lookups() { return lookups; }
    @Override public ITypertContextRegistry contexts() { return contexts; }

    /**
     * LocalRegistry 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private static class LocalRegistry implements ITypertLocalRegistry {
        private final Map<String, InvocationDescriptor> store = new ConcurrentHashMap<>();
        @Override public void register(InvocationDescriptor d) { store.put(d.endpoint(), d); }
        @Override public Optional<InvocationDescriptor> get(String endpoint) { return Optional.ofNullable(store.get(endpoint)); }
        @Override public List<InvocationDescriptor> list() { return List.copyOf(store.values()); }
    }

    /**
     * RemoteRegistry 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private static class RemoteRegistry implements ITypertRemoteRegistry {
        private final Map<String, InvocationDescriptor> store = new ConcurrentHashMap<>();
        @Override public void register(InvocationDescriptor d) { store.put(d.endpoint(), d); }
        @Override public Optional<InvocationDescriptor> get(String endpoint) { return Optional.ofNullable(store.get(endpoint)); }
        @Override public List<InvocationDescriptor> list() { return List.copyOf(store.values()); }
    }

    /**
     * LookupRegistry 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private static class LookupRegistry implements ITypertLookupRegistry {
        private final Map<String, Object> store = new ConcurrentHashMap<>();
        @Override public void register(String key, Object provider) { store.put(key, provider); }
        @Override public Optional<Object> get(String key) { return Optional.ofNullable(store.get(key)); }
        @Override public List<String> keys() { return List.copyOf(store.keySet()); }
    }

    /**
     * ContextRegistry 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private static class ContextRegistry implements ITypertContextRegistry {
        private final Map<String, Object> hosts = new ConcurrentHashMap<>();
        private final Map<String, Object> clients = new ConcurrentHashMap<>();
        @Override public void registerHost(String key, Object resolver) { hosts.put(key, resolver); }
        @Override public Optional<Object> getHost(String key) { return Optional.ofNullable(hosts.get(key)); }
        @Override public void registerClient(String key, Object binder) { clients.put(key, binder); }
        @Override public Optional<Object> getClient(String key) { return Optional.ofNullable(clients.get(key)); }
    }
}
