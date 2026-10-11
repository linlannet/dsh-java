package net.linlan.dsh.api.stream;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IWorkflowStreamApi {

    SseEmitter stream(StartWorkflowRequestDTO request);
}
