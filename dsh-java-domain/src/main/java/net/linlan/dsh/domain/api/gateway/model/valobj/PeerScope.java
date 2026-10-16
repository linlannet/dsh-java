package net.linlan.dsh.domain.api.gateway.model.valobj;

import java.util.Objects;

/**
 * PeerScope 表示一个 Remote Peer 的连接作用域。
 *
 * <p>每个 Peer 连接拥有唯一连接 ID，owner Peer 拥有全部权限，非 owner Peer 按角色判定。</p>
 *
 */
public record PeerScope(
        String connectionId,
        String peerId,
        boolean owner,
        String role
) {
    public PeerScope {
        Objects.requireNonNull(connectionId, "connectionId 不能为空");
        Objects.requireNonNull(peerId, "peerId 不能为空");
        Objects.requireNonNull(role, "role 不能为空");
    }

    public static PeerScope owner(String connectionId, String peerId) {
        return new PeerScope(connectionId, peerId, true, "owner");
    }

    public static PeerScope guest(String connectionId, String peerId, String role) {
        return new PeerScope(connectionId, peerId, false, role);
    }
}
