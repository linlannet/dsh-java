package net.linlan.dsh.domain.api.gateway.model.valobj;

import java.util.Objects;
import java.util.Set;

/**
 * RemoteAccessPolicy 表示 Remote 访问控制策略值对象。
 *
 * <p>owner Peer 全部允许，非 owner Peer 按角色与资源绑定判定。</p>
 *
 */
public record RemoteAccessPolicy(
        String principalKind,
        String role,
        Set<String> resourceBinding
) {
    public RemoteAccessPolicy {
        Objects.requireNonNull(principalKind, "principalKind 不能为空");
        Objects.requireNonNull(role, "role 不能为空");
        resourceBinding = resourceBinding == null ? Set.of() : Set.copyOf(resourceBinding);
    }

    public static RemoteAccessPolicy owner() {
        return new RemoteAccessPolicy("peer", "owner", Set.of("*"));
    }

    public static RemoteAccessPolicy of(String role, Set<String> resourceBinding) {
        return new RemoteAccessPolicy("peer", role, resourceBinding);
    }

    public boolean allows(String resource) {
        if ("owner".equals(role)) {
            return true;
        }
        if (resourceBinding.contains("*")) {
            return true;
        }
        return resourceBinding.contains(resource);
    }
}
