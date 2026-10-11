package net.linlan.dsh.domain.sdk.service;

import net.linlan.dsh.domain.sdk.ISdkServerService;
import net.linlan.dsh.domain.sdk.adapter.port.ISdkTransportPort;
import net.linlan.dsh.domain.sdk.model.valobj.*;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SDK server service: handles incoming requests via the handler port and
 * emits notifications to connected clients.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
@Conditional(SdkStandaloneCondition.class)
public class SdkServerService implements ISdkServerService {

    private final ISdkTransportPort transport;
    private final ISessionEventLogService sessionEventLogService;

    public SdkServerService(ISdkTransportPort transport) {
        this(transport, null);
    }

    public SdkServerService(ISdkTransportPort transport, ISessionEventLogService sessionEventLogService) {
        this.transport = transport;
        this.sessionEventLogService = sessionEventLogService;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        transport.close();
    }

    @Override
    public void emitNotification(String method, Object params) {
        transport.notify(method, params);
    }

    @Override
    public List<SessionSummary> listSessions(int limit) {
        if (sessionEventLogService == null) {
            return List.of();
        }
        return List.of();
    }

    @Override
    public SessionDetail loadSession(String sessionId) {
        if (sessionEventLogService == null) {
            return null;
        }
        try {
            var events = sessionEventLogService.readAll(sessionId);
            SessionHeader header = sessionEventLogService.getHeader(sessionId);
            Instant createdAt = header != null ? header.createdAt() : Instant.EPOCH;
            return new SessionDetail(sessionId, "active", createdAt, new ArrayList<>(events));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean cancelSession(String sessionId) {
        emitNotification("session/cancelled", java.util.Map.of("sessionId", sessionId));
        return true;
    }

    @Override
    public void streamSession(String sessionId, Consumer<Object> sink) {
        if (sessionEventLogService == null) {
            return;
        }
        try {
            for (Object event : sessionEventLogService.readAll(sessionId)) {
                sink.accept(event);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<ToolInfo> listTools(String sessionId) {
        return List.of();
    }

    @Override
    public boolean cancelTool(String sessionId, String callId) {
        emitNotification("tool/cancelled", java.util.Map.of("sessionId", sessionId, "callId", callId));
        return true;
    }

    @Override
    public List<McpToolInfo> listMcpTools() {
        return List.of();
    }

    @Override
    public List<ResourceInfo> listResources(String scope) {
        return List.of();
    }
}
