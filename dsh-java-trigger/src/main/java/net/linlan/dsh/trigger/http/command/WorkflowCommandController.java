package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.api.IWorkflowCommandApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.api.stream.IWorkflowStreamApi;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowCommandController {

    private final IWorkflowCommandApi workflowCommandApi;
    private final IWorkflowStreamApi workflowStreamApi;

    public WorkflowCommandController(
            IWorkflowCommandApi workflowCommandApi,
            IWorkflowStreamApi workflowStreamApi
    ) {
        this.workflowCommandApi = workflowCommandApi;
        this.workflowStreamApi = workflowStreamApi;
    }

    @PostMapping("/start")
    public Response<WorkflowStartResponseDTO> start(@RequestBody StartWorkflowRequestDTO request) {
        return workflowCommandApi.start(request);
    }

    @PostMapping("/{runId}/cancel")
    public Response<Void> cancel(
            @PathVariable("runId") String runId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        return workflowCommandApi.cancel(runId, reason);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody StartWorkflowRequestDTO request) {
        return workflowStreamApi.stream(request);
    }
}
