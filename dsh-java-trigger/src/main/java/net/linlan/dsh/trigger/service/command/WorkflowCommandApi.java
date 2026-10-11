package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowEventResponseDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.api.IWorkflowCommandApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.workflow.IWorkflowCase;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class WorkflowCommandApi implements IWorkflowCommandApi {

    private final IWorkflowCase workflowCase;

    public WorkflowCommandApi(IWorkflowCase workflowCase) {
        this.workflowCase = workflowCase;
    }

    public Response<WorkflowStartResponseDTO> start(StartWorkflowRequestDTO request) {
        return execute(() -> workflowCase.start(request));
    }

    public Response<WorkflowStartResponseDTO> startStreaming(
            StartWorkflowRequestDTO request,
            Consumer<WorkflowEventResponseDTO> eventSink
    ) {
        return execute(() -> workflowCase.startStreaming(request, eventSink));
    }

    public Response<Void> cancel(String runId, String reason) {
        return execute(() -> {
            workflowCase.cancel(runId, reason);
            return null;
        });
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
