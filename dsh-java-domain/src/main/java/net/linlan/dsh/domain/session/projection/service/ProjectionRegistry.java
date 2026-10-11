package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProjectionRegistry 是投影提供者的注册与路由服务。
 * <p>线程安全：内部用 {@link ConcurrentHashMap} 维护注册表。
 */
public final class ProjectionRegistry {

    private final ConcurrentHashMap<ProjectionType, ProjectionProvider<?>> providers = new ConcurrentHashMap<>();

    /**
     * 注册投影 provider。
     * @param provider 投影提供者
     */
    public void register(ProjectionProvider<?> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        providers.put(provider.type(), provider);
    }

    /**
     * 按类型路由解析 provider。
     * @param type 投影类型
     * @return 对应的 provider（未注册时返回 {@link Optional#empty()}）
     */
    public Optional<ProjectionProvider<?>> resolve(ProjectionType type) {
        return Optional.ofNullable(providers.get(type));
    }

    /**
     * 返回已注册的 provider 数量。
     */
    public int size() {
        return providers.size();
    }

    /**
     * 返回所有已注册的投影类型。
     */
    public Set<ProjectionType> registeredTypes() {
        return providers.keySet();
    }
}
