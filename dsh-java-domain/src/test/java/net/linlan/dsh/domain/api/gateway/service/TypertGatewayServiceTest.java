package net.linlan.dsh.domain.api.gateway.service;

import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import net.linlan.dsh.domain.api.gateway.model.valobj.InvokeRemoteRequest;
import net.linlan.dsh.domain.api.gateway.model.valobj.GatewayStreamItem;
import net.linlan.dsh.domain.typert.ITypertService;
import net.linlan.dsh.domain.typert.adapter.port.ITypertRegistryContract;
import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import net.linlan.dsh.domain.typert.model.entity.TypertContribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypertGatewayService 网关分派")
class TypertGatewayServiceTest {

    private static class StubTypertService implements ITypertService {
        @Override
        public void registerContribution(TypertContribution contribution) { }

        @Override
        public Optional<InvocationDescriptor> resolveInvocation(String endpoint) {
            if ("session/create".equals(endpoint)) {
                return Optional.of(new InvocationDescriptor(
                        "session", "create",
                        Map.of("sessionId", Map.of("required", true)),
                        Map.of(), "创建会话"
                ));
            }
            return Optional.empty();
        }

        @Override
        public List<InvocationDescriptor> listInvocations() { return List.of(); }

        @Override
        public ITypertRegistryContract registry() { return null; }
    }

    @Test
    @DisplayName("分派已知 endpoint 成功")
    void dispatchKnownEndpoint() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var peer = PeerScope.owner("conn-1", "peer-1");
        var request = InvokeRemoteRequest.of("session/create", Map.of("sessionId", "s1"), peer);

        var handle = gateway.dispatch(request);
        assertNotNull(handle);
        assertTrue(handle.streamId().startsWith("conn-1:"));
        assertEquals("session/create", handle.descriptor().endpoint());
    }

    @Test
    @DisplayName("分派未知 endpoint 抛异常")
    void dispatchUnknownEndpoint() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var peer = PeerScope.owner("conn-1", "peer-1");
        var request = InvokeRemoteRequest.of("unknown/endpoint", Map.of(), peer);

        assertThrows(TypertGatewayService.GatewayDispatchException.class, () -> gateway.dispatch(request));
    }

    @Test
    @DisplayName("缺少必需参数校验失败")
    void missingRequiredParam() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var peer = PeerScope.owner("conn-1", "peer-1");
        var request = InvokeRemoteRequest.of("session/create", Map.of(), peer);

        assertThrows(TypertGatewayService.GatewayDispatchException.class, () -> gateway.dispatch(request));
    }

    @Test
    @DisplayName("多路复用流重连恢复未确认流项")
    void multiplexedReconnectRestore() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var connectionId = "conn-2";

        gateway.emit(connectionId, "event-1");
        gateway.emit(connectionId, "event-2");
        gateway.acknowledge(connectionId, 1);

        var peer = PeerScope.owner(connectionId, "peer-2");
        var pending = gateway.subscribeMultiplexed(peer, 0);
        assertFalse(pending.isEmpty());
        assertTrue(pending.stream().anyMatch(i -> i.sequence() == 2));
    }

    @Test
    @DisplayName("Peer 断连后流状态标记为 disconnected")
    void peerDisconnect() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var connectionId = "conn-3";

        gateway.emit(connectionId, "event-1");
        gateway.disposePeer(connectionId);

        var peer = PeerScope.owner(connectionId, "peer-3");
        var pending = gateway.subscribeMultiplexed(peer, -1);
        assertFalse(pending.isEmpty());
    }

    @Test
    @DisplayName("未知 Peer 订阅返回空列表")
    void unknownPeerSubscribe() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var peer = PeerScope.owner("unknown-conn", "unknown-peer");
        var result = gateway.subscribeMultiplexed(peer, -1);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("cancel 移除流上下文")
    void cancelStream() {
        var gateway = new TypertGatewayService(new StubTypertService());
        var peer = PeerScope.owner("conn-4", "peer-4");
        var request = InvokeRemoteRequest.of("session/create", Map.of("sessionId", "s1"), peer);
        var handle = gateway.dispatch(request);

        gateway.cancel(handle.streamId());
        assertNotNull(handle.streamId());
    }
}
