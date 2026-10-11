package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalRequestDTO;
import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.api.IRuntimeApprovalApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.approval.IRuntimeApprovalCase;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuntimeApprovalApi implements IRuntimeApprovalApi {

    private final IRuntimeApprovalCase runtimeApprovalCase;

    public RuntimeApprovalApi(IRuntimeApprovalCase runtimeApprovalCase) {
        this.runtimeApprovalCase = runtimeApprovalCase;
    }

    public Response<List<RuntimeApprovalResponseDTO>> listPending() {
        return execute(runtimeApprovalCase::listPending);
    }

    public Response<ResolveRuntimeApprovalResponseDTO> resolve(
            String approvalId,
            ResolveRuntimeApprovalRequestDTO request
    ) {
        return execute(() -> runtimeApprovalCase.resolve(approvalId, request));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
