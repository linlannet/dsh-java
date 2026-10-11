package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowEventResponseDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.function.Consumer;

public interface IWorkflowCommandApi {

    Response<WorkflowStartResponseDTO> start(StartWorkflowRequestDTO request);

    Response<WorkflowStartResponseDTO> startStreaming(
            StartWorkflowRequestDTO request,
            Consumer<WorkflowEventResponseDTO> eventSink
    );

    Response<Void> cancel(String runId, String reason);
}
