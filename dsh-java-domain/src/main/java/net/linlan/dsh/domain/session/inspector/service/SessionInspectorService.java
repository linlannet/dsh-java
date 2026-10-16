package net.linlan.dsh.domain.session.inspector.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SessionInspectorService 检查原始 Session 日志与 Chat group/node 虚拟表格。
 * 实验性扩展，默认关闭。
 */
public class SessionInspectorService {

    public List<String> inspectRawLog(String sessionId, int page, int pageSize) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        return List.of();
    }

    public Map<String, Object> inspectVirtualTable(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        return Map.of();
    }

    public String locateToChat(String sessionId, String nodeId) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        return sessionId + "#" + nodeId;
    }
}
