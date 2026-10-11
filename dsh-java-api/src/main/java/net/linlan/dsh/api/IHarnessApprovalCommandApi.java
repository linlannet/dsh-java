package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IHarnessApprovalCommandApi {

    Response<ApprovalActionResponseDTO> approve(String sessionId);
}
