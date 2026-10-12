package net.linlan.dsh.domain.session.workspace.model.entity;

import java.util.List;
import java.util.Objects;

public record WorkspaceEntity(
        String workspaceId,
        String rootPath,
        List<String> attachedSessionIds
) {
    public WorkspaceEntity {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(rootPath, "rootPath");
        if (workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (rootPath.isBlank()) {
            throw new IllegalArgumentException("rootPath must not be blank");
        }
        attachedSessionIds = attachedSessionIds == null ? List.of() : List.copyOf(attachedSessionIds);
    }

    public static WorkspaceEntity of(String workspaceId, String rootPath) {
        return new WorkspaceEntity(workspaceId, rootPath, List.of());
    }

    public static WorkspaceEntity of(String workspaceId, String rootPath, List<String> attachedSessionIds) {
        return new WorkspaceEntity(workspaceId, rootPath, attachedSessionIds);
    }

    public WorkspaceEntity attachSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (attachedSessionIds.contains(sessionId)) {
            return this;
        }
        var newList = new java.util.ArrayList<>(attachedSessionIds);
        newList.add(sessionId);
        return new WorkspaceEntity(workspaceId, rootPath, newList);
    }

    public WorkspaceEntity detachSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (!attachedSessionIds.contains(sessionId)) {
            return this;
        }
        var newList = new java.util.ArrayList<>(attachedSessionIds);
        newList.remove(sessionId);
        return new WorkspaceEntity(workspaceId, rootPath, newList);
    }
}
