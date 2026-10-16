package net.linlan.dsh.domain.api.gateway.model.valobj;

import java.util.Map;
import java.util.Objects;

/**
 * InvokeRemoteRequest 表示一次 Remote 调用请求。
 *
 * <p>携带 endpoint、参数、Peer 作用域与取消信号，由 Gateway 分派至 Host。</p>
 *
 */
public record InvokeRemoteRequest(
        String endpoint,
        Map<String, Object> args,
        PeerScope peer,
        String cancellationSignalId
) {
    public InvokeRemoteRequest {
        Objects.requireNonNull(endpoint, "endpoint 不能为空");
        Objects.requireNonNull(args, "args 不能为空");
        Objects.requireNonNull(peer, "peer 不能为空");
    }

    public static InvokeRemoteRequest of(String endpoint, Map<String, Object> args, PeerScope peer) {
        return new InvokeRemoteRequest(endpoint, args, peer, null);
    }
}
