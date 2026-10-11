package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalRequestDTO;
import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IRuntimeApprovalApi {

    Response<List<RuntimeApprovalResponseDTO>> listPending();

    Response<ResolveRuntimeApprovalResponseDTO> resolve(
            String approvalId,
            ResolveRuntimeApprovalRequestDTO request
    );
}
