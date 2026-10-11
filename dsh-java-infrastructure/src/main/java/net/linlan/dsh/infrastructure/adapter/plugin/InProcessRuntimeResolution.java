package net.linlan.dsh.infrastructure.adapter.plugin;

import net.linlan.dsh.domain.plugin.runtime.adapter.port.IRuntimeResolutionPort;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.ResolvedPackage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InProcessRuntimeResolution 是进程内运行时解析适配器。
 * <p>维护 {@code Map<String, ResolvedPackage>} 作为解析映射。</p>
 */
@Component
public class InProcessRuntimeResolution implements IRuntimeResolutionPort {

    private final Map<String, ResolvedPackage> store = new ConcurrentHashMap<>();

    @Override
    public Map<String, ResolvedPackage> resolutionMap() {
        return Map.copyOf(store);
    }

    @Override
    public void refresh(String moduleId) {
        var existing = store.get(moduleId);
        if (existing != null) {
            store.put(moduleId, new ResolvedPackage(
                    existing.moduleId(),
                    existing.entryPoint(),
                    existing.sourcePath(),
                    existing.packageType(),
                    java.time.Instant.now()
            ));
        }
    }

    @Override
    public Optional<ResolvedPackage> resolve(String moduleId) {
        return Optional.ofNullable(store.get(moduleId));
    }

    /**
     * 注册或更新一个已解析的包。
     *
     * @param pkg 已解析的包
     */
    @Override
    public void register(ResolvedPackage pkg) {
        store.put(pkg.moduleId(), pkg);
    }

    /**
     * 移除一个已解析的包。
     *
     * @param moduleId 模块标识
     */
    public void unregister(String moduleId) {
        store.remove(moduleId);
    }
}
