package net.linlan.dsh.domain.sdk;

import net.linlan.dsh.domain.sdk.model.valobj.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Application-facing SDK server seam: handles incoming requests and
 * emits notifications to connected clients.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ISdkServerService {

    void start();

    void stop();

    void emitNotification(String method, Object params);

    default List<SessionSummary> listSessions(int limit) {
        return List.of();
    }

    default SessionDetail loadSession(String sessionId) {
        return null;
    }

    default boolean cancelSession(String sessionId) {
        return false;
    }

    default void streamSession(String sessionId, Consumer<Object> sink) {
    }

    default List<ToolInfo> listTools(String sessionId) {
        return List.of();
    }

    default boolean cancelTool(String sessionId, String callId) {
        return false;
    }

    default List<McpToolInfo> listMcpTools() {
        return List.of();
    }

    default List<ResourceInfo> listResources(String scope) {
        return List.of();
    }
}
