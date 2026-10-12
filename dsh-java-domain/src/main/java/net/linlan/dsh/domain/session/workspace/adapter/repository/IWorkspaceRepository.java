package net.linlan.dsh.domain.session.workspace.adapter.repository;

import net.linlan.dsh.domain.session.workspace.model.entity.WorkspaceEntity;

import java.util.List;
import java.util.Optional;

public interface IWorkspaceRepository {
    void save(WorkspaceEntity workspace);
    Optional<WorkspaceEntity> findById(String workspaceId);
    List<String> listAttachedSessions(String workspaceId);
}
