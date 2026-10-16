package net.linlan.dsh.domain.api.gateway.service;

import net.linlan.dsh.domain.api.gateway.model.valobj.AccessSnapshot;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import net.linlan.dsh.domain.api.gateway.model.valobj.RemoteAccessPolicy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * RemoteAccessService 在 Gateway 解析参数前判定非 owner Peer 的方法级访问权限（fail-fast）。
 *
 * <p>列表方法经 allows() 逐项过滤，每个 Peer 订阅自己的访问快照 AccessSnapshot。</p>
 *
 */
@Service
public class RemoteAccessService {

    private final Map<String, AccessSnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(0);

    /**
     * 为 Peer 安装访问策略，生成访问快照。
     * @param peer Peer 作用域
     * @param policy 访问策略
     * @return 访问快照
     */
    public AccessSnapshot installPolicy(PeerScope peer, RemoteAccessPolicy policy) {
        Objects.requireNonNull(peer, "peer 不能为空");
        Objects.requireNonNull(policy, "policy 不能为空");
        AccessSnapshot snapshot = new AccessSnapshot(peer.connectionId(), policy, versionCounter.incrementAndGet());
        snapshots.put(peer.connectionId(), snapshot);
        return snapshot;
    }

    /**
     * 判定 Peer 是否有权访问指定资源（fail-fast）。
     * @param peer Peer 作用域
     * @param resource 资源标识
     * @return true 允许访问，false 拒绝
     */
    public boolean canAccess(PeerScope peer, String resource) {
        Objects.requireNonNull(peer, "peer 不能为空");
        if (peer.owner()) {
            return true;
        }
        AccessSnapshot snapshot = snapshots.get(peer.connectionId());
        if (snapshot == null) {
            return false;
        }
        return snapshot.policy().allows(resource);
    }

    /**
     * 对列表方法逐项过滤，仅返回 Peer 有权访问的资源。
     * @param peer Peer 作用域
     * @param resources 资源列表
     * @return 过滤后的资源列表
     */
    public List<String> filterAccessible(PeerScope peer, List<String> resources) {
        Objects.requireNonNull(peer, "peer 不能为空");
        if (peer.owner()) {
            return resources;
        }
        return resources.stream()
                .filter(r -> canAccess(peer, r))
                .collect(Collectors.toList());
    }

    /**
     * 获取 Peer 的访问快照。
     * @param connectionId Peer 连接 ID
     * @return 访问快照，不存在时返回 null
     */
    public AccessSnapshot snapshot(String connectionId) {
        return snapshots.get(connectionId);
    }

    /**
     * Peer 断连时移除其访问快照。
     * @param connectionId Peer 连接 ID
     */
    public void dispose(String connectionId) {
        snapshots.remove(connectionId);
    }
}
