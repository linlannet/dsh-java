package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.CreateWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.RenameWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.ReorderWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.WorkspaceEntryResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IWorkspaceCommandApi {

    Response<List<WorkspaceEntryResponseDTO>> createWorkspace(CreateWorkspaceRequestDTO request);

    Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(String name);

    Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(String name, RenameWorkspaceRequestDTO request);

    Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(ReorderWorkspaceRequestDTO request);
}
