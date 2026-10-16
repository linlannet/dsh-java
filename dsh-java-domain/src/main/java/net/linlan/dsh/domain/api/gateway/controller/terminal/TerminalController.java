package net.linlan.dsh.domain.api.gateway.controller.terminal;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * TerminalController 在 Session 当前工作目录打开交互式 shell、屏幕恢复、重连。
 * 终端输出不进入 Agent 对话记录。
 *
 */
public class TerminalController implements IRemoteController {

    @Override
    public String namespace() {
        return "terminal";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "open" -> Map.of("terminalId", "", "cwd", args.getOrDefault("cwd", ""));
            case "screen" -> Map.of("lines", java.util.List.of(), "cursor", Map.of());
            case "restore" -> Map.of("status", "restored", "terminalId", args.getOrDefault("terminalId", ""));
            case "reconnect" -> Map.of("status", "reconnected", "terminalId", args.getOrDefault("terminalId", ""));
            case "close" -> Map.of("status", "closed", "terminalId", args.getOrDefault("terminalId", ""));
            default -> throw new UnsupportedOperationException("未知 terminal 方法: " + method);
        };
    }
}
