package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.WorkspaceEntryResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IWorkspaceQueryApi {

    Response<List<WorkspaceEntryResponseDTO>> getWorkspaces();

    Response<List<String>> listDirectories(String path);
}
