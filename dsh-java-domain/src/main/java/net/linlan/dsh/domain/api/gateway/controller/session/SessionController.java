package net.linlan.dsh.domain.api.gateway.controller.session;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * SessionController 管理会话生命周期、历史流、控制状态、技能发现、文件引用与 Agent-Session 身份策略。
 *
 */
public class SessionController implements IRemoteController {

    @Override
    public String namespace() {
        return "session";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "create" -> createSession(args, peer);
            case "open" -> openSession(args, peer);
            case "close" -> closeSession(args, peer);
            case "rename" -> renameSession(args, peer);
            case "history" -> sessionHistory(args, peer);
            case "controlState" -> controlState(args, peer);
            case "skills" -> discoverSkills(args, peer);
            case "fileReferences" -> fileReferences(args, peer);
            case "agentIdentity" -> agentIdentity(args, peer);
            default -> throw new UnsupportedOperationException("未知 session 方法: " + method);
        };
    }

    private Object createSession(Map<String, Object> args, PeerScope peer) {
        return Map.of("status", "created", "sessionId", args.getOrDefault("sessionId", ""));
    }

    private Object openSession(Map<String, Object> args, PeerScope peer) {
        return Map.of("status", "opened", "sessionId", args.getOrDefault("sessionId", ""));
    }

    private Object closeSession(Map<String, Object> args, PeerScope peer) {
        return Map.of("status", "closed", "sessionId", args.getOrDefault("sessionId", ""));
    }

    private Object renameSession(Map<String, Object> args, PeerScope peer) {
        return Map.of("status", "renamed", "sessionId", args.getOrDefault("sessionId", ""));
    }

    private Object sessionHistory(Map<String, Object> args, PeerScope peer) {
        return Map.of("history", java.util.List.of());
    }

    private Object controlState(Map<String, Object> args, PeerScope peer) {
        return Map.of("state", "idle");
    }

    private Object discoverSkills(Map<String, Object> args, PeerScope peer) {
        return Map.of("skills", java.util.List.of());
    }

    private Object fileReferences(Map<String, Object> args, PeerScope peer) {
        return Map.of("references", java.util.List.of());
    }

    private Object agentIdentity(Map<String, Object> args, PeerScope peer) {
        return Map.of("agentId", args.getOrDefault("agentId", ""), "sessionId", args.getOrDefault("sessionId", ""));
    }
}
