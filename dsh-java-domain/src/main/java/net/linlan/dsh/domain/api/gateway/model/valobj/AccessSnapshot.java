package net.linlan.dsh.domain.api.gateway.model.valobj;

import java.util.Objects;

/**
 * AccessSnapshot 表示一个 Peer 的访问快照。
 *
 * <p>每个 Peer 订阅自己的访问快照，用于 fail-fast 判定方法级访问权限。</p>
 *
 */
public record AccessSnapshot(
        String connectionId,
        RemoteAccessPolicy policy,
        long version
) {
    public AccessSnapshot {
        Objects.requireNonNull(connectionId, "connectionId 不能为空");
        Objects.requireNonNull(policy, "policy 不能为空");
    }
}
