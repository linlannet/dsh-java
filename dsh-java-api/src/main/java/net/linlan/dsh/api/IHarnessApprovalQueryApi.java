package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessApprovalQueryApi {

    Response<List<PendingApprovalResponseDTO>> listPendingApprovals(int limit);
}
