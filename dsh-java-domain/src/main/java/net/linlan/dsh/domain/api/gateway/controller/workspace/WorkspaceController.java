package net.linlan.dsh.domain.api.gateway.controller.workspace;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * WorkspaceController 管理工作区的创建、重命名、移除、重排、会话归档与完整工作区投影。
 *
 */
public class WorkspaceController implements IRemoteController {

    @Override
    public String namespace() {
        return "workspace";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "create" -> Map.of("status", "created", "workspaceId", args.getOrDefault("workspaceId", ""));
            case "rename" -> Map.of("status", "renamed", "workspaceId", args.getOrDefault("workspaceId", ""));
            case "remove" -> Map.of("status", "removed", "workspaceId", args.getOrDefault("workspaceId", ""));
            case "reorder" -> Map.of("status", "reordered");
            case "archiveSession" -> Map.of("status", "archived");
            case "projection" -> Map.of("projection", Map.of());
            default -> throw new UnsupportedOperationException("未知 workspace 方法: " + method);
        };
    }
}
