package net.linlan.dsh.domain.api.gateway.controller.workspace;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * WorkspaceFilesController 提供有界文件读取：分页 UTF-8 与有界窗口原始字节、
 * FS 变化监听、目录列举，不提供修改操作。
 *
 */
public class WorkspaceFilesController implements IRemoteController {

    @Override
    public String namespace() {
        return "workspace.files";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "read" -> Map.of("content", "", "encoding", "utf-8", "page", args.getOrDefault("page", 0));
            case "readBytes" -> Map.of("bytes", new byte[0], "window", args.getOrDefault("window", Map.of()));
            case "list" -> Map.of("entries", java.util.List.of());
            case "watch" -> Map.of("watchId", "");
            case "unwatch" -> Map.of("status", "unwatched");
            default -> throw new UnsupportedOperationException("未知 workspace.files 方法: " + method);
        };
    }
}
