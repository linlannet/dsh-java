package net.linlan.dsh.domain.api.gateway.service;

import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import net.linlan.dsh.domain.api.gateway.model.valobj.RemoteAccessPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteAccessService 访问控制")
class RemoteAccessServiceTest {

    @Test
    @DisplayName("owner Peer 全部允许")
    void ownerPeerAllowed() {
        var service = new RemoteAccessService();
        var peer = PeerScope.owner("conn-1", "peer-1");
        assertTrue(service.canAccess(peer, "session"));
        assertTrue(service.canAccess(peer, "anything"));
    }

    @Test
    @DisplayName("非 owner Peer 按策略判定 fail-fast")
    void nonOwnerPolicyFailFast() {
        var service = new RemoteAccessService();
        var peer = PeerScope.guest("conn-2", "peer-2", "guest");
        service.installPolicy(peer, RemoteAccessPolicy.of("guest", Set.of("session")));

        assertTrue(service.canAccess(peer, "session"));
        assertFalse(service.canAccess(peer, "settings"));
    }

    @Test
    @DisplayName("未安装策略的非 owner Peer 拒绝访问")
    void noPolicyDeniesAccess() {
        var service = new RemoteAccessService();
        var peer = PeerScope.guest("conn-3", "peer-3", "guest");
        assertFalse(service.canAccess(peer, "session"));
    }

    @Test
    @DisplayName("列表方法逐项过滤")
    void listFiltering() {
        var service = new RemoteAccessService();
        var peer = PeerScope.guest("conn-4", "peer-4", "guest");
        service.installPolicy(peer, RemoteAccessPolicy.of("guest", Set.of("session", "workspace")));

        var filtered = service.filterAccessible(peer, List.of("session", "workspace", "settings", "account"));
        assertEquals(List.of("session", "workspace"), filtered);
    }

    @Test
    @DisplayName("owner 列表方法不过滤")
    void ownerListNoFilter() {
        var service = new RemoteAccessService();
        var peer = PeerScope.owner("conn-5", "peer-5");
        var resources = List.of("session", "workspace", "settings");
        assertEquals(resources, service.filterAccessible(peer, resources));
    }

    @Test
    @DisplayName("dispose 后访问快照移除")
    void disposeRemovesSnapshot() {
        var service = new RemoteAccessService();
        var peer = PeerScope.guest("conn-6", "peer-6", "guest");
        service.installPolicy(peer, RemoteAccessPolicy.of("guest", Set.of("session")));
        service.dispose("conn-6");
        assertFalse(service.canAccess(peer, "session"));
    }
}
